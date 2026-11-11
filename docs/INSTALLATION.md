# Installation and Build Guide

## Среда
Android Studio или Android SDK command-line tools, JDK совместимой версии, Android SDK Platform/Build Tools, Gradle Wrapper, ADB. Точные версии JDK/AGP/Gradle/compileSdk фиксируются в проекте после создания build skeleton.

## Сборка
```bash
./gradlew assembleDebug
```
Ожидаемый стандартный путь:
`app/build/outputs/apk/debug/app-debug.apk`. Подтвердить фактический путь после сборки.

## ADB
```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Android TV
Установить через ADB USB/network или доверенный файловый менеджер. Разрешение установки из неизвестных источников включать только при необходимости.

## Первый запуск
1. Запустить ScreenDrop.
2. Подключить TV к Wi-Fi/Hotspot.
3. Выдать необходимые разрешения.
4. Нажать Start Receiving.
5. Проверить диагностику.
6. Открыть QR с телефона.

## Логи
```bash
adb logcat
```

Release signing не должен использовать debug keystore. Не коммитить keystore и пароли в Git.
