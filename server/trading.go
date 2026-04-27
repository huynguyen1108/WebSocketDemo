package main

import (
	"encoding/json"
	"fmt"
	"log"
	"math/rand"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

// ── Domain types ──────────────────────────────────────────────────────────────

type OrderStatus string

const (
	StatusPending     OrderStatus = "PENDING"
	StatusPartialFill OrderStatus = "PARTIAL_FILL"
	StatusFilled      OrderStatus = "FILLED"
	StatusCancelled   OrderStatus = "CANCELLED"
)

type TradingOrder struct {
	OrderId       string      `json:"orderId"`
	ClientOrderId string      `json:"clientOrderId"`
	Symbol        string      `json:"symbol"`
	Side          string      `json:"side"`
	OrderType     string      `json:"orderType"`
	Price         float64     `json:"price"`
	Quantity      int64       `json:"quantity"`
	FilledQty     int64       `json:"filledQty"`
	Status        OrderStatus `json:"status"`
	CreatedAt     int64       `json:"createdAt"`
	UpdatedAt     int64       `json:"updatedAt"`
}

type placeOrderRequest struct {
	ClientOrderId string  `json:"clientOrderId"`
	Symbol        string  `json:"symbol"`
	Side          string  `json:"side"`
	OrderType     string  `json:"orderType"`
	Price         float64 `json:"price"`
	Quantity      int64   `json:"quantity"`
}

type cancelOrderRequest struct {
	OrderId string `json:"orderId"`
}

type tradingMessage struct {
	Type    string          `json:"type"`
	Payload json.RawMessage `json:"payload"`
}

// ── Per-user order store ──────────────────────────────────────────────────────

type userOrderStore struct {
	mu     sync.Mutex
	orders map[string]*TradingOrder
}

func newUserOrderStore() *userOrderStore {
	return &userOrderStore{orders: make(map[string]*TradingOrder)}
}

func (s *userOrderStore) add(o *TradingOrder) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.orders[o.OrderId] = o
}

func (s *userOrderStore) get(id string) (*TradingOrder, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	o, ok := s.orders[id]
	return o, ok
}

func (s *userOrderStore) cancel(id string) (*TradingOrder, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	o, ok := s.orders[id]
	if !ok {
		return nil, fmt.Errorf("order not found: %s", id)
	}
	if o.Status == StatusFilled || o.Status == StatusCancelled {
		return nil, fmt.Errorf("cannot cancel order with status %s", o.Status)
	}
	o.Status = StatusCancelled
	o.UpdatedAt = time.Now().UnixMilli()
	return o, nil
}

func (s *userOrderStore) snapshot() []*TradingOrder {
	s.mu.Lock()
	defer s.mu.Unlock()
	list := make([]*TradingOrder, 0, len(s.orders))
	for _, o := range s.orders {
		list = append(list, o)
	}
	return list
}

// ── Global store registry ─────────────────────────────────────────────────────

var (
	userStoresMu sync.Mutex
	userStores   = make(map[string]*userOrderStore)
)

func getOrCreateStore(username string) *userOrderStore {
	userStoresMu.Lock()
	defer userStoresMu.Unlock()
	if s, ok := userStores[username]; ok {
		return s
	}
	s := newUserOrderStore()
	userStores[username] = s
	return s
}

// ── WebSocket handler ─────────────────────────────────────────────────────────

func serveTrading(username string, w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Println("[TRADING] Upgrade error:", err)
		return
	}
	defer conn.Close()

	store := getOrCreateStore(username)
	log.Printf("[TRADING] %s connected", username)

	sendTradingMsg(conn, "order_snapshot", store.snapshot())

	conn.SetReadLimit(64 * 1024)
	for {
		_, raw, err := conn.ReadMessage()
		if err != nil {
			log.Printf("[TRADING] %s disconnected: %v", username, err)
			break
		}

		var msg tradingMessage
		if err := json.Unmarshal(raw, &msg); err != nil {
			log.Printf("[TRADING] Parse error: %v", err)
			continue
		}

		switch msg.Type {
		case "place_order":
			var req placeOrderRequest
			if err := json.Unmarshal(msg.Payload, &req); err != nil {
				log.Printf("[TRADING] Bad place_order: %v", err)
				continue
			}
			now := time.Now().UnixMilli()
			order := &TradingOrder{
				OrderId:       generateID(),
				ClientOrderId: req.ClientOrderId,
				Symbol:        req.Symbol,
				Side:          req.Side,
				OrderType:     req.OrderType,
				Price:         req.Price,
				Quantity:      req.Quantity,
				FilledQty:     0,
				Status:        StatusPending,
				CreatedAt:     now,
				UpdatedAt:     now,
			}
			store.add(order)
			sendTradingMsg(conn, "order_ack", order)
			go simulateMatching(conn, store, order.OrderId)

		case "cancel_order":
			var req cancelOrderRequest
			if err := json.Unmarshal(msg.Payload, &req); err != nil {
				continue
			}
			updated, err := store.cancel(req.OrderId)
			if err != nil {
				log.Printf("[TRADING] Cancel: %v", err)
				continue
			}
			sendTradingMsg(conn, "order_update", updated)
		}
	}
}

// ── Order matching simulation ─────────────────────────────────────────────────

func simulateMatching(conn *websocket.Conn, store *userOrderStore, orderId string) {
	// 1-5 s before first (partial) fill
	time.Sleep(time.Duration(1000+rand.Intn(4000)) * time.Millisecond)

	o, ok := store.get(orderId)
	if !ok || o.Status == StatusCancelled {
		return
	}

	// Partial fill: 50-80 % of qty
	partialQty := int64(float64(o.Quantity) * (0.5 + rand.Float64()*0.3))
	if partialQty < 1 {
		partialQty = 1
	}

	store.mu.Lock()
	o.FilledQty = partialQty
	if partialQty >= o.Quantity {
		o.Status = StatusFilled
	} else {
		o.Status = StatusPartialFill
	}
	o.UpdatedAt = time.Now().UnixMilli()
	store.mu.Unlock()

	sendTradingMsg(conn, "order_update", o)
	if o.Status == StatusFilled {
		return
	}

	// 2-8 s more → full fill
	time.Sleep(time.Duration(2000+rand.Intn(6000)) * time.Millisecond)

	o2, ok := store.get(orderId)
	if !ok || o2.Status == StatusCancelled {
		return
	}

	store.mu.Lock()
	o2.FilledQty = o2.Quantity
	o2.Status = StatusFilled
	o2.UpdatedAt = time.Now().UnixMilli()
	store.mu.Unlock()

	sendTradingMsg(conn, "order_update", o2)
}

// ── Helper ────────────────────────────────────────────────────────────────────

func sendTradingMsg(conn *websocket.Conn, msgType string, payload interface{}) {
	payloadBytes, err := json.Marshal(payload)
	if err != nil {
		return
	}
	out, err := json.Marshal(map[string]json.RawMessage{
		"type":    json.RawMessage(`"` + msgType + `"`),
		"payload": payloadBytes,
	})
	if err != nil {
		return
	}
	conn.SetWriteDeadline(time.Now().Add(writeWait))
	conn.WriteMessage(websocket.TextMessage, out)
}
