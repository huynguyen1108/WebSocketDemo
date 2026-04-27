package main

import "sync"

// MarketHub là push-only hub: server → client.
// Client chỉ nhận dữ liệu, không gửi lên.
type MarketHub struct {
	clients map[chan []byte]bool
	mu      sync.RWMutex
}

func newMarketHub() *MarketHub {
	return &MarketHub{clients: make(map[chan []byte]bool)}
}

func (h *MarketHub) join() chan []byte {
	ch := make(chan []byte, 256)
	h.mu.Lock()
	h.clients[ch] = true
	h.mu.Unlock()
	return ch
}

func (h *MarketHub) leave(ch chan []byte) {
	h.mu.Lock()
	if _, ok := h.clients[ch]; ok {
		delete(h.clients, ch)
		close(ch)
	}
	h.mu.Unlock()
}

func (h *MarketHub) broadcast(data []byte) {
	h.mu.RLock()
	defer h.mu.RUnlock()
	for ch := range h.clients {
		select {
		case ch <- data:
		default:
			// Client quá chậm → bỏ qua tick này (không block)
		}
	}
}

func (h *MarketHub) clientCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients)
}
