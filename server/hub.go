package main

import (
	"encoding/json"
	"log"
	"sync"
)

// Hub quản lý tất cả WebSocket clients đang kết nối.
// Sử dụng channel-based concurrency để tránh race condition.
type Hub struct {
	clients    map[*Client]bool
	broadcast  chan []byte
	register   chan *Client
	unregister chan *Client
	mu         sync.RWMutex
}

func newHub() *Hub {
	return &Hub{
		clients:    make(map[*Client]bool),
		broadcast:  make(chan []byte, 256),
		register:   make(chan *Client),
		unregister: make(chan *Client),
	}
}

func (h *Hub) run() {
	for {
		select {
		case client := <-h.register:
			h.mu.Lock()
			h.clients[client] = true
			h.mu.Unlock()

			log.Printf("[HUB] Connected: %s (%s), total=%d", client.username, client.id[:8], h.clientCount())

			msg := newSystemMessage(client.username + " joined the chat")
			if data, err := json.Marshal(msg); err == nil {
				h.broadcast <- data
			}

		case client := <-h.unregister:
			h.mu.Lock()
			if _, ok := h.clients[client]; ok {
				delete(h.clients, client)
				close(client.send)
			}
			h.mu.Unlock()

			log.Printf("[HUB] Disconnected: %s, total=%d", client.username, h.clientCount())

			msg := newSystemMessage(client.username + " left the chat")
			if data, err := json.Marshal(msg); err == nil {
				h.broadcast <- data
			}

		case message := <-h.broadcast:
			h.mu.RLock()
			for client := range h.clients {
				select {
				case client.send <- message:
				default:
					// Buffer đầy → client quá chậm → kick
					close(client.send)
					delete(h.clients, client)
				}
			}
			h.mu.RUnlock()
		}
	}
}

func (h *Hub) clientCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients)
}
