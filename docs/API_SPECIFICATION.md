# HTTP API Specification

Base URL: `http://<TV_IP>:8080`  
Protocol: HTTP/1.1 over TCP. JSON UTF-8. Timestamps ISO-8601 UTC.

## Авторизация
Для защищённых API:
```http
Authorization: Bearer <SESSION_TOKEN>
```
Не передавать токен в query string API и не логировать его. Страница и статические assets доступны для bootstrap.

## Endpoints

### GET /
Возвращает web sender (`200 text/html`).

### GET /api/health
Публичный минимальный health:
```json
{"status":"ready","server":"ScreenDrop","version":"1.0.0"}
```

### GET /api/session
Bearer token required:
```json
{"active":true,"expiresAt":"2026-11-11T18:00:00Z","maxFileSizeBytes":104857600,"allowedExtensions":["pdf","pptx","docx","txt","jpg","jpeg","png","mp4"]}
```

### POST /api/upload
Bearer token; `multipart/form-data`: `file`, optional `senderName`.
Success `201`:
```json
{"success":true,"file":{"id":"opaque-random-id","name":"presentation.pdf","sizeBytes":2048000,"mimeType":"application/pdf","sha256":"hex-digest","receivedAt":"2026-11-11T17:30:00Z"}}
```

### GET /api/files
Bearer token:
```json
{"files":[{"id":"opaque-random-id","name":"presentation.pdf","sizeBytes":2048000,"mimeType":"application/pdf","sha256":"hex-digest","senderName":"Student","receivedAt":"2026-11-11T17:30:00Z"}]}
```

### DELETE /api/files/{id}
Bearer token; ID is internal opaque ID, never a path.
```json
{"success":true}
```

### POST /api/session/close
Bearer token; invalidates token.
```json
{"success":true,"status":"closed"}
```

## Error format
```json
{"error":{"code":"FILE_TOO_LARGE","message":"The file exceeds the configured size limit."}}
```

| HTTP | Meaning |
|---|---|
| 400 | Invalid request |
| 401 | Missing/invalid token |
| 403 | Closed session |
| 404 | Not found |
| 413 | File too large |
| 415 | Unsupported type |
| 429 | Too many uploads |
| 500 | Internal error |
| 503 | Server not ready |

## Лимиты
100 MiB/file, 3 concurrent uploads, senderName max 64 chars, filename max 255 chars (defaults; configurable). Проверять размер во время чтения. Не доверять MIME, присланному клиентом.

Progress в MVP можно показывать через XMLHttpRequest upload progress. WebSocket не обязателен.
