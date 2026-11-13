# ScreenDrop
Локальная система передачи файлов на Android-экраны по QR-коду.

Студент открывает QR-ссылку в браузере смартфона или компьютера и отправляет файл на экран. Основной сценарий: смартфон создаёт Personal Hotspot, экран подключается к нему, встроенный HTTP-сервер принимает файл.

> Важно: Hotspot не гарантирует доступность сервера с телефона-хоста. До разработки полного UI необходимо проверить реальный HTTP GET и POST между физическими устройствами.

## Текущий статус (MVP, сборка 1.0.0)
- [x] Android-приложение (Kotlin + Compose + Ktor), touch-first UI — работает на планшетах, сенсорных панелях и Android TV
- [x] Гайд-экран «Нет сети» + кнопка системных настроек Wi-Fi
- [x] Network Monitor (ConnectivityManager, Wi-Fi/Ethernet, IPv4, VPN исключается)
- [x] Встроенный Ktor-сервер (CIO, 0.0.0.0:8080): `/`, `/api/health`, `/api/session`, `/api/upload`, `/api/files`, `DELETE /api/files/{id}`, `/api/session/close`
- [x] Сессии: токен 256 бит (SecureRandom, base64url), idle-таймаут 30 мин, ручное закрытие
- [x] QR-код (ZXing) с URL `http://<IP>:8080/?token=<TOKEN>`
- [x] Веб-отправитель (assets/web): выбор файлов, имя отправителя, прогресс (XHR), ошибки на русском
- [x] Streaming upload + SHA-256 во время записи, temp-файл, атомарная финализация
- [x] Лимиты: 100 MiB/файл (413), расширения (415), 3 параллельные загрузки (429), имя 255, senderName 64
- [x] Список файлов, открытие (FileProvider + ACTION_VIEW), удаление
- [x] 37 unit/интеграционных тестов (JVM) — все зелёные
- [ ] Физический тест: телефон (Hotspot) ↔ планшет (ADR-001 PoC)
- [ ] Foreground Service (вне MVP сегодня: сервер живёт при открытом приложении, экран не гаснет)

Сборка:
```bash
./gradlew assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
./gradlew test            # 37 тестов
```

## Документы
- [Полная документация](PROJECT_DOCUMENTATION.md)
- [SRS](docs/REQUIREMENTS.md)
- [Архитектура](docs/ARCHITECTURE.md)
- [Сеть](docs/NETWORK_DESIGN.md)
- [API](docs/API_SPECIFICATION.md)
- [Передача файлов](docs/FILE_TRANSFER.md)
- [Безопасность](docs/SECURITY.md)
- [Тестирование](docs/TESTING.md)
- [Установка](docs/INSTALLATION.md)
- [Руководство пользователя](docs/USER_GUIDE.md)
- [Демонстрация](docs/DEMO_GUIDE.md)
- [Презентация (тезисы)](docs/PRESENTATION.md)
- [Презентация к защите: PPTX + PDF](docs/presentation/README.md)
- [Текст выступления на 5 минут](docs/presentation/SPEECH.md)
- [Вопросы к защите](docs/DEFENSE_QUESTIONS.md)
- [Ограничения](docs/KNOWN_LIMITATIONS.md)

Стек: Kotlin, Android SDK, Jetpack Compose, Ktor Server, Coroutines, ConnectivityManager, ZXing, HTML/CSS/JavaScript. Внешний backend не требуется.
