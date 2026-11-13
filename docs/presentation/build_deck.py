#!/usr/bin/env python3
"""Builds the ScreenDrop university presentation (PPTX).

Run from repository root:
    python3 docs/presentation/build_deck.py
Then export PDF:
    libreoffice --headless --convert-to pdf --outdir docs/presentation \
        docs/presentation/ScreenDrop-presentation.pptx
"""

from pathlib import Path

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, MSO_AUTO_SIZE, PP_ALIGN
from pptx.oxml.ns import qn
from pptx.util import Emu, Inches, Pt

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "presentation"
ASSETS = OUT / "assets"

# ---------------------------------------------------------------- palette
BG = RGBColor(0x0B, 0x11, 0x20)
BG_SOFT = RGBColor(0x10, 0x18, 0x2A)
CARD = RGBColor(0x15, 0x1E, 0x2F)
CARD_HI = RGBColor(0x1B, 0x27, 0x40)
BORDER = RGBColor(0x26, 0x33, 0x4D)
TEXT = RGBColor(0xE9, 0xEF, 0xFA)
MUTED = RGBColor(0x93, 0xA3, 0xBF)
DIM = RGBColor(0x6B, 0x7C, 0x9B)
ACCENT = RGBColor(0x5B, 0x8D, 0xEF)
ACCENT2 = RGBColor(0x2D, 0xD4, 0xA7)
AMBER = RGBColor(0xF5, 0xB8, 0x4D)
RED = RGBColor(0xFF, 0x7A, 0x7A)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)

BODY = "Noto Sans"
MONO = "JetBrains Mono"

W, H = 13.333, 7.5
MARGIN = 0.75
CONTENT_W = W - 2 * MARGIN

prs = Presentation()
prs.slide_width = Inches(W)
prs.slide_height = Inches(H)
prs.core_properties.title = "ScreenDrop — локальная передача файлов по QR"
prs.core_properties.author = "ScreenDrop project"
prs.core_properties.subject = "Курсовой проект, системное программирование"
prs.core_properties.language = "ru-RU"

PAGE = {"n": 0}


# ---------------------------------------------------------------- helpers
def set_font(run, name=BODY):
    run.font.name = name
    rPr = run._r.get_or_add_rPr()
    for tag in ("a:latin", "a:ea", "a:cs"):
        el = rPr.find(qn(tag))
        if el is None:
            from pptx.oxml import parse_xml

            el = parse_xml(
                '<a:%s xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"/>'
                % tag.split(":")[1]
            )
            rPr.append(el)
        el.set("typeface", name)


def new_slide(bg=BG):
    s = prs.slides.add_slide(prs.slide_layouts[6])
    fill = s.background.fill
    fill.solid()
    fill.fore_color.rgb = bg
    PAGE["n"] += 1
    return s


def rect(
    slide,
    x,
    y,
    w,
    h,
    fill=CARD,
    line=None,
    radius=0.06,
    shape=MSO_SHAPE.ROUNDED_RECTANGLE,
    line_w=1.0,
):
    shp = slide.shapes.add_shape(shape, Inches(x), Inches(y), Inches(w), Inches(h))
    if fill is None:
        shp.fill.background()
    else:
        shp.fill.solid()
        shp.fill.fore_color.rgb = fill
    if line is None:
        shp.line.fill.background()
    else:
        shp.line.color.rgb = line
        shp.line.width = Pt(line_w)
    if shape == MSO_SHAPE.ROUNDED_RECTANGLE and radius is not None:
        try:
            shp.adjustments[0] = radius
        except Exception:
            pass
    shp.shadow.inherit = False
    return shp


def textbox(slide, x, y, w, h, anchor=MSO_ANCHOR.TOP):
    tb = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = tb.text_frame
    tf.word_wrap = True
    tf.auto_size = MSO_AUTO_SIZE.NONE
    tf.vertical_anchor = anchor
    tf.margin_left = 0
    tf.margin_right = 0
    tf.margin_top = 0
    tf.margin_bottom = 0
    return tf


def para(
    tf,
    runs,
    size=16,
    color=TEXT,
    bold=False,
    font=BODY,
    align=PP_ALIGN.LEFT,
    space_before=0,
    space_after=0,
    line_spacing=1.15,
    first=False,
):
    p = tf.paragraphs[0] if first else tf.add_paragraph()
    p.alignment = align
    p.space_before = Pt(space_before)
    p.space_after = Pt(space_after)
    p.line_spacing = line_spacing
    if isinstance(runs, str):
        runs = [(runs, {})]
    for value, style in runs:
        r = p.add_run()
        r.text = value
        r.font.size = Pt(style.get("size", size))
        r.font.bold = style.get("bold", bold)
        r.font.italic = style.get("italic", False)
        r.font.color.rgb = style.get("color", color)
        set_font(r, style.get("font", font))
    return p


def text(
    slide,
    x,
    y,
    w,
    h,
    value,
    size=16,
    color=TEXT,
    bold=False,
    font=BODY,
    align=PP_ALIGN.LEFT,
    anchor=MSO_ANCHOR.TOP,
    line_spacing=1.15,
):
    tf = textbox(slide, x, y, w, h, anchor)
    para(
        tf,
        value,
        size=size,
        color=color,
        bold=bold,
        font=font,
        align=align,
        line_spacing=line_spacing,
        first=True,
    )
    return tf


def header(slide, eyebrow, title, accent=ACCENT):
    text(slide, MARGIN, 0.42, 8.5, 0.3, eyebrow.upper(), size=10.5, color=accent, bold=True)
    rect(slide, MARGIN, 0.74, 0.52, 0.05, fill=accent, radius=None, shape=MSO_SHAPE.RECTANGLE)
    text(slide, MARGIN, 0.9, CONTENT_W, 0.85, title, size=31, bold=True, color=TEXT)


def footer(slide, label="ScreenDrop · локальная передача файлов"):
    if PAGE["n"] == 1:
        return
    text(slide, MARGIN, H - 0.44, 6.0, 0.25, label, size=9, color=DIM)
    text(
        slide,
        W - MARGIN - 0.8,
        H - 0.44,
        0.8,
        0.25,
        f"{PAGE['n']:02d}",
        size=9,
        color=DIM,
        align=PP_ALIGN.RIGHT,
        font=MONO,
    )


def notes(slide, value):
    slide.notes_slide.notes_text_frame.text = value


def pill(slide, x, y, w, h, value, color=ACCENT, size=11, font=BODY, fill=None):
    rect(slide, x, y, w, h, fill=fill if fill is not None else CARD_HI, line=None, radius=0.5)
    text(
        slide,
        x,
        y,
        w,
        h,
        value,
        size=size,
        color=color,
        bold=True,
        font=font,
        align=PP_ALIGN.CENTER,
        anchor=MSO_ANCHOR.MIDDLE,
    )


def bullet(
    tf,
    value,
    size=15.5,
    color=TEXT,
    marker_color=ACCENT,
    space_after=9,
    bold_prefix=None,
    first=False,
):
    runs = [("•  ", {"color": marker_color, "bold": True})]
    if bold_prefix:
        runs.append((bold_prefix, {"color": TEXT, "bold": True, "size": size}))
    runs.append((value, {"color": color, "size": size}))
    para(tf, runs, size=size, space_after=space_after, line_spacing=1.12, first=first)


def card(slide, x, y, w, h, fill=CARD, line=BORDER, radius=0.08):
    return rect(slide, x, y, w, h, fill=fill, line=line, radius=radius)


def arrow(slide, x, y, w=0.3, h=0.26):
    shp = slide.shapes.add_shape(MSO_SHAPE.RIGHT_ARROW, Inches(x), Inches(y), Inches(w), Inches(h))
    shp.fill.solid()
    shp.fill.fore_color.rgb = BORDER
    shp.line.fill.background()
    shp.shadow.inherit = False
    return shp


def picture(slide, path, x, y, w=None, h=None):
    kw = {}
    if w:
        kw["width"] = Inches(w)
    if h:
        kw["height"] = Inches(h)
    return slide.shapes.add_picture(str(path), Inches(x), Inches(y), **kw)


# ================================================================ 1. title
s = new_slide()
# soft glow blocks
g1 = rect(s, 8.7, -1.5, 6.2, 6.2, fill=RGBColor(0x16, 0x25, 0x45), shape=MSO_SHAPE.OVAL, radius=None)
g1.fill.transparency = 0.0
text(s, MARGIN, 0.82, 8.4, 0.3, "КУРСОВОЙ ПРОЕКТ · СИСТЕМНОЕ ПРОГРАММИРОВАНИЕ", size=11, color=ACCENT, bold=True)
rect(s, MARGIN, 1.18, 0.62, 0.06, fill=ACCENT2, radius=None, shape=MSO_SHAPE.RECTANGLE)

tf = textbox(s, MARGIN, 1.42, 8.6, 1.25)
para(
    tf,
    [("Screen", {"color": WHITE}), ("Drop", {"color": ACCENT})],
    size=74,
    bold=True,
    line_spacing=1.0,
    first=True,
)
text(
    s,
    MARGIN,
    2.72,
    7.6,
    0.95,
    "Локальная передача файлов\nна Android-экран по QR-коду",
    size=23,
    color=TEXT,
    bold=True,
    line_spacing=1.22,
)
text(
    s,
    MARGIN,
    3.9,
    7.5,
    0.75,
    "Экран — HTTP-сервер. Телефон — браузер.\nБез установки приложений и без интернета.",
    size=13.5,
    color=MUTED,
    line_spacing=1.3,
)

chips = [("Kotlin", 1.0), ("Jetpack Compose", 1.62), ("Ktor CIO", 1.12), ("TCP/IP · HTTP", 1.42), ("MediaStore", 1.28)]
cx = MARGIN
for label, cw in chips:
    pill(s, cx, 4.95, cw, 0.36, label, color=ACCENT, size=10.5, fill=RGBColor(0x16, 0x22, 0x3A))
    cx += cw + 0.14

rect(s, MARGIN, 5.72, 7.4, 0.012, fill=BORDER, radius=None, shape=MSO_SHAPE.RECTANGLE)
text(s, MARGIN, 5.92, 5.2, 0.3, "Выполнил: ______________________", size=12.5, color=MUTED)
text(s, MARGIN, 6.28, 5.2, 0.3, "Группа: __________   ·   2026", size=12.5, color=MUTED)

card(s, 9.45, 1.62, 3.3, 4.62, fill=RGBColor(0x11, 0x1A, 0x2C))
rect(s, 9.62, 1.82, 2.96, 2.96, fill=WHITE, radius=0.07)
picture(s, ASSETS / "qr-url.png", 9.78, 1.98, w=2.64)
text(
    s,
    9.55,
    4.98,
    3.1,
    0.5,
    "Сканируйте камерой",
    size=13,
    color=TEXT,
    bold=True,
    align=PP_ALIGN.CENTER,
)
text(
    s,
    9.5,
    5.32,
    3.2,
    0.7,
    "откроется веб-отправитель ScreenDrop",
    size=10.5,
    color=MUTED,
    align=PP_ALIGN.CENTER,
    line_spacing=1.2,
)
notes(
    s,
    "Здравствуйте. Мой проект — ScreenDrop: локальная передача файлов на Android-экран по QR-коду. "
    "Показываю работающий MVP: экран поднимает HTTP-сервер, телефон открывает ссылку из QR в браузере. "
    "Главное — никакого облака и установки приложения на телефон.",
)

# ================================================================ 2. problem
s = new_slide()
header(s, "Проблема", "Файл на большой экран — это 5 лишних шагов")
cards = [
    ("01", "Мессенджеры", "Нужен аккаунт, фото и документы сжимаются, зависит от интернета и чужого сервиса."),
    ("02", "USB и флешки", "Физический доступ к экрану, риск заражения, на аудитории это медленно и неудобно."),
    ("03", "Облачные диски", "Внешний сервис, лимиты, приватность документов и лишние авторизации."),
]
for i, (num, title, body) in enumerate(cards):
    x = MARGIN + i * (3.75 + 0.29)
    card(s, x, 1.8, 3.75, 2.3)
    text(s, x + 0.28, 2.0, 0.8, 0.4, num, size=15, color=ACCENT2, bold=True, font=MONO)
    text(s, x + 0.28, 2.42, 3.2, 0.4, title, size=19, bold=True)
    text(s, x + 0.28, 2.95, 3.18, 1.0, body, size=13, color=MUTED, line_spacing=1.28)

card(s, MARGIN, 4.42, CONTENT_W, 2.05, fill=BG_SOFT)
text(s, 1.05, 4.68, 6.9, 0.45, "Экран сам становится сервером", size=25, bold=True)
text(
    s,
    1.05,
    5.18,
    6.9,
    0.4,
    "QR  →  браузер  →  multipart  →  файл на экране",
    size=13,
    color=ACCENT2,
    font=MONO,
)
text(
    s,
    1.05,
    5.62,
    6.8,
    0.55,
    "Ничего не устанавливаем на телефон и не отправляем данные в интернет.",
    size=12,
    color=MUTED,
)
metrics = [("1", "QR-код"), ("0", "приложений"), ("0", "облаков")]
for i, (big, label) in enumerate(metrics):
    x = 8.45 + i * 1.42
    text(s, x, 4.75, 1.3, 0.6, big, size=34, color=ACCENT, bold=True, font=MONO)
    text(s, x, 5.35, 1.3, 0.3, label, size=10.5, color=MUTED)
footer(s)
notes(
    s,
    "Сейчас файл на большой экран обычно передают через мессенджер, флешку или облако: это аккаунты, сжатие и зависимость от интернета. "
    "ScreenDrop превращает сам экран в приёмник: на телефоне не нужно ничего ставить, достаточно камеры и браузера.",
)

# ================================================================ 3. architecture
s = new_slide()
header(s, "Архитектура", "Один APK — и сервер, и приёмник файлов")
boxes = [
    ("Браузер", "HTML / CSS / JS", ACCENT),
    ("TCP/IP", "локальная сеть", MUTED),
    ("Ktor CIO", "0.0.0.0:8080", ACCENT),
    ("UploadManager", "streaming + SHA-256", ACCENT2),
    ("FileStorage", "Documents/ScreenDrop", ACCENT2),
]
bw, gap, bh, by = 1.95, 0.44, 1.22, 1.95
bx = 1.0
for i, (title, sub, color) in enumerate(boxes):
    card(s, bx, by, bw, bh)
    rect(s, bx, by, bw, 0.05, fill=color, radius=None, shape=MSO_SHAPE.RECTANGLE)
    text(s, bx + 0.1, by + 0.28, bw - 0.2, 0.32, title, size=14, bold=True, align=PP_ALIGN.CENTER)
    text(
        s,
        bx + 0.1,
        by + 0.68,
        bw - 0.2,
        0.32,
        sub,
        size=8.5,
        color=MUTED,
        align=PP_ALIGN.CENTER,
        font=MONO if i != 0 else BODY,
    )
    if i < len(boxes) - 1:
        arrow(s, bx + bw + 0.07, by + 0.5, w=0.3, h=0.24)
    bx += bw + gap

support = [
    ("SessionManager", "токен 256 бит · idle 30 мин"),
    ("NetworkMonitor", "ConnectivityManager · IPv4"),
    ("MainViewModel + UI", "state machine · QR"),
]
sw = 3.72
for i, (title, sub) in enumerate(support):
    x = MARGIN + i * (sw + 0.32)
    card(s, x, 4.35, sw, 0.98, fill=CARD, line=BORDER)
    text(s, x + 0.24, 4.53, sw - 0.4, 0.3, title, size=13.5, bold=True)
    text(s, x + 0.24, 4.88, sw - 0.4, 0.28, sub, size=10.5, color=MUTED)

card(s, MARGIN, 5.72, CONTENT_W, 0.86, fill=BG_SOFT)
text(s, 1.0, 5.9, 0.35, 0.35, "→", size=18, color=ACCENT2, bold=True)
text(
    s,
    1.35,
    5.94,
    10.9,
    0.45,
    "Внешнего backend нет: все компоненты работают внутри Android-приложения, данные не покидают локальную сеть.",
    size=13.5,
    color=TEXT,
)
footer(s)
notes(
    s,
    "Архитектура линейная: браузер отправляет HTTP-запрос по локальной сети, Ktor принимает его, UploadManager пишет потоково, FileStorage финализирует файл. "
    "Рядом — сессии, мониторинг сети и UI. Внешний сервер не нужен: всё живёт в одном APK.",
)

# ================================================================ 4. network
s = new_slide()
header(s, "Сеть", "Как телефон находит экран")
tf = textbox(s, MARGIN, 1.78, 6.55, 3.5)
bullet(tf, "Два сценария: Personal Hotspot телефона или общая Wi-Fi-сеть.", bold_prefix="Сценарии. ", first=True)
bullet(tf, "ConnectivityManager + NetworkCallback: Wi-Fi/Ethernet, IPv4, VPN исключён.", bold_prefix="Мониторинг. ")
bullet(tf, "Сервер слушает 0.0.0.0:8080 и доступен по локальному IP.", bold_prefix="Bind. ")
bullet(tf, "В QR — адрес экрана и одноразовый токен сессии.", bold_prefix="Адрес. ")

card(s, MARGIN, 4.95, 6.55, 1.55, fill=RGBColor(0x2A, 0x22, 0x14), line=RGBColor(0x55, 0x42, 0x24))
text(s, 1.0, 5.12, 0.35, 0.35, "!", size=18, color=AMBER, bold=True)
text(s, 1.38, 5.1, 5.7, 0.3, "Главный риск MVP", size=13, color=AMBER, bold=True)
text(
    s,
    1.38,
    5.42,
    5.75,
    0.9,
    "Локальный bind не доказывает доступность с телефона: hotspot и гостевой Wi-Fi могут изолировать клиентов. Проверяем реальным GET/POST.",
    size=11.5,
    color=RGBColor(0xE8, 0xD5, 0xB0),
    line_spacing=1.25,
)

card(s, 7.6, 1.72, 4.98, 4.78)
rect(s, 7.82, 1.94, 3.06, 3.06, fill=WHITE, radius=0.07)
picture(s, ASSETS / "qr-url.png", 7.99, 2.11, w=2.72)
text(s, 7.82, 5.12, 3.06, 0.3, "QR сессии", size=14, bold=True, align=PP_ALIGN.CENTER)
text(
    s,
    7.78,
    5.5,
    4.6,
    0.55,
    "http://192.168.43.112:8080/?token=…",
    size=9.5,
    color=MUTED,
    font=MONO,
    align=PP_ALIGN.CENTER,
)
footer(s)
notes(
    s,
    "Сеть — самая рискованная часть. Мы отслеживаем Wi-Fi и Ethernet через ConnectivityManager, берём IPv4 и исключаем VPN. "
    "При этом успешный локальный health-check ничего не доказывает: доступность проверяется только реальным запросом с другого устройства.",
)

# ================================================================ 5. API
s = new_slide()
header(s, "Протокол", "HTTP API и жизненный цикл сессии")
rows = [
    ("GET", "/", "веб-отправитель (assets)", ACCENT2),
    ("GET", "/api/health", "проверка живости", ACCENT2),
    ("GET", "/api/session", "статус и токен сессии", ACCENT2),
    ("POST", "/api/upload", "multipart-загрузка файла", ACCENT),
    ("GET", "/api/files", "список принятых файлов", ACCENT2),
    ("DELETE", "/api/files/{id}", "удаление файла", RED),
    ("POST", "/api/session/close", "закрыть сессию", ACCENT),
]
y = 1.78
for method, path, desc, color in rows:
    card(s, MARGIN, y, 7.45, 0.53, fill=CARD if rows.index((method, path, desc, color)) % 2 == 0 else BG_SOFT, line=None)
    pill(s, MARGIN + 0.14, y + 0.12, 0.9, 0.29, method, color=color, size=8.5, font=MONO)
    text(s, MARGIN + 1.2, y + 0.14, 2.95, 0.28, path, size=11.5, color=TEXT, font=MONO)
    text(s, MARGIN + 4.2, y + 0.15, 3.1, 0.28, desc, size=11, color=MUTED)
    y += 0.61

text(
    s,
    MARGIN,
    6.22,
    7.45,
    0.3,
    "Upload, files и delete требуют  Authorization: Bearer <token>",
    size=10,
    color=DIM,
    font=MONO,
)

card(s, 8.55, 1.78, 4.03, 4.74)
text(s, 8.8, 1.98, 3.5, 0.35, "Сессия", size=17, bold=True)
tf = textbox(s, 8.8, 2.42, 3.55, 1.9)
bullet(tf, "256 бит из SecureRandom → base64url", size=11.5, space_after=6, first=True)
bullet(tf, "idle-таймаут 30 минут", size=11.5, space_after=6)
bullet(tf, "закрытие инвалидирует токен", size=11.5, space_after=6)
bullet(tf, "новая сессия = новый QR", size=11.5, space_after=6)

rect(s, 8.8, 4.02, 3.5, 0.012, fill=BORDER, radius=None, shape=MSO_SHAPE.RECTANGLE)
text(s, 8.8, 4.16, 3.5, 0.3, "КОДЫ ОТВЕТА", size=9.5, color=ACCENT, bold=True)
codes = [
    ("200 / 201", "успех"),
    ("400 / 401", "запрос / токен"),
    ("413 / 415", "размер / формат"),
    ("429", "> 3 загрузок"),
]
yy = 4.52
for code, meaning in codes:
    text(s, 8.8, yy, 1.15, 0.26, code, size=10, color=ACCENT2, font=MONO, bold=True)
    text(s, 10.0, yy, 2.2, 0.26, meaning, size=10.5, color=MUTED)
    yy += 0.38
footer(s)
notes(
    s,
    "API маленькое и предсказуемое: отдать страницу, здоровье, информацию о сессии, загрузить файл, получить список и удалить. "
    "Все чувствительные операции закрыты bearer-токеном на 256 бит; сессия живёт 30 минут и закрывается вручную.",
)

# ================================================================ 6. streaming
s = new_slide()
header(s, "Ядро системы", "Streaming upload: файл не попадает в RAM")
stages = [
    ("1", "multipart", "парсинг тела"),
    ("2", "валидация", "токен · лимиты"),
    ("3", "temp .part", "черновик на диске"),
    ("4", "stream", "+ SHA-256"),
    ("5", "finalize", "атомарно"),
    ("6", "MediaStore", "+ metadata"),
]
bw, gap, bh, by = 1.63, 0.34, 1.12, 1.98
bx = 0.95
for i, (num, title, sub) in enumerate(stages):
    accent = ACCENT2 if i >= 4 else ACCENT
    card(s, bx, by, bw, bh, fill=CARD if i < 4 else RGBColor(0x13, 0x2A, 0x28), line=BORDER)
    text(s, bx + 0.12, by + 0.12, 0.3, 0.24, num, size=9, color=accent, font=MONO, bold=True)
    text(s, bx + 0.12, by + 0.4, bw - 0.24, 0.28, title, size=12.5, bold=True, align=PP_ALIGN.CENTER)
    text(s, bx + 0.08, by + 0.75, bw - 0.16, 0.25, sub, size=8.5, color=MUTED, align=PP_ALIGN.CENTER)
    if i < len(stages) - 1:
        arrow(s, bx + bw + 0.04, by + 0.44, w=0.26, h=0.22)
    bx += bw + gap

bottom = [
    ("O(1) по памяти", "Буфер 64 КиБ: размер файла не влияет на heap", ACCENT),
    ("Лимиты и защита", "100 МиБ · 3 потока · sanitize · path traversal", ACCENT2),
    ("Атомарность", "Файл виден только после успешной финализации", AMBER),
]
for i, (title, body, color) in enumerate(bottom):
    x = MARGIN + i * (3.75 + 0.29)
    card(s, x, 3.85, 3.75, 1.5)
    rect(s, x, 3.85, 0.05, 1.5, fill=color, radius=None, shape=MSO_SHAPE.RECTANGLE)
    text(s, x + 0.26, 4.05, 3.2, 0.32, title, size=14.5, bold=True)
    text(s, x + 0.26, 4.46, 3.2, 0.7, body, size=11, color=MUTED, line_spacing=1.25)

card(s, MARGIN, 5.62, CONTENT_W, 0.75, fill=BG_SOFT)
text(
    s,
    1.0,
    5.79,
    11.4,
    0.42,
    "Обрыв сети → .part удаляется, приёмник возвращается в READY. Неполный файл никогда не считается готовым.",
    size=12.5,
    color=TEXT,
)
footer(s)
notes(
    s,
    "Ключевое техническое решение — потоковая запись. Файл не читается в память целиком: мы копируем блоками по 64 килобайта и параллельно считаем SHA-256. "
    "Сначала пишем во временный .part, и только после успешной проверки атомарно финализируем в MediaStore. Поэтому оборванная загрузка не оставляет мусора.",
)

# ================================================================ 7. wifi share
s = new_slide()
header(s, "Новый режим", "Wi-Fi Share: подключить телефон одним сканом")
text(
    s,
    MARGIN,
    1.7,
    6.6,
    0.55,
    "Если гость ещё не в сети — показываем два QR подряд.",
    size=14,
    color=TEXT,
)
steps = [
    ("1", "Подключение к сети", 'WIFI:S:<SSID>;T:WPA;P:<пароль>;;', ACCENT2),
    ("2", "Передача файла", "http://<IP>:8080/?token=<TOKEN>", ACCENT),
    ("3", "Разрешения", "CHANGE_WIFI_STATE · NEARBY_WIFI_DEVICES (13+)", AMBER),
]
y = 2.3
for num, title, code, color in steps:
    rect(s, MARGIN, y + 0.08, 0.34, 0.34, fill=color, radius=1.0, shape=MSO_SHAPE.OVAL)
    text(s, MARGIN, y + 0.11, 0.34, 0.28, num, size=11, color=BG, bold=True, align=PP_ALIGN.CENTER)
    text(s, MARGIN + 0.52, y + 0.06, 5.6, 0.28, title, size=13.5, bold=True)
    text(s, MARGIN + 0.52, y + 0.38, 6.1, 0.26, code, size=9.5, color=MUTED, font=MONO)
    y += 0.92

card(s, MARGIN, 5.12, 6.6, 1.35, fill=RGBColor(0x2A, 0x22, 0x14), line=RGBColor(0x55, 0x42, 0x24))
text(s, 1.0, 5.28, 0.5, 0.3, "◐", size=15, color=AMBER, bold=True)
text(s, 1.38, 5.27, 5.8, 0.3, "Статус: прототип", size=12.5, color=AMBER, bold=True)
text(
    s,
    1.38,
    5.58,
    5.75,
    0.8,
    "Генерация QR и чтение SSID реализованы. Активация hotspot и выдача пароля — следующий шаг.",
    size=11,
    color=RGBColor(0xE8, 0xD5, 0xB0),
    line_spacing=1.25,
)

for i, (path, label, color) in enumerate(
    [("qr-wifi.png", "Подключение к Wi-Fi", ACCENT2), ("qr-url.png", "Передача файла", ACCENT)]
):
    x = 7.75 + i * 2.62
    card(s, x, 1.72, 2.42, 3.5)
    rect(s, x + 0.16, 1.9, 2.1, 2.1, fill=WHITE, radius=0.08)
    picture(s, ASSETS / path, x + 0.27, 2.01, w=1.88)
    text(s, x + 0.1, 4.2, 2.22, 0.3, label, size=10.5, color=color, bold=True, align=PP_ALIGN.CENTER)
    text(s, x + 0.1, 4.52, 2.22, 0.3, "QR №%d" % (i + 1), size=9, color=DIM, align=PP_ALIGN.CENTER)

text(
    s,
    7.75,
    5.42,
    5.0,
    0.9,
    "Формат WIFI: понимают стандартные камеры Android и iOS — отдельное приложение не нужно.",
    size=11,
    color=MUTED,
    line_spacing=1.3,
)
footer(s)
notes(
    s,
    "Отдельный режим решает проблему «телефон гостя не в той сети». Показываем QR с параметрами Wi-Fi: камера сама предлагает подключиться, затем второй QR открывает отправку. "
    "Честно отмечу статус: генерация QR и чтение SSID уже есть, активация точки доступа — следующий шаг.",
)

# ================================================================ 8. testing
s = new_slide()
header(s, "Проверка", "37 тестов и физический прогон")
tf = textbox(s, MARGIN, 1.68, 2.6, 0.85)
para(tf, [("37", {"color": ACCENT, "size": 48, "bold": True, "font": MONO}),
          (" / 37", {"color": MUTED, "size": 22, "bold": True, "font": MONO})], first=True, line_spacing=1.0)
text(s, MARGIN, 2.6, 2.4, 0.3, "JVM-тестов зелёные", size=11.5, color=MUTED)

tf = textbox(s, MARGIN, 3.02, 5.55, 1.9)
bullet(tf, "токен, истечение, закрытие", size=12, bold_prefix="Session: ", space_after=5, first=True)
bullet(tf, "лимиты, имена, path traversal, SHA-256", size=12, bold_prefix="Upload: ", space_after=5)
bullet(tf, "финализация, metadata, удаление", size=12, bold_prefix="Storage: ", space_after=5)
bullet(tf, "роуты, авторизация, 401/413/415/429", size=12, bold_prefix="Server: ", space_after=5)

rect(s, MARGIN, 5.02, 5.5, 0.012, fill=BORDER, radius=None, shape=MSO_SHAPE.RECTANGLE)
text(s, MARGIN, 5.14, 5.4, 0.3, "Физический тест: PC → планшет", size=12.5, bold=True, color=ACCENT2)
tf = textbox(s, MARGIN, 5.5, 5.6, 1.2)
bullet(tf, "PDF загружен: 201, SHA-256 совпал, файл в списке", size=11, space_after=4, first=True)
bullet(tf, "hotspot и общая Wi-Fi: QR перегенерирован, сессия жива", size=11, space_after=4)
bullet(tf, "401 / 415 / 429 / 404 — ожидаемые отказы", size=11, space_after=4)

card(s, 6.7, 1.7, 5.88, 4.05, fill=BG_SOFT)
picture(s, ROOT / "docs" / "screenshots" / "tablet-qr-and-file-list.png", 6.86, 1.86, w=5.56)
rect(s, 6.86, 1.86, 5.56, 3.32, fill=None, line=BORDER, radius=0.02, line_w=1.0)
text(
    s,
    6.86,
    5.42,
    5.56,
    0.3,
    "Планшет: QR сессии и принятый файл в списке",
    size=9.5,
    color=MUTED,
    align=PP_ALIGN.CENTER,
)
footer(s)
notes(
    s,
    "Проверяли в двух плоскостях. Юнит- и интеграционные тесты на JVM — 37 из 37 — покрывают сессии, лимиты, хранилище и HTTP-коды. "
    "И отдельно физический прогон: с PC на планшет загрузили PDF, сверили SHA-256, проверили отказы 401, 415, 429 и защиту от path traversal.",
)

# ================================================================ 9. limitations
s = new_slide()
header(s, "Ограничения", "Что MVP сознательно не решает")
limits = [
    ("HTTP без TLS", "Токен и файл видны в локальном трафике → только доверенная сеть.", RED),
    ("Client isolation", "Гостевой Wi-Fi может блокировать связь → проверка доступности.", AMBER),
    ("Фоновая работа", "Сервер живёт при открытом UI → Foreground Service.", AMBER),
    ("Нет resumable", "Обрыв = повторная загрузка → Range-запросы, P2.", ACCENT),
    ("Зависимость от viewer", "Открытие файла — через ACTION_VIEW и установленное приложение.", ACCENT),
    ("Один приёмник", "Нет multi-room и автообнаружения → desktop, mDNS, несколько экранов.", MUTED),
]
for i, (title, body, color) in enumerate(limits):
    col, row = i % 3, i // 3
    x = MARGIN + col * (3.75 + 0.29)
    y = 1.78 + row * 1.92
    card(s, x, y, 3.75, 1.7)
    rect(s, x, y, 0.05, 1.7, fill=color, radius=None, shape=MSO_SHAPE.RECTANGLE)
    text(s, x + 0.26, y + 0.18, 3.2, 0.3, title, size=14, bold=True)
    text(s, x + 0.26, y + 0.58, 3.18, 0.95, body, size=10.5, color=MUTED, line_spacing=1.25)

card(s, MARGIN, 5.66, CONTENT_W, 0.72, fill=BG_SOFT)
text(
    s,
    1.0,
    5.82,
    11.3,
    0.4,
    "Честные границы проекта — часть инженерного результата: мы знаем, где решение работает, а где нет.",
    size=12.5,
    color=MUTED,
)
footer(s)
notes(
    s,
    "Границы обозначены явно. Главные: HTTP не шифрует трафик, гостевые сети могут изолировать клиентов, а сервер живёт только пока открыто приложение. "
    "Resumable uploads, TLS и Foreground Service вынесены за MVP — это следующий этап.",
)

# ================================================================ 10. results
s = new_slide()
header(s, "Итоги", "Что получилось")
results = [
    ("1", "Работающий MVP", "APK, 37 тестов, реальная передача PC → планшет, файлы в Documents/ScreenDrop.", ACCENT2),
    ("2", "Техническая база", "Встроенный Ktor, streaming I/O, атомарная запись, QR-сессии и лимиты.", ACCENT),
    ("3", "Понятный UX", "Скан → браузер → файл на экране. Без установки приложения на телефон.", AMBER),
]
y = 1.78
for num, title, body, color in results:
    rect(s, MARGIN, y + 0.04, 0.38, 0.38, fill=color, radius=1.0, shape=MSO_SHAPE.OVAL)
    text(s, MARGIN, y + 0.08, 0.38, 0.3, num, size=12, color=BG, bold=True, align=PP_ALIGN.CENTER)
    text(s, MARGIN + 0.58, y, 6.3, 0.3, title, size=15.5, bold=True)
    text(s, MARGIN + 0.58, y + 0.36, 6.1, 0.5, body, size=11.5, color=MUTED, line_spacing=1.25)
    y += 1.02

card(s, MARGIN, 4.92, 6.85, 1.0, fill=CARD)
text(s, 1.0, 5.08, 2.6, 0.28, "Дальше:", size=12, color=ACCENT, bold=True)
text(
    s,
    1.0,
    5.38,
    6.5,
    0.4,
    "Foreground Service · TLS · resumable uploads · hotspot-режим",
    size=10.5,
    color=TEXT,
)

card(s, 8.3, 1.72, 4.28, 4.2, fill=BG_SOFT)
rect(s, 9.02, 1.94, 2.84, 2.84, fill=WHITE, radius=0.07)
picture(s, ASSETS / "qr-repo.png", 9.17, 2.09, w=2.54)
text(s, 8.5, 4.92, 3.9, 0.3, "github.com/zt-a/screendrop", size=11, color=ACCENT, font=MONO, align=PP_ALIGN.CENTER)
text(s, 8.5, 5.24, 3.9, 0.3, "исходники и APK в репозитории", size=10, color=MUTED, align=PP_ALIGN.CENTER)

text(s, MARGIN, 6.28, 6.5, 0.55, "Спасибо! Вопросы?", size=26, bold=True, color=ACCENT2)
footer(s)
notes(
    s,
    "Итог: есть работающий MVP с физически подтверждённой передачей, 37 зелёными тестами и понятным сценарием. "
    "Технически проект закрывает полный путь файла: сеть, HTTP, потоковый I/O, хеширование и хранилище Android. "
    "Ссылка на репозиторий и APK — на экране. Готов ответить на вопросы.",
)

# ---------------------------------------------------------------- save
OUT.mkdir(parents=True, exist_ok=True)
pptx_path = OUT / "ScreenDrop-presentation.pptx"
prs.save(pptx_path)
print(f"saved: {pptx_path} ({pptx_path.stat().st_size / 1024:.0f} KiB, {len(prs.slides.__iter__.__self__._sldIdLst)} slides)")
