# Testing Specification

## Уровни
**Unit:** token, expiration, filename, MIME, size, SHA-256, metadata, storage, state transitions.  
**Integration:** Ktor startup, health, session, upload, list, delete, close, invalid token, expired session, oversized/interrupted upload.  
**Instrumentation:** app startup, Compose navigation, D-pad, FileProvider, service lifecycle, NetworkCallback.  
**Physical device:** Hotspot и Android TV; эмулятор не подтверждает реальную маршрутизацию.

## Test cases
| ID | Сценарий | Ожидаемый результат |
|---|---|---|
| TC-001 | Нет сети | WAITING_NETWORK/ошибка |
| TC-002 | Сервер стартует | READY server state |
| TC-003 | GET health локально | 200 |
| TC-004 | GET health со смартфона | 200, если сеть допускает |
| TC-005 | Upload PDF | 201, файл сохранён |
| TC-006 | Неверный token | 401 |
| TC-007 | Закрытая сессия | запрос отклонён |
| TC-008 | Файл сверх лимита | 413 |
| TC-009 | Неподдерживаемый тип | 415 |
| TC-010 | Разрыв upload | нет готового файла |
| TC-011 | Параллельные клиенты | в пределах лимита |
| TC-012 | Превышен concurrency | 429 |
| TC-013 | Потеря Wi-Fi | UI обновлён |
| TC-014 | Повторное подключение | контролируемое восстановление |
| TC-015 | Открытие PDF | запускается viewer |
| TC-016 | Старый QR | token отклонён |

## Физический тест
Записать модель и Android version смартфона и TV, тип сети, результат GET с телефона, upload result, размер и SHA-256. Проверить: Hotspot → TV join → start server → QR → GET → upload PDF → display file.

## Целостность
На Linux:
```bash
sha256sum presentation.pdf
```
Сравнить с digest, рассчитанным TV.

## Отчёт
Для каждого теста фиксировать ID, дату, окружение, шаги, ожидаемый/фактический результат, PASS/FAIL/BLOCKED и лог. Не ставить PASS без выполнения. Отдельно указывать unit, emulator, integration и physical-device.
