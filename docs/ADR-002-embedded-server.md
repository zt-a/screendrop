# ADR-002: Embedded HTTP Server

**Статус:** Proposed

Использовать embedded Ktor Server внутри Android APK, чтобы не требовались отдельный backend и VPS.

Последствия: учитывать Android lifecycle, корректно останавливать engine, соблюдать требования Foreground Service, выполнять I/O вне Main Thread и не обещать бесконечную фоновую работу.
