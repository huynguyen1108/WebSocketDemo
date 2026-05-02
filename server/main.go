package main

import (
	"crypto/rand"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"net/http"
	"time"

	"github.com/gorilla/websocket"
)

func generateID() string {
	b := make([]byte, 8)
	rand.Read(b)
	return fmt.Sprintf("%x", b)
}

// ── Chat handlers ─────────────────────────────────────────────────────────────

func serveWs(hub *Hub, w http.ResponseWriter, r *http.Request) {
	username := r.URL.Query().Get("username")
	if username == "" {
		username = "Anon-" + generateID()[:4]
	}
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Println("[WS] Upgrade error:", err)
		return
	}
	client := &Client{
		id:       generateID(),
		username: username,
		hub:      hub,
		conn:     conn,
		send:     make(chan []byte, 256),
	}
	client.hub.register <- client
	go client.writePump()
	go client.readPump()
}

// ── Market handlers ───────────────────────────────────────────────────────────

func serveMarket(hub *MarketHub, sim *StockSimulator, w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Println("[MARKET] Upgrade error:", err)
		return
	}

	send := hub.join()
	log.Printf("[MARKET] Client connected, total=%d", hub.clientCount())

	// Gửi snapshot ngay khi client kết nối
	snap := sim.snapshot()
	if data, err := json.Marshal(MarketMessage{Type: MsgTypeSnapshot, Payload: snap}); err == nil {
		conn.SetWriteDeadline(time.Now().Add(writeWait))
		conn.WriteMessage(websocket.TextMessage, data)
	}

	pingTicker := time.NewTicker(30 * time.Second)
	defer func() {
		pingTicker.Stop()
		hub.leave(send)
		conn.Close()
		log.Printf("[MARKET] Client disconnected, total=%d", hub.clientCount())
	}()

	// Goroutine phát hiện disconnect từ client
	disconnected := make(chan struct{})
	go func() {
		defer close(disconnected)
		conn.SetReadLimit(512) // Client không gửi gì lớn
		for {
			if _, _, err := conn.ReadMessage(); err != nil {
				break
			}
		}
	}()

	for {
		select {
		case msg, ok := <-send:
			conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}
			if err := conn.WriteMessage(websocket.TextMessage, msg); err != nil {
				return
			}
		case <-pingTicker.C:
			conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		case <-disconnected:
			return
		}
	}
}

// startMarketTicker chạy simulator và broadcast định kỳ.
func startMarketTicker(hub *MarketHub, sim *StockSimulator) {
	go func() {
		// Stock tick: 800ms — đủ nhanh để UI cảm thấy real-time
		stockTicker := time.NewTicker(800 * time.Millisecond)
		// Index tick: 2s — index chậm hơn stock
		indexTicker := time.NewTicker(2 * time.Second)
		defer stockTicker.Stop()
		defer indexTicker.Stop()

		for {
			select {
			case <-stockTicker.C:
				for _, stock := range sim.tick() {
					data, _ := json.Marshal(MarketMessage{Type: MsgTypeStockTick, Payload: stock})
					hub.broadcast(data)
				}
			case <-indexTicker.C:
				for _, idx := range sim.tickIndices() {
					data, _ := json.Marshal(MarketMessage{Type: MsgTypeIndexTick, Payload: idx})
					hub.broadcast(data)
				}
			}
		}
	}()
}

// ── Status / Home ─────────────────────────────────────────────────────────────

func serveStatus(chatHub *Hub, marketHub *MarketHub, w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	json.NewEncoder(w).Encode(map[string]any{
		"status":        "ok",
		"chatClients":   chatHub.clientCount(),
		"marketClients": marketHub.clientCount(),
		"videoRooms":    videoRoomCount(),
		"time":          time.Now().Format(time.RFC3339),
	})
}

func serveHome(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, `<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8">
<title>WS Stock Demo</title>
<style>
  body { font-family: monospace; background: #0d1117; color: #c9d1d9; padding: 20px; }
  h2 { color: #58a6ff; }
  #status { color: #888; }
  #indices { display: flex; gap: 16px; margin: 12px 0; }
  .index-card { background: #161b22; border: 1px solid #30363d; border-radius: 8px; padding: 12px 16px; min-width: 160px; }
  .index-name { font-size: 11px; color: #8b949e; }
  .index-val { font-size: 22px; font-weight: bold; margin: 4px 0; }
  .up { color: #3fb950; } .down { color: #f85149; } .flat { color: #e3b341; }
  table { width: 100%; border-collapse: collapse; }
  th { text-align: left; padding: 8px; background: #161b22; color: #8b949e; font-size: 11px; }
  td { padding: 6px 8px; border-bottom: 1px solid #21262d; font-size: 13px; }
  tr:hover { background: #161b22; }
  .badge { display: inline-block; font-size: 10px; padding: 1px 4px; border-radius: 3px; }
  .badge.hose { background:#1f3d2f; color:#3fb950; }
  .badge.hnx  { background:#1f2d4f; color:#58a6ff; }
</style>
</head>
<body>
<h2>📈 WebSocket Stock Demo</h2>
<div>Trạng thái: <span id="status" style="color:#f85149">Chưa kết nối</span></div>
<div id="indices"></div>
<table>
  <thead><tr><th>Mã</th><th>Sàn</th><th>Giá</th><th>+/-</th><th>%</th><th>Khối lượng</th></tr></thead>
  <tbody id="stocks"></tbody>
</table>
<script>
const stocks = {}, indices = {};
const fmt = n => Number(n).toLocaleString('vi-VN');
const fmtP = n => (n >= 0 ? '+' : '') + n.toFixed(2) + '%';
const cls = c => c > 0 ? 'up' : c < 0 ? 'down' : 'flat';

function renderIndices() {
  document.getElementById('indices').innerHTML = Object.values(indices).map(i =>
    '<div class="index-card">' +
    '<div class="index-name">' + i.name + '</div>' +
    '<div class="index-val ' + cls(i.change) + '">' + i.value.toFixed(2) + '</div>' +
    '<div class="' + cls(i.change) + '">' + (i.change >= 0 ? '+' : '') + i.change.toFixed(2) + ' (' + fmtP(i.changePercent) + ')</div>' +
    '<div style="font-size:11px;color:#8b949e;margin-top:4px">↑' + i.advances + ' ↓' + i.declines + ' —' + i.noChanges + '</div>' +
    '</div>'
  ).join('');
}

function renderStock(s) {
  const cls2 = cls(s.change);
  let row = document.getElementById('row-' + s.symbol);
  if (!row) {
    row = document.createElement('tr');
    row.id = 'row-' + s.symbol;
    document.getElementById('stocks').appendChild(row);
  }
  row.innerHTML =
    '<td>' + s.symbol + '</td>' +
    '<td><span class="badge ' + (s.exchange ? s.exchange.toLowerCase() : '') + '">' + s.exchange + '</span></td>' +
    '<td class="' + cls2 + '" style="font-weight:bold">' + fmt(s.price) + '</td>' +
    '<td class="' + cls2 + '">' + (s.change >= 0 ? '+' : '') + fmt(s.change) + '</td>' +
    '<td class="' + cls2 + '">' + fmtP(s.changePercent) + '</td>' +
    '<td style="color:#8b949e">' + (s.volume / 1000).toFixed(0) + 'K</td>';
  row.style.transition = 'background 0.3s';
  row.style.background = s.change > 0 ? '#0d2818' : s.change < 0 ? '#2d1117' : 'transparent';
  setTimeout(() => { row.style.background = 'transparent'; }, 600);
}

const ws = new WebSocket('ws://' + location.host + '/market');
ws.onopen = () => document.getElementById('status').textContent = '🟢 Đã kết nối';
ws.onclose = () => document.getElementById('status').textContent = '🔴 Mất kết nối';
ws.onmessage = e => {
  const { type, payload } = JSON.parse(e.data);
  if (type === 'snapshot') {
    payload.stocks.forEach(s => { stocks[s.symbol] = s; renderStock(s); });
    payload.indices.forEach(i => { indices[i.name] = i; });
    renderIndices();
  } else if (type === 'stock_tick') {
    stocks[payload.symbol] = payload;
    renderStock(payload);
  } else if (type === 'index_tick') {
    indices[payload.name] = payload;
    renderIndices();
  }
};
</script>
</body>
</html>`)
}

// ── Main ──────────────────────────────────────────────────────────────────────

func main() {
	chatHub := newHub()
	go chatHub.run()

	marketHub := newMarketHub()
	sim := newStockSimulator()
	startMarketTicker(marketHub, sim)

	mux := http.NewServeMux()

	// Auth
	mux.HandleFunc("/api/auth/login", handleLogin)

	// Chat WS — token is validated when present, but not required (allows web demo)
	mux.HandleFunc("/ws", func(w http.ResponseWriter, r *http.Request) {
		if tok := bearerToken(r); tok != "" {
			if _, err := validateJWT(tok); err != nil {
				http.Error(w, "Unauthorized: "+err.Error(), http.StatusUnauthorized)
				return
			}
		}
		serveWs(chatHub, w, r)
	})

	// Market WS — same: token validated if present, unauthenticated allowed for web demo
	mux.HandleFunc("/market", func(w http.ResponseWriter, r *http.Request) {
		if tok := bearerToken(r); tok != "" {
			if _, err := validateJWT(tok); err != nil {
				http.Error(w, "Unauthorized: "+err.Error(), http.StatusUnauthorized)
				return
			}
		}
		serveMarket(marketHub, sim, w, r)
	})

	// Trading WS — JWT required (order placement must be authenticated)
	mux.HandleFunc("/trading", requireAuth(serveTrading))

	// Video signaling WS — JWT required
	mux.HandleFunc("/video", requireAuth(serveVideo))
	mux.HandleFunc("/video-test", serveVideoTest)

	mux.HandleFunc("/status", func(w http.ResponseWriter, r *http.Request) {
		serveStatus(chatHub, marketHub, w, r)
	})
	mux.HandleFunc("/", serveHome)

	addr := ":8080"
	log.Printf("🚀 Server listening on %s", addr)
	log.Printf("   Emulator  : ws://10.0.2.2:8080  (Android emulator → host)")
	if ip := localIP(); ip != "" {
		log.Printf("   WiFi/LAN  : ws://%s:8080  (real device on same network)", ip)
	}
	log.Printf("   Auth      : POST /api/auth/login")
	log.Printf("   Market  WS: /market")
	log.Printf("   Chat    WS: /ws?username=X")
	log.Printf("   Trading WS: /trading  (JWT required)")
	log.Printf("   Video   WS: /video?room=X  (JWT required)")
	log.Printf("   Video test: http://localhost:8080/video-test")
	log.Fatal(http.ListenAndServe(addr, mux))
}

// localIP returns the LAN IPv4 address (192.168.x.x or 10.x.x.x), skipping
// loopback and link-local (169.254.x.x) addresses from virtual adapters.
func localIP() string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return ""
	}
	var fallback string
	for _, addr := range addrs {
		ipnet, ok := addr.(*net.IPNet)
		if !ok {
			continue
		}
		ip4 := ipnet.IP.To4()
		if ip4 == nil || ip4.IsLoopback() || ip4.IsLinkLocalUnicast() {
			continue
		}
		// Prefer private LAN ranges.
		if ip4[0] == 192 || ip4[0] == 10 || (ip4[0] == 172 && ip4[1] >= 16 && ip4[1] <= 31) {
			return ip4.String()
		}
		if fallback == "" {
			fallback = ip4.String()
		}
	}
	return fallback
}
