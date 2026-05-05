package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

// ── Room state ────────────────────────────────────────────────────────────────

type roomState int

const (
	stateWaiting roomState = iota // caller present, no callee yet
	stateRinging                  // both present, waiting for accept/reject
	stateActive                   // accepted, relaying SDP/ICE
)

const ringTimeout = 30 * time.Second

type videoPeer struct {
	username string
	conn     *websocket.Conn
	send     chan []byte
	done     chan struct{}
}

type videoRoom struct {
	mu     sync.Mutex
	state  roomState
	caller *videoPeer
	callee *videoPeer
	timer  *time.Timer // ring timeout
	ended  bool        // prevents double-cleanup
}

var (
	videoRoomsMu sync.Mutex
	videoRooms   = map[string]*videoRoom{}
)

func getOrCreateVideoRoom(roomID string) *videoRoom {
	videoRoomsMu.Lock()
	defer videoRoomsMu.Unlock()
	if r, ok := videoRooms[roomID]; ok {
		return r
	}
	r := &videoRoom{state: stateWaiting}
	videoRooms[roomID] = r
	return r
}

func deleteVideoRoom(roomID string) {
	videoRoomsMu.Lock()
	defer videoRoomsMu.Unlock()
	delete(videoRooms, roomID)
}

func videoRoomCount() int {
	videoRoomsMu.Lock()
	defer videoRoomsMu.Unlock()
	return len(videoRooms)
}

// ── WebSocket handler ─────────────────────────────────────────────────────────

// serveVideo handles /video?room=<id>&role=caller|callee.
// JWT is required (via requireAuth wrapper).
func serveVideo(username string, w http.ResponseWriter, r *http.Request) {
	roomID := r.URL.Query().Get("room")
	if roomID == "" {
		http.Error(w, "Missing ?room= parameter", http.StatusBadRequest)
		return
	}
	role := r.URL.Query().Get("role")
	if role != "caller" && role != "callee" {
		http.Error(w, "Missing or invalid ?role= (must be caller|callee)", http.StatusBadRequest)
		return
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Println("[VIDEO] Upgrade error:", err)
		return
	}

	peer := &videoPeer{
		username: username,
		conn:     conn,
		send:     make(chan []byte, 64),
		done:     make(chan struct{}),
	}

	room := getOrCreateVideoRoom(roomID)

	// Claim a slot based on role.
	room.mu.Lock()
	switch role {
	case "caller":
		if room.caller != nil {
			room.mu.Unlock()
			peer.sendNow(map[string]any{"type": "busy", "reason": "another caller already in room"})
			conn.Close()
			log.Printf("[VIDEO] Room %s busy (caller exists), rejected %s", roomID, username)
			return
		}
		if room.callee != nil {
			room.mu.Unlock()
			peer.sendNow(map[string]any{"type": "busy", "reason": "callee already waiting"})
			conn.Close()
			return
		}
		room.caller = peer
		room.state = stateWaiting

	case "callee":
		if room.caller == nil {
			room.mu.Unlock()
			peer.sendNow(map[string]any{"type": "rejected", "reason": "no incoming call"})
			conn.Close()
			log.Printf("[VIDEO] Room %s no caller, rejected callee %s", roomID, username)
			return
		}
		if room.callee != nil {
			room.mu.Unlock()
			peer.sendNow(map[string]any{"type": "busy", "reason": "another callee already in room"})
			conn.Close()
			return
		}
		room.callee = peer
		room.state = stateRinging
		// Notify both peers.
		room.caller.sendMsg(map[string]any{"type": "ringing", "calleeName": peer.username})
		peer.sendMsg(map[string]any{"type": "incoming", "callerName": room.caller.username})
		// Start ring timeout.
		room.timer = time.AfterFunc(ringTimeout, func() { onRingTimeout(roomID) })
	}
	room.mu.Unlock()

	log.Printf("[VIDEO] %s joined room=%s role=%s", username, roomID, role)

	go peer.writePump()
	peer.readPump(room, role, roomID) // blocks until disconnected
}

// ── Peer I/O ──────────────────────────────────────────────────────────────────

func (p *videoPeer) readPump(room *videoRoom, role, roomID string) {
	defer func() {
		close(p.done)
		p.conn.Close()
		onPeerDisconnect(room, role, roomID)
		log.Printf("[VIDEO] %s left room=%s role=%s", p.username, roomID, role)
	}()

	p.conn.SetReadLimit(maxMessageSize)
	p.conn.SetReadDeadline(time.Now().Add(pongWait))
	p.conn.SetPongHandler(func(string) error {
		p.conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})

	for {
		_, raw, err := p.conn.ReadMessage()
		if err != nil {
			break
		}

		// Peek the type field to handle control messages locally.
		var hdr struct {
			Type   string `json:"type"`
			Reason string `json:"reason,omitempty"`
		}
		_ = json.Unmarshal(raw, &hdr)

		switch hdr.Type {
		case "accept":
			if role == "callee" {
				handleAccept(room, roomID)
			}
		case "reject":
			if role == "callee" {
				handleReject(room, roomID, hdr.Reason)
				return // close callee connection
			}
		case "cancel":
			if role == "caller" {
				handleCancel(room, roomID)
				return // close caller connection
			}
		default:
			// Relay raw bytes (offer/answer/ice/leave) to the other peer
			// only when the call is active.
			room.mu.Lock()
			active := room.state == stateActive
			var other *videoPeer
			if role == "caller" {
				other = room.callee
			} else {
				other = room.caller
			}
			room.mu.Unlock()

			if active && other != nil {
				select {
				case other.send <- raw:
				default:
					log.Printf("[VIDEO] Send buffer full for %s", other.username)
				}
			}
		}
	}
}

func (p *videoPeer) writePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		p.conn.Close()
	}()

	for {
		select {
		case msg, ok := <-p.send:
			p.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				p.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}
			if err := p.conn.WriteMessage(websocket.TextMessage, msg); err != nil {
				return
			}
		case <-ticker.C:
			p.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := p.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		case <-p.done:
			return
		}
	}
}

// sendMsg enqueues a JSON message non-blockingly (drops if buffer full).
func (p *videoPeer) sendMsg(v any) {
	select {
	case p.send <- mustJSON(v):
	default:
	}
}

// sendNow writes synchronously (used before the writePump starts).
func (p *videoPeer) sendNow(v any) {
	p.conn.SetWriteDeadline(time.Now().Add(writeWait))
	p.conn.WriteMessage(websocket.TextMessage, mustJSON(v))
}

// ── Control message handlers ──────────────────────────────────────────────────

func handleAccept(room *videoRoom, roomID string) {
	room.mu.Lock()
	if room.state != stateRinging || room.caller == nil || room.callee == nil {
		room.mu.Unlock()
		return
	}
	room.state = stateActive
	if room.timer != nil {
		room.timer.Stop()
		room.timer = nil
	}
	caller := room.caller
	callee := room.callee
	room.mu.Unlock()

	// Caller becomes the WebRTC initiator (creates offer).
	caller.sendMsg(map[string]any{"type": "join", "username": callee.username, "initiator": true})
	callee.sendMsg(map[string]any{"type": "join", "username": caller.username, "initiator": false})
	log.Printf("[VIDEO] Room %s call accepted", roomID)
}

func handleReject(room *videoRoom, roomID, reason string) {
	endRoom(room, roomID, "rejected", reason)
	log.Printf("[VIDEO] Room %s call rejected reason=%q", roomID, reason)
}

func handleCancel(room *videoRoom, roomID string) {
	endRoom(room, roomID, "cancelled", "")
	log.Printf("[VIDEO] Room %s call cancelled", roomID)
}

func onRingTimeout(roomID string) {
	videoRoomsMu.Lock()
	room, ok := videoRooms[roomID]
	videoRoomsMu.Unlock()
	if !ok {
		return
	}
	endRoom(room, roomID, "timeout", "")
	log.Printf("[VIDEO] Room %s ring timeout", roomID)
}

// onPeerDisconnect handles connection closes mid-flight.
func onPeerDisconnect(room *videoRoom, role, roomID string) {
	room.mu.Lock()
	if room.ended {
		room.mu.Unlock()
		deleteVideoRoom(roomID)
		return
	}
	state := room.state
	caller := room.caller
	callee := room.callee
	room.mu.Unlock()

	switch {
	case state == stateActive:
		// Notify the surviving peer that the call ended.
		var other *videoPeer
		if role == "caller" {
			other = callee
		} else {
			other = caller
		}
		if other != nil {
			other.sendMsg(map[string]any{"type": "leave"})
		}
		endRoom(room, roomID, "", "")

	case state == stateRinging:
		// Treat caller-leaving as cancel, callee-leaving as reject.
		if role == "caller" {
			endRoom(room, roomID, "cancelled", "caller disconnected")
		} else {
			endRoom(room, roomID, "rejected", "callee disconnected")
		}

	case state == stateWaiting:
		// Caller left while waiting alone — just clean up.
		endRoom(room, roomID, "", "")
	}
}

// endRoom notifies the affected peer (if any) and tears down the room.
// notifyType: "rejected" | "cancelled" | "timeout" | "" (no signal).
func endRoom(room *videoRoom, roomID, notifyType, reason string) {
	room.mu.Lock()
	if room.ended {
		room.mu.Unlock()
		return
	}
	room.ended = true
	if room.timer != nil {
		room.timer.Stop()
		room.timer = nil
	}
	caller := room.caller
	callee := room.callee
	room.caller = nil
	room.callee = nil
	room.mu.Unlock()

	if notifyType != "" {
		msg := map[string]any{"type": notifyType}
		if reason != "" {
			msg["reason"] = reason
		}
		switch notifyType {
		case "rejected":
			if caller != nil {
				caller.sendMsg(msg)
			}
		case "cancelled":
			if callee != nil {
				callee.sendMsg(msg)
			}
		case "timeout":
			if caller != nil {
				caller.sendMsg(msg)
			}
			if callee != nil {
				callee.sendMsg(msg)
			}
		}
	}

	// Close both connections after a short delay so the message can flush.
	go func() {
		time.Sleep(200 * time.Millisecond)
		if caller != nil {
			caller.conn.Close()
		}
		if callee != nil {
			callee.conn.Close()
		}
		deleteVideoRoom(roomID)
	}()
}

// ── Browser test page ─────────────────────────────────────────────────────────

func serveVideoTest(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, videoTestHTML)
}

func mustJSON(v any) []byte {
	data, _ := json.Marshal(v)
	return data
}

const videoTestHTML = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Video Call Test</title>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: monospace; background: #0d1117; color: #c9d1d9; padding: 20px; }
h2 { color: #58a6ff; margin-bottom: 16px; }
.row { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
input { background: #161b22; border: 1px solid #30363d; color: #c9d1d9; padding: 6px 10px; border-radius: 6px; font-family: monospace; }
button { background: #238636; color: #fff; border: none; padding: 6px 14px; border-radius: 6px; cursor: pointer; font-family: monospace; }
button:hover { background: #2ea043; }
button.red { background: #b62324; }
button.red:hover { background: #da3633; }
button.yellow { background: #9e6a03; }
button.yellow:hover { background: #bf8700; }
.videos { display: flex; gap: 16px; margin: 16px 0; flex-wrap: wrap; }
.vc { text-align: center; }
.vc video { width: 320px; height: 240px; background: #161b22; border: 1px solid #30363d; border-radius: 8px; display: block; }
.vc div { margin-top: 4px; font-size: 12px; color: #8b949e; }
#log { background: #161b22; border: 1px solid #30363d; padding: 12px; border-radius: 8px; height: 200px; overflow-y: auto; font-size: 11px; line-height: 1.7; }
.tag { display: inline-block; padding: 1px 5px; border-radius: 3px; font-size: 10px; margin-right: 4px; }
.ok   { background: #1f3d2f; color: #3fb950; }
.err  { background: #3d1f1f; color: #f85149; }
.info { background: #1f2d4f; color: #58a6ff; }
.ring { background: #4a3814; color: #e3b341; }
</style>
</head>
<body>
<h2>📹 Video Call Test (Ringing Flow)</h2>
<p style="color:#8b949e;font-size:12px;margin-bottom:12px">
  Tab 1 → Login + Start Call (caller).<br>
  Tab 2 → Login + Wait (callee) → Accept / Reject.
</p>

<div class="row">
  <input id="u" placeholder="username" value="demo" style="width:110px">
  <input id="pw" type="password" placeholder="password" value="password" style="width:110px">
  <button onclick="login()">Login</button>
  <span id="st" style="color:#f85149;font-size:12px">Not logged in</span>
</div>

<div class="row">
  <input id="room" placeholder="room-id" value="room1" style="width:110px">
  <button onclick="startCall()">Start Call (caller)</button>
  <button class="yellow" onclick="waitForCall()">Wait (callee)</button>
  <button class="red" onclick="hangup()">Cancel / Leave</button>
</div>

<div id="incoming" style="display:none;background:#1f2d4f;padding:10px;border-radius:6px;margin:10px 0">
  <div id="incomingMsg" style="margin-bottom:8px"></div>
  <button onclick="acceptCall()">Accept</button>
  <button class="red" onclick="rejectCall()">Reject</button>
</div>

<div class="videos">
  <div class="vc">
    <video id="lv" autoplay muted playsinline></video>
    <div>Local</div>
  </div>
  <div class="vc">
    <video id="rv" autoplay playsinline></video>
    <div>Remote</div>
  </div>
</div>

<div id="log"></div>

<script>
let token = null, ws = null, pc = null, localStream = null, role = null;
const iceConfig = { iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] };

function log(cls, msg) {
  const el = document.getElementById('log');
  el.insertAdjacentHTML('beforeend',
    '<div>' + new Date().toLocaleTimeString() + ' <span class="tag ' + cls + '">' + cls.toUpperCase() + '</span>' + msg + '</div>');
  el.scrollTop = el.scrollHeight;
}

async function login() {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: document.getElementById('u').value, password: document.getElementById('pw').value })
  });
  if (!res.ok) { log('err', 'Login failed: ' + res.status); return; }
  token = (await res.json()).token;
  const st = document.getElementById('st');
  st.textContent = '✅ ' + document.getElementById('u').value;
  st.style.color = '#3fb950';
  log('ok', 'Logged in as ' + document.getElementById('u').value);
}

async function startCall() { return openWS('caller'); }
async function waitForCall() { return openWS('callee'); }

async function openWS(r) {
  if (!token) { log('err', 'Login first'); return; }
  const room = document.getElementById('room').value.trim();
  if (!room) { log('err', 'Enter room ID'); return; }
  if (ws) { log('err', 'Already connected'); return; }
  role = r;

  // Caller pre-acquires media; callee defers until accept.
  if (role === 'caller') {
    try {
      localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
      document.getElementById('lv').srcObject = localStream;
      log('ok', 'Camera / mic ready');
    } catch (e) { log('err', 'Media error: ' + e.message); return; }
  }

  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
  ws = new WebSocket(proto + '//' + location.host + '/video?room=' + encodeURIComponent(room) + '&role=' + role + '&token=' + token);
  ws.onopen  = () => log('info', 'WS connected as ' + role);
  ws.onclose = () => { log('info', 'WS closed'); cleanup(); };
  ws.onerror = () => log('err', 'WS error');
  ws.onmessage = onMsg;
}

async function onMsg(e) {
  const msg = JSON.parse(e.data);
  log('info', '← ' + msg.type + (msg.reason ? ' reason=' + msg.reason : ''));

  if (msg.type === 'incoming') {
    document.getElementById('incomingMsg').textContent = '📞 Incoming call from ' + msg.callerName;
    document.getElementById('incoming').style.display = 'block';
    log('ring', 'Incoming from ' + msg.callerName);
  } else if (msg.type === 'ringing') {
    log('ring', 'Ringing ' + msg.calleeName + '...');
  } else if (msg.type === 'rejected') {
    log('err', 'Call rejected' + (msg.reason ? ': ' + msg.reason : ''));
    cleanup();
  } else if (msg.type === 'cancelled') {
    log('err', 'Call cancelled');
    document.getElementById('incoming').style.display = 'none';
    cleanup();
  } else if (msg.type === 'timeout') {
    log('err', 'No answer (timeout)');
    document.getElementById('incoming').style.display = 'none';
    cleanup();
  } else if (msg.type === 'busy') {
    log('err', 'Busy: ' + (msg.reason || ''));
    cleanup();
  } else if (msg.type === 'join') {
    document.getElementById('incoming').style.display = 'none';
    await createPC();
    if (msg.initiator) {
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      send({ type: 'offer', sdp: offer.sdp });
      log('ok', '→ offer sent');
    }
  } else if (msg.type === 'offer') {
    if (!pc) await createPC();
    await pc.setRemoteDescription({ type: 'offer', sdp: msg.sdp });
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    send({ type: 'answer', sdp: answer.sdp });
    log('ok', '→ answer sent');
  } else if (msg.type === 'answer') {
    await pc.setRemoteDescription({ type: 'answer', sdp: msg.sdp });
  } else if (msg.type === 'ice' && msg.candidate && pc) {
    await pc.addIceCandidate({ candidate: msg.candidate, sdpMid: msg.sdpMid, sdpMLineIndex: msg.sdpMLineIndex });
  } else if (msg.type === 'leave') {
    log('info', 'Peer left');
    cleanup();
  }
}

async function acceptCall() {
  if (!localStream) {
    try {
      localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
      document.getElementById('lv').srcObject = localStream;
    } catch (e) { log('err', 'Media error: ' + e.message); return; }
  }
  send({ type: 'accept' });
  document.getElementById('incoming').style.display = 'none';
}
function rejectCall() {
  send({ type: 'reject', reason: 'busy' });
  document.getElementById('incoming').style.display = 'none';
  cleanup();
}
function hangup() {
  if (role === 'caller') send({ type: 'cancel' });
  else send({ type: 'leave' });
  cleanup();
}

async function createPC() {
  pc = new RTCPeerConnection(iceConfig);
  if (localStream) localStream.getTracks().forEach(t => pc.addTrack(t, localStream));
  pc.onicecandidate = e => {
    if (e.candidate) send({ type: 'ice', candidate: e.candidate.candidate, sdpMid: e.candidate.sdpMid, sdpMLineIndex: e.candidate.sdpMLineIndex });
  };
  pc.ontrack = e => { document.getElementById('rv').srcObject = e.streams[0]; };
  pc.onconnectionstatechange = () => log('info', 'PC ' + pc.connectionState);
}

function send(obj) {
  if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj));
}

function cleanup() {
  if (pc) { pc.close(); pc = null; }
  if (ws) { ws.close(); ws = null; }
  if (localStream) { localStream.getTracks().forEach(t => t.stop()); localStream = null; }
  document.getElementById('lv').srcObject = null;
  document.getElementById('rv').srcObject = null;
  role = null;
}
</script>
</body>
</html>`
