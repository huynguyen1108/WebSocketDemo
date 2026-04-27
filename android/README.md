# WebSocket Demo — Android App

Ứng dụng Android demo real-time WebSocket với kiến trúc Clean Architecture + MVI, Jetpack Compose UI.

## Stack

| Layer | Thư viện |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| DI | Hilt |
| Async | Kotlin Coroutines, Flow |
| Network | OkHttp, Retrofit 2, kotlinx.serialization |
| Storage | DataStore (typed) + Android Keystore AES-256-GCM |
| Logging | Timber |

## Kiến trúc

### Module graph

```
:app
 ├── :feature:auth:presentation
 │    └── :feature:auth:domain
 │         └── :feature:auth:data
 ├── :feature:stock:presentation
 │    └── :feature:stock:domain
 │         └── :feature:stock:data
 ├── :feature:chat:presentation
 │    └── :feature:chat:domain
 │         └── :feature:chat:data
 ├── :feature:order:presentation
 │    └── :feature:order:domain
 │         └── :feature:order:data
 ├── :core:network
 ├── :core:security
 └── :core:common
```

Mỗi feature chia 3 layer:
- **domain**: interface, use case, model — không phụ thuộc framework
- **data**: repository impl, DataSource, DTO, DI module
- **presentation**: ViewModel, UI State, Composable screens

### Convention plugins (`build-logic/`)

| Plugin | Áp dụng cho |
|---|---|
| `wschat.android.application` | `:app` |
| `wschat.android.library` | Core modules |
| `wschat.android.feature` | Feature modules (tự động thêm Hilt, Compose, Timber) |

## Tính năng

### Xác thực
- Đăng nhập bằng username/password qua REST API (`POST /api/auth/login`)
- JWT token lưu trong **DataStore** với mã hóa **AES-256-GCM** (Android Keystore)
- Token tự động đính kèm header `Authorization: Bearer` khi kết nối WebSocket
- Logout xóa session, disconnect WebSocket, navigate về màn hình login

### Market Data (`/market`)
- Kết nối WebSocket tự động sau khi đăng nhập
- Nhận snapshot toàn bộ cổ phiếu và chỉ số ngay khi kết nối
- Real-time update từng cổ phiếu (800ms) và chỉ số (2s)
- Tìm kiếm, sắp xếp theo giá/thay đổi/volume
- Auto-reconnect theo exponential backoff khi mất kết nối
- Phát hiện Doze mode, tự disconnect để tiết kiệm pin

### Chat (`/ws`)
- Chat real-time nhiều người dùng
- Auto-reconnect

### Đặt lệnh (`/trading`)
- Đặt lệnh mua/bán cổ phiếu (limit/market order)
- Hủy lệnh đang chờ
- Nhận update trạng thái lệnh real-time (pending → filled/cancelled)
- Yêu cầu JWT — server từ chối kết nối nếu token không hợp lệ

## WebSocket Client

`OkHttpWebSocketClient` (`core:network`) — dùng **generation counter** để tránh stale events:

- Mỗi lần `connect()` tăng `generation`
- `WebSocketCallback` chỉ emit events khi `gen == generation` (tức là connection hiện tại)
- Stale connection mid-handshake bị `cancel()` ngay lập tức
- `disconnect()` tăng generation → callback của connection cũ bị ignore

```
connect() → gen++ → cancel(old) → new WebSocket(gen)
disconnect() → gen++ → close(old)
```

## Bảo mật

| Thành phần | Chi tiết |
|---|---|
| Token storage | DataStore typed + custom `Serializer<SessionData>` |
| Mã hóa | AES-256-GCM, key trong Android Keystore (hardware-backed nếu có) |
| IV | 12 byte random, prepend vào ciphertext |
| Session cache | `@Volatile var cached` — đọc sync không cần suspend |

## Cài đặt & Chạy

### Yêu cầu
- Android Studio Hedgehog (2023.1.1) trở lên
- JDK 17
- Android SDK 34 (compileSdk), minSdk 26

### Bước chạy

1. Clone repo, mở thư mục `android/` trong Android Studio
2. Chờ Gradle sync
3. Khởi động Go server (xem [server/README.md](../server/README.md))
4. Chạy app trên emulator
5. Nhập server URL: `ws://10.0.2.2:8080` (emulator trỏ về localhost host machine)
6. Đăng nhập với tài khoản demo

### Demo accounts

| Username | Password |
|---|---|
| demo | password |
| admin | admin123 |
| user1 | pass123 |

## Cấu trúc thư mục

```
android/
├── app/                        # Entry point, Navigation, Application class
├── build-logic/                # Convention plugins
│   └── convention/
│       └── src/main/kotlin/
├── core/
│   ├── common/                 # AppResult, Scope qualifiers, Dispatchers
│   ├── network/                # OkHttpWebSocketClient, NetworkMonitor
│   └── security/               # TokenStore, TokenDataStore, CryptoManager
├── feature/
│   ├── auth/
│   │   ├── domain/             # AuthRepository, LoginUseCase, LogoutUseCase
│   │   ├── data/               # AuthRepositoryImpl, AuthRemoteDataSource, Retrofit
│   │   └── presentation/       # LoginViewModel, LoginScreen
│   ├── stock/
│   │   ├── domain/             # StockRepository, ObserveStocksUseCase, ...
│   │   ├── data/               # StockWebSocketDataSource, MarketEvent sealed class
│   │   └── presentation/       # StockViewModel, StockScreen
│   ├── chat/
│   │   ├── domain/
│   │   ├── data/               # ChatWebSocketDataSource
│   │   └── presentation/
│   └── order/
│       ├── domain/
│       ├── data/               # OrderWebSocketDataSource
│       └── presentation/
└── gradle/
    └── libs.versions.toml      # Version catalog
```
