# WebSocket Demo — Go Server

Go server cung cấp REST API đăng nhập và ba WebSocket endpoint: market data, chat, và trading.

## Stack

- **Go** 1.21
- **gorilla/websocket** v1.5.1 — WebSocket upgrade & I/O
- **golang-jwt/jwt** v5.3.1 — JWT HS256

## Kiến trúc

```
main.go          Routing, khởi tạo các hub
auth.go          POST /api/auth/login, JWT generate/validate, middleware requireAuth
market_hub.go    MarketHub — push-only broadcast (server → clients)
main.go          serveMarket() — /market WebSocket handler
hub.go           Hub — chat pub/sub (register/unregister/broadcast)
client.go        Client — read/write pump cho chat
trading.go       serveTrading() — /trading WebSocket handler, order matching
stock.go         StockSimulator — sinh dữ liệu giả lập
message.go       Định nghĩa message types
```

## Endpoints

### REST

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| POST | `/api/auth/login` | — | Đăng nhập, trả về JWT |

**Request:**
```json
{ "username": "demo", "password": "password" }
```

**Response:**
```json
{ "token": "<JWT>", "userId": "demo" }
```

### WebSocket

| Path | Auth | Mô tả |
|---|---|---|
| `/market` | Tùy chọn | Real-time stock & index data |
| `/ws` | Tùy chọn | Chat nhiều người dùng |
| `/trading` | **Bắt buộc** | Đặt/hủy lệnh giao dịch |

Auth qua header: `Authorization: Bearer <token>` hoặc query param `?token=<token>`.

## WebSocket Message Format

### `/market` — Server → Client

```jsonc
// Snapshot (gửi ngay khi kết nối)
{ "type": "snapshot", "payload": { "stocks": [...], "indices": [...], "status": "open" } }

// Stock tick (mỗi 800ms)
{ "type": "stock_tick", "payload": { "symbol": "VIC", "price": 45200, "change": 200, ... } }

// Index tick (mỗi 2s)
{ "type": "index_tick", "payload": { "name": "VN-Index", "value": 1248.5, ... } }
```

### `/trading` — Bidirectional

**Client → Server:**
```jsonc
// Đặt lệnh
{ "type": "place_order", "payload": { "clientOrderId": "...", "symbol": "VIC", "side": "buy", "orderType": "limit", "price": 45000, "quantity": 100 } }

// Hủy lệnh
{ "type": "cancel_order", "payload": { "orderId": "..." } }
```

**Server → Client:**
```jsonc
// Xác nhận lệnh
{ "type": "order_ack", "payload": { "orderId": "...", "status": "pending", ... } }

// Khớp lệnh (giả lập)
{ "type": "order_update", "payload": { "orderId": "...", "status": "filled", "matchedQuantity": 100, ... } }

// Hủy thành công
{ "type": "order_cancelled", "payload": { "orderId": "..." } }
```

### `/ws` — Chat

**Client → Server:**
```json
{ "type": "message", "content": "Hello!" }
```

**Server → Client:**
```json
{ "id": "abc123", "username": "demo", "content": "Hello!", "timestamp": 1234567890 }
```

## Chạy

```bash
cd server
go run .
# Server lắng nghe tại :8080
```

```bash
# Build binary
go build -o server .
./server
```

## Demo Accounts

| Username | Password |
|---|---|
| demo | password |
| admin | admin123 |
| user1 | pass123 |

> JWT secret được hardcode cho mục đích demo. Thay bằng biến môi trường trong production.

## Web Demo

Mở trình duyệt tại `http://localhost:8080` để xem market data trực tiếp (không cần app Android).
