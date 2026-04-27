package main

import "time"

const (
	MsgTypeJoin    = "join"
	MsgTypeLeave   = "leave"
	MsgTypeMessage = "message"
	MsgTypeSystem  = "system"
)

type Message struct {
	Type      string `json:"type"`
	UserID    string `json:"userId"`
	Username  string `json:"username"`
	Content   string `json:"content"`
	Timestamp int64  `json:"timestamp"`
}

func newSystemMessage(content string) Message {
	return Message{
		Type:      MsgTypeSystem,
		UserID:    "system",
		Username:  "System",
		Content:   content,
		Timestamp: time.Now().UnixMilli(),
	}
}
