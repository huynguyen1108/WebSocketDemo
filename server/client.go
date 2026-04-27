package main

import (
	"encoding/json"
	"log"
	"net/http"
	"time"

	"github.com/gorilla/websocket"
)

const (
	writeWait      = 10 * time.Second
	pongWait       = 60 * time.Second
	pingPeriod     = (pongWait * 9) / 10 // Ping trước khi pongWait hết hạn
	maxMessageSize = 512 * 1024           // 512 KB
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  4096,
	WriteBufferSize: 4096,
	CheckOrigin: func(r *http.Request) bool {
		return true // Allow all origins cho mục đích test
	},
}

type Client struct {
	id       string
	username string
	hub      *Hub
	conn     *websocket.Conn
	send     chan []byte
}

// readPump đọc message từ WebSocket connection và forward vào hub.broadcast.
// Chạy trong một goroutine riêng mỗi client.
func (c *Client) readPump() {
	defer func() {
		c.hub.unregister <- c
		c.conn.Close()
	}()

	c.conn.SetReadLimit(maxMessageSize)
	c.conn.SetReadDeadline(time.Now().Add(pongWait))
	c.conn.SetPongHandler(func(string) error {
		c.conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})

	for {
		_, rawMsg, err := c.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				log.Printf("[CLIENT] Read error %s: %v", c.username, err)
			}
			break
		}

		var msg Message
		if err := json.Unmarshal(rawMsg, &msg); err != nil {
			log.Printf("[CLIENT] Invalid JSON from %s: %v", c.username, err)
			continue
		}

		// Override các field quan trọng từ server để tránh giả mạo
		msg.UserID = c.id
		msg.Username = c.username
		msg.Timestamp = time.Now().UnixMilli()

		if data, err := json.Marshal(msg); err == nil {
			c.hub.broadcast <- data
		}
	}
}

// writePump gửi message từ hub xuống WebSocket connection.
// Chạy trong một goroutine riêng mỗi client.
func (c *Client) writePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		c.conn.Close()
	}()

	for {
		select {
		case message, ok := <-c.send:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				// Hub đã đóng channel → gửi close frame
				c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			w, err := c.conn.NextWriter(websocket.TextMessage)
			if err != nil {
				return
			}
			w.Write(message)

			// Flush thêm các message đang chờ vào cùng 1 write frame → giảm syscall
			pending := len(c.send)
			for i := 0; i < pending; i++ {
				w.Write([]byte{'\n'})
				w.Write(<-c.send)
			}

			if err := w.Close(); err != nil {
				return
			}

		case <-ticker.C:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}
