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

// ── Room registry ─────────────────────────────────────────────────────────────

type videoPeer struct {
	username string
	conn     *websocket.Conn
	send     chan []byte
	done     chan struct{}
}

// videoRoom holds at most 2 peers for a 1-to-1 call.
type videoRoom struct {
	mu    sync.Mutex
	peers [2]*videoPeer
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
	r := &videoRoom{}
	videoRooms[roomID] = r
	return r
}

func cleanupVideoRoom(roomID string) {
	videoRoomsMu.Lock()
	defer videoRoomsMu.Unlock()
	r, ok := videoRooms[roomID]
	if !ok {
		return
	}
	r.mu.Lock()
	empty := r.peers[0] == nil && r.peers[1] == nil
	r.mu.Unlock()
	if empty {
		delete(videoRooms, roomID)
		log.Printf("[VIDEO] Room %s removed", roomID)
	}
}

func videoRoomCount() int {
	videoRoomsMu.Lock()
	defer videoRoomsMu.Unlock()
	return len(videoRooms)
}

// ── WebSocket handler ─────────────────────────────────────────────────────────

// serveVideo handles /video?room=<id>. JWT is required (via requireAuth wrapper).
func serveVideo(username string, w http.ResponseWriter, r *http.Request) {
	roomID := r.URL.Query().Get("room")
	if roomID == "" {
		http.Error(w, "Missing ?room= parameter", http.StatusBadRequest)
		return
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Println("[VIDEO] Upgrade error:", err)
		return
	}

	room := getOrCreateVideoRoom(roomID)
	peer := &videoPeer{
		username: username,
		conn:     conn,
		send:     make(chan []byte, 64),
		done:     make(chan struct{}),
	}

	// Claim a slot (max 2 peers per room).
	room.mu.Lock()
	var slot int
	var other *videoPeer
	if room.peers[0] == nil {
		slot = 0
		room.peers[0] = peer
		other = room.peers[1]
	} else if room.peers[1] == nil {
		slot = 1
		room.peers[1] = peer
		other = room.peers[0]
	} else {
		room.mu.Unlock()
		conn.WriteMessage(websocket.TextMessage, mustJSON(map[string]any{"type": "busy"}))
		conn.Close()
		log.Printf("[VIDEO] Room %s full, rejected %s", roomID, username)
		return
	}
	room.mu.Unlock()

	log.Printf("[VIDEO] %s joined room=%s slot=%d", username, roomID, slot)

	// When both peers are present, notify them.
	// The peer that was already waiting (slot 0) becomes the WebRTC initiator.
	if other != nil {
		other.sendMsg(map[string]any{"type": "join", "username": username, "initiator": true})
		peer.sendMsg(map[string]any{"type": "join", "username": other.username, "initiator": false})
	}

	go peer.writePump()
	peer.readPump(room, slot, roomID) // blocks until disconnected
}

// ── Peer I/O ──────────────────────────────────────────────────────────────────

func (p *videoPeer) readPump(room *videoRoom, slot int, roomID string) {
	defer func() {
		close(p.done)
		p.conn.Close()

		room.mu.Lock()
		room.peers[slot] = nil
		other := room.peers[1-slot]
		room.mu.Unlock()

		if other != nil {
			other.sendMsg(map[string]any{"type": "leave", "username": p.username})
		}

		cleanupVideoRoom(roomID)
		log.Printf("[VIDEO] %s left room=%s", p.username, roomID)
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

		// Relay raw SDP/ICE bytes to the other peer — server does not parse WebRTC content.
		room.mu.Lock()
		other := room.peers[1-slot]
		room.mu.Unlock()

		if other != nil {
			select {
			case other.send <- raw:
			default:
				log.Printf("[VIDEO] Send buffer full for %s", other.username)
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

func (p *videoPeer) sendMsg(v any) {
	select {
	case p.send <- mustJSON(v):
	default:
	}
}

func mustJSON(v any) []byte {
	data, _ := json.Marshal(v)
	return data
}

// ── Browser test page ─────────────────────────────────────────────────────────

func serveVideoTest(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, videoTestHTML)
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
.videos { display: flex; gap: 16px; margin: 16px 0; flex-wrap: wrap; }
.vc { text-align: center; }
.vc video { width: 320px; height: 240px; background: #161b22; border: 1px solid #30363d; border-radius: 8px; display: block; }
.vc div { margin-top: 4px; font-size: 12px; color: #8b949e; }
#log { background: #161b22; border: 1px solid #30363d; padding: 12px; border-radius: 8px; height: 200px; overflow-y: auto; font-size: 11px; line-height: 1.7; }
.tag { display: inline-block; padding: 1px 5px; border-radius: 3px; font-size: 10px; margin-right: 4px; }
.ok   { background: #1f3d2f; color: #3fb950; }
.err  { background: #3d1f1f; color: #f85149; }
.info { background: #1f2d4f; color: #58a6ff; }
</style>
</head>
<body>
<h2>📹 Video Call Test</h2>
<p style="color:#8b949e;font-size:12px;margin-bottom:12px">Open two browser tabs, same room ID → first tab is initiator (creates offer).</p>

<div class="row">
  <input id="u" placeholder="username" value="demo" style="width:110px">
  <input id="pw" type="password" placeholder="password" value="password" style="width:110px">
  <button onclick="login()">Login</button>
  <span id="st" style="color:#f85149;font-size:12px">Not logged in</span>
</div>

<div class="row">
  <input id="room" placeholder="room-id" value="room1" style="width:110px">
  <button onclick="joinRoom()">Join Room</button>
  <button class="red" onclick="leaveRoom()">Leave</button>
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
let token = null, ws = null, pc = null, localStream = null;
const iceConfig = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:openrelay.metered.ca:80' },
    {
      urls: [
        'turn:openrelay.metered.ca:80',
        'turn:openrelay.metered.ca:443',
        'turn:openrelay.metered.ca:443?transport=tcp',
        'turn:openrelay.metered.ca:80?transport=tcp',
      ],
      username: 'openrelayproject',
      credential: 'openrelayproject',
    },
  ],
};

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

async function joinRoom() {
  if (!token) { log('err', 'Login first'); return; }
  const room = document.getElementById('room').value.trim();
  if (!room) { log('err', 'Enter room ID'); return; }

  try {
    localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
    document.getElementById('lv').srcObject = localStream;
    log('ok', 'Camera / mic ready');
  } catch (e) { log('err', 'Media error: ' + e.message); return; }

  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
  ws = new WebSocket(proto + '//' + location.host + '/video?room=' + encodeURIComponent(room) + '&token=' + token);
  ws.onopen  = () => log('info', 'WS connected → waiting for peer…');
  ws.onclose = () => log('info', 'WS closed');
  ws.onerror = () => log('err', 'WS error');
  ws.onmessage = onMsg;
}

async function onMsg(e) {
  const msg = JSON.parse(e.data);
  log('info', '← ' + msg.type + (msg.username ? ' (' + msg.username + ')' : '') + (msg.initiator != null ? ' initiator=' + msg.initiator : ''));

  if (msg.type === 'busy') { log('err', 'Room is full (max 2 peers)'); leaveRoom(); return; }

  if (msg.type === 'join') {
    await createPC();
    if (msg.initiator) {
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      send({ type: 'offer', sdp: offer.sdp });
      log('ok', '→ offer sent');
    }
  }

  if (msg.type === 'offer') {
    if (!pc) await createPC();
    await pc.setRemoteDescription({ type: 'offer', sdp: msg.sdp });
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    send({ type: 'answer', sdp: answer.sdp });
    log('ok', '→ answer sent');
  }

  if (msg.type === 'answer') {
    await pc.setRemoteDescription({ type: 'answer', sdp: msg.sdp });
    log('ok', 'Remote description set');
  }

  if (msg.type === 'ice' && msg.candidate && pc) {
    await pc.addIceCandidate({ candidate: msg.candidate, sdpMid: msg.sdpMid, sdpMLineIndex: msg.sdpMLineIndex });
  }

  if (msg.type === 'leave') {
    log('info', 'Peer left the room');
    if (pc) { pc.close(); pc = null; }
    document.getElementById('rv').srcObject = null;
  }
}

async function createPC() {
  pc = new RTCPeerConnection(iceConfig);
  localStream.getTracks().forEach(t => pc.addTrack(t, localStream));

  pc.onicecandidate = e => {
    if (e.candidate && ws && ws.readyState === WebSocket.OPEN) {
      send({ type: 'ice', candidate: e.candidate.candidate, sdpMid: e.candidate.sdpMid, sdpMLineIndex: e.candidate.sdpMLineIndex });
    }
  };

  pc.ontrack = e => {
    document.getElementById('rv').srcObject = e.streams[0];
    log('ok', 'Remote stream attached');
  };

  pc.onconnectionstatechange = () => log('info', 'PC state → ' + pc.connectionState);
  log('ok', 'RTCPeerConnection created');
}

function send(obj) {
  if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj));
}

function leaveRoom() {
  if (pc) { pc.close(); pc = null; }
  if (ws) { ws.close(); ws = null; }
  if (localStream) { localStream.getTracks().forEach(t => t.stop()); localStream = null; }
  document.getElementById('lv').srcObject = null;
  document.getElementById('rv').srcObject = null;
  log('info', 'Left room');
}
</script>
</body>
</html>`
