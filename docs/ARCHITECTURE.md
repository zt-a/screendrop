# Software Architecture Document

## Цели
Минимальная сложность, локальная работа, потоковая передача, разделение UI/сети/сессий/хранилища.

## Контекст
```text
Browser (phone/desktop)
        | HTTP over TCP/IP
        v
Android TV: ScreenDrop
  ├─ Compose UI / ViewModel
  ├─ Network Manager
  ├─ Server Lifecycle Manager
  ├─ Ktor HTTP Server
  ├─ Session Manager
  ├─ Upload Manager
  └─ Private File Storage
```

## Компоненты
- **Presentation:** Home, Received Files, Settings, Network Diagnostics.
- **Network Manager:** ConnectivityManager, NetworkCallback, NetworkCapabilities, LinkProperties, выбор адреса.
- **Server Lifecycle Manager:** запуск/остановка Ktor, bind, порт, обработка смены сети.
- **HTTP Server:** web assets, health, session, upload, files, delete.
- **Session Manager:** ID, token, timestamps, status, validation/close/expire.
- **Upload Manager:** auth, лимиты, concurrency, multipart stream, SHA-256, temp file, finalize.
- **Storage:** private app-specific storage и metadata.

## Зависимости
UI → ViewModel/use cases → interfaces. Ktor routes вызывают сервисы. Storage не зависит от Ktor и Compose. Session Manager не зависит от UI.

## Запуск
1. Пользователь запускает приём.
2. Проверяется сеть.
3. Запускается Ktor.
4. Создаётся сессия.
5. Формируется QR URL.
6. READY отображается после успешного старта.

Различать `SERVER_READY` и `CLIENT_REACHABILITY_VERIFIED`: локальный запрос не доказывает доступ с телефона.

## Upload
HTTP route → token validation → limits → temporary file → streaming write + digest → finalize → metadata → UI update.

## Состояния
`STOPPED → STARTING → WAITING_NETWORK/CHECKING_NETWORK → READY ↔ RECEIVING`. Возможны `ERROR` и `STOPPED`.

## Android Service
Для длительной работы проверить актуальные требования Foreground Service и targetSdk. Не обещать бесконечную работу в фоне.

## Хранение
```text
files/
  received/<file-id>/content
  received/<file-id>/metadata.json
  temporary/<upload-id>.part
```

## ADR
1. Android TV — основной сервер.
2. Browser sender — без установки APK на телефон.
3. Embedded Ktor — без отдельного backend.
4. Streaming upload — ограничение RAM.
5. Hotspot compatibility — обязательный технический gate.

Будущий Phone Transfer Hub оформляется отдельным режимом и ADR.
