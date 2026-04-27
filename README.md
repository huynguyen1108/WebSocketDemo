# WebSocket Demo — Stock Market & Chat

Demo ứng dụng Android kết hợp Go server, minh họa kiến trúc WebSocket thực tế với xác thực JWT, real-time market data, chat và đặt lệnh giao dịch.

## Cấu trúc dự án

```
WebSocketDemo/
├── server/      # Go WebSocket server
└── android/     # Android app (Jetpack Compose)
```

## Tính năng

| Tính năng | Mô tả |
|---|---|
| Xác thực | Đăng nhập JWT, token lưu encrypted trên thiết bị |
| Market data | Real-time giá cổ phiếu & chỉ số (WebSocket push) |
| Chat | Nhắn tin real-time nhiều người dùng |
| Đặt lệnh | Mua/bán cổ phiếu qua WebSocket authenticated |

## Yêu cầu

- **Server**: Go 1.21+
- **Android**: Android Studio Hedgehog+, JDK 17, Android SDK 34

## Chạy nhanh

```bash
# 1. Start server
cd server && go run .

# 2. Mở android/ trong Android Studio, chạy app trên emulator
#    Server URL: ws://10.0.2.2:8080
```

Xem chi tiết trong [server/README.md](server/README.md) và [android/README.md](android/README.md).

## Demo accounts

| Username | Password |
|---|---|
| demo | password |
| admin | admin123 |
| user1 | pass123 |
