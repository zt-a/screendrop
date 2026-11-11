# Network Design Specification

## Топология Personal Hotspot
```text
Smartphone (Hotspot + Browser)
          |
          | HTTP over TCP/IP, если маршрутизация разрешает
          |
Android TV (Wi-Fi client + ScreenDrop server)
```

Hotspot может не позволять host smartphone обращаться к подключённому TV. Client isolation также может блокировать связь.

## Existing Wi-Fi
Телефон и TV подключены к одному AP. Общая сеть не гарантирует доступ: возможны VLAN, firewall и AP isolation.

## Параметры
- TCP;
- HTTP/1.1;
- порт 8080 по умолчанию;
- bind обычно `0.0.0.0`;
- QR URL `http://<TV_IP>:8080/?token=<SESSION_TOKEN>`.

Не использовать localhost в QR.

## Android APIs
ConnectivityManager, NetworkCallback, NetworkCapabilities, LinkProperties, LinkAddress. WifiManager — только при необходимости и с учётом ограничений API/permissions.

## Выбор IP
Получить активную Network, её LinkProperties, проверить адреса, исключить loopback и неподходящие интерфейсы, учитывать VPN/Ethernet/Wi-Fi. Не выбирать первый адрес без проверки.

## Диагностика
Различать NO_NETWORK, NO_IPV4, SERVER_BIND_FAILED, SERVER_RUNNING, LOCAL_HEALTH_OK, CLIENT_REACHABILITY_UNKNOWN, CLIENT_REACHABILITY_VERIFIED, CLIENT_REACHABILITY_FAILED.

Локальный health check подтверждает только локальную работу. Реальный критерий — HTTP GET/POST с отдельного смартфона.

## Смена сети
Обрабатывать onAvailable, onLost, onLinkPropertiesChanged и onCapabilitiesChanged. Обновлять адрес, QR и server lifecycle; не терять завершённые файлы; не публиковать незавершённые.

## Проверка
Записывать модели и версии смартфона/TV, тип сети, IP, HTTP GET и upload result. Ping может быть заблокирован; главным критерием является HTTP.

## Термины
TCP — надёжный упорядоченный поток байтов; IP — адресация и маршрутизация; HTTP — прикладной протокол; socket — сетевой endpoint; port — номер транспортного endpoint; client isolation — запрет прямого взаимодействия клиентов.
