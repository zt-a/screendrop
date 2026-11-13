# Презентация ScreenDrop

- `ScreenDrop-presentation.pptx` — редактируемая презентация (10 слайдов, 16:9).
- `ScreenDrop-presentation.pdf` — готовый к показу файл.
- `SPEECH.md` — текст выступления на 5 минут с таймингом.
- `assets/` — QR-коды, сгенерированные через `qrencode`.
- `build_deck.py` — скрипт сборки презентации.
- `check_layout.py` — эвристическая проверка переполнения текстовых блоков.

## Перед выступлением

1. Откройте `ScreenDrop-presentation.pptx` и впишите имя и группу на титульном слайде.
2. Проверьте, что PDF пересобран после правок (иначе показывайте PPTX).
3. Заметки докладчика уже встроены в PPTX: «Вид → Заметки».
4. Для показа используйте PDF, если на компьютере нет PowerPoint/LibreOffice.

## Пересборка

```bash
python3 docs/presentation/build_deck.py
python3 docs/presentation/check_layout.py
cd docs/presentation
soffice --headless --convert-to pdf --outdir . ScreenDrop-presentation.pptx
```

Для сборки нужны Python 3, `python-pptx`, Pillow, LibreOffice и установленные шрифты
**Noto Sans** и **JetBrains Mono**. QR-коды пересоздаются так:

```bash
cd docs/presentation/assets
qrencode -o qr-url.png  -s 14 -m 2 -l M "http://192.168.43.112:8080/?token=8Fq2xN7rT4mK9pLw"
qrencode -o qr-wifi.png -s 14 -m 2 -l M "WIFI:S:ScreenDrop-5G;T:WPA;P:screendrop2026;;"
qrencode -o qr-repo.png -s 14 -m 2 -l M "https://github.com/zt-a/screendrop"
```
