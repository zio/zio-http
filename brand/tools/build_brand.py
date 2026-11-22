"""Build the ZIO HTTP brand kit from the brand-sheet boards.

Usage, from the repository root (fontTools, uharfbuzz, brotli and Pillow are the Python dependencies; Chromium renders
the PNGs):

    uv run --with fonttools --with uharfbuzz --with brotli --with pillow python brand/tools/build_brand.py <fonts dir> <boards dir>

<fonts dir> holds the variable fonts from github.com/google/fonts (OFL, not committed):
    InterTight[wght].ttf, JetBrainsMono[wght].ttf and Inter[opsz,wght].ttf (any file names starting with
    "InterTight", "JetBrainsMono" and "Inter[" or "Inter-").
<boards dir> holds the Claude Design canvas export: canvas.json and its *.dc.html boards.

Writes into brand/:
    icons/*.svg                 favicons and app icons
    logo/*.svg                  the mark and the wordmark lockups, text converted to outlines
    logo/*.png                  README banners and social cards, cut out of boards 7 and 8 at full size
    sheet/                      the boards (editable sources), canvas.json and a PNG of each board
    brand-sheet.html            every board on one page, fonts embedded, works offline
"""
import base64
import glob
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

import uharfbuzz as hb
from fontTools import subset
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from PIL import Image

FONTS, BOARDS = (os.path.abspath(p) for p in sys.argv[1:3])
OUT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
CHROME = shutil.which("chromium") or shutil.which("google-chrome-stable") or shutil.which("chromium-browser")
TMP = tempfile.mkdtemp(prefix="zio-http-brand-")


def font_file(prefix_re):
    hits = [f for f in os.listdir(FONTS) if re.match(prefix_re, f) and f.endswith(".ttf")]
    if len(hits) != 1:
        sys.exit(f"expected one font matching {prefix_re} in {FONTS}, found {hits}")
    return os.path.join(FONTS, hits[0])


SANS = font_file(r"InterTight")
MONO = font_file(r"JetBrainsMono")
SITE = font_file(r"Inter[\[-]")

# ---------------------------------------------------------------- palette (board 3, theme-tokens.css)
INK, LIGHT_TEXT = "#0B1020", "#E8EAF0"
MINT, DEEP_MINT = "#3DDC97", "#0B7A50"
PAGE_DARK, SURFACE_DARK = "#06080F", "#0B1020"

# ---------------------------------------------------------------- the mark (32-unit grid, board 1.2)
# Request out along the top, response back along the bottom; bars 10 units apart, heads 4.5 units at 45 degrees.
MARK_PATHS = ('<path d="M5 11.5H25"/><path d="M20.5 7L25 11.5L20.5 16"/>'
              '<path d="M27 21.5H7"/><path d="M11.5 17L7 21.5L11.5 26"/>')


def mark_g(stroke, sw=3, extra=""):
    paint = f' stroke="{stroke}"' if stroke else ""
    return (f'<g{extra} fill="none"{paint} stroke-width="{sw}" stroke-linecap="round" '
            f'stroke-linejoin="round">{MARK_PATHS}</g>')


def svg(w, h, vb, body, label=None):
    a11y = f' role="img" aria-label="{label}"' if label else ""
    title = f"<title>{label}</title>" if label else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="{vb}"{a11y}>{title}{body}</svg>\n'


def write(rel, content):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(content)


# ---------------------------------------------------------------- icons
write("icons/favicon.svg", svg(32, 32, "0 0 32 32", mark_g(MINT)))
write("icons/favicon-light.svg", svg(32, 32, "0 0 32 32", mark_g(DEEP_MINT)))
write("icons/favicon-light-bg.svg", svg(32, 32, "0 0 32 32", mark_g(DEEP_MINT)))
write("icons/favicon-adaptive.svg", svg(32, 32, "0 0 32 32",
      f"<style>.m{{stroke:{DEEP_MINT}}}@media (prefers-color-scheme: dark){{.m{{stroke:{MINT}}}}}</style>"
      + mark_g(None, extra=' class="m"')))
write("icons/favicon-mono.svg", svg(32, 32, "0 0 32 32", mark_g("currentColor")))
write("icons/icon-tile.svg", svg(32, 32, "0 0 32 32", f'<rect width="32" height="32" rx="7" fill="{INK}"/>' + mark_g(MINT)))
write("icons/icon-maskable-512.svg", svg(512, 512, "0 0 512 512", f'<rect width="512" height="512" fill="{INK}"/>'
      + mark_g(MINT, extra=' transform="translate(102.4 102.4) scale(9.6)"')))

write("logo/zio-http-mark.svg", svg(32, 32, "0 0 32 32", mark_g(DEEP_MINT), "ZIO HTTP mark"))
write("logo/zio-http-mark-favicon.svg", svg(32, 32, "0 0 32 32", f'<rect width="32" height="32" rx="7" fill="{PAGE_DARK}"/>'
      + mark_g(MINT, 3.4, ' transform="translate(3.2 3.2) scale(0.8)"'), "ZIO HTTP"))
write("logo/zio-http-mark-knockout.svg", svg(32, 32, "0 0 32 32",
      '<defs><mask id="k"><rect width="32" height="32" fill="#fff"/>'
      + mark_g("#000", 3.4, ' transform="translate(3.2 3.2) scale(0.8)"')
      + f'</mask></defs><rect width="32" height="32" rx="7" fill="{MINT}" mask="url(#k)"/>', "ZIO HTTP mark"))


# ---------------------------------------------------------------- the wordmark (board 1.3 and 4.2), outlined
class Face:
    def __init__(self, path, wght):
        self.tt = instantiateVariableFont(TTFont(path), {"wght": wght})
        self.hb = hb.Font(hb.Face(hb.Blob.from_file_path(path)))
        self.hb.set_variations({"wght": wght})
        self.upm = self.tt["head"].unitsPerEm
        self.gs = self.tt.getGlyphSet()
        self.order = self.tt.getGlyphOrder()
        bp = BoundsPen(self.gs)
        self.gs[self.tt.getBestCmap()[ord("H")]].draw(bp)
        self.cap = bp.bounds[3] / self.upm

    def outline(self, text, size, x, y, tracking_em):
        """Text set at (x, baseline y) with CSS letter-spacing. Returns (path data, (xmin, ymin, xmax, ymax))."""
        buf = hb.Buffer()
        buf.add_str(text)
        buf.guess_segment_properties()
        hb.shape(self.hb, buf, {"kern": True, "liga": False, "calt": False})
        sc = size / self.upm
        pen, bounds = SVGPathPen(self.gs), BoundsPen(self.gs)
        cx = x
        for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
            glyph = self.gs[self.order[info.codepoint]]
            t = (sc, 0, 0, -sc, cx + pos.x_offset * sc, y - pos.y_offset * sc)
            glyph.draw(TransformPen(pen, t))
            glyph.draw(TransformPen(bounds, t))
            cx += pos.x_advance * sc + tracking_em * size
        return pen.getCommands(), bounds.bounds


BOLD = Face(SANS, 700)
EM = 100.0
CAP = BOLD.cap                      # Inter Tight Bold cap height, 0.7275 em
STROKE_EM, GAP_EM, TRACK_EM = 0.127, 0.055, -0.03   # stroke = the T's crossbar
# The arrows, cropped to their outline, stand on the baseline and reach the cap height.
SU = STROKE_EM * 19 / (CAP - STROKE_EM)              # stroke in mark units: the outline is 19 + SU units tall
VB_X, VB_Y, VB_W, VB_H = 5 - SU / 2, 7 - SU / 2, 22 + SU, 19 + SU
K = CAP * EM / VB_H
ARROWS_W = VB_W * K


def arrows(x, base, color):
    return (f'<g transform="translate({x:.3f} {base - CAP * EM:.3f}) scale({K:.5f}) translate({-VB_X:.4f} {-VB_Y:.4f})">'
            + mark_g(color, round(SU, 4)) + "</g>")


def lockup(text_color, arrow_color, stacked=False, pad_em=0.0, height=64):
    parts = [arrows(0, 0, arrow_color)]
    x = ARROWS_W + GAP_EM * EM
    if stacked:
        d1, b1 = BOLD.outline("IO", EM, x, 0, TRACK_EM)
        d2, b2 = BOLD.outline("HTTP", EM, 0, EM, TRACK_EM)
        parts += [f'<path d="{d1}" fill="{text_color}"/>', f'<path d="{d2}" fill="{text_color}"/>']
        boxes = [b1, b2]
    else:
        d, b = BOLD.outline("IO HTTP", EM, x, 0, TRACK_EM)
        parts.append(f'<path d="{d}" fill="{text_color}"/>')
        boxes = [b]
    # Font units are y-up; the outlines were flipped, so bounds are already in SVG (y-down) space.
    xmin = min([0.0] + [b[0] for b in boxes])
    xmax = max([ARROWS_W] + [b[2] for b in boxes])
    ymin = min([-CAP * EM] + [b[1] for b in boxes])
    ymax = max([0.0] + [b[3] for b in boxes])
    pad = pad_em * EM
    vx, vy, vw, vh = xmin - pad, ymin - pad, xmax - xmin + 2 * pad, ymax - ymin + 2 * pad
    w = round(height * vw / vh, 1)
    return svg(w, height, f"{vx:.2f} {vy:.2f} {vw:.2f} {vh:.2f}", "".join(parts), "ZIO HTTP")


X_EM = CAP / 2                      # clear space around the wordmark: half the height of the Z
write("logo/zio-http-logo.svg", lockup(INK, DEEP_MINT, pad_em=X_EM))
write("logo/zio-http-logo-bare.svg", lockup(INK, DEEP_MINT, height=34))
write("logo/zio-http-logo-on-dark.svg", lockup(LIGHT_TEXT, MINT, pad_em=X_EM))
write("logo/zio-http-logo-stacked.svg", lockup(INK, DEEP_MINT, stacked=True, pad_em=X_EM, height=128))
write("logo/zio-http-logo-stacked-on-dark.svg", lockup(LIGHT_TEXT, MINT, stacked=True, pad_em=X_EM, height=128))
write("logo/zio-http-logo-mono-black.svg", lockup("#000000", "#000000", pad_em=X_EM))
write("logo/zio-http-logo-mono-white.svg", lockup("#FFFFFF", "#FFFFFF", pad_em=X_EM))


# ---------------------------------------------------------------- rendering
FONTFACE = (f"@font-face{{font-family:'Inter Tight';src:url('file://{SANS}') format('truetype');font-weight:100 900}}"
            f"@font-face{{font-family:'JetBrains Mono';src:url('file://{MONO}') format('truetype');font-weight:100 800}}"
            f"@font-face{{font-family:'Inter';src:url('file://{SITE}') format('truetype');font-weight:100 900}}"
            '[style*="JetBrains Mono"]{font-variant-ligatures:none;font-feature-settings:"calt" 0,"liga" 0}')


def shoot(html, w, h, out, scale=1):
    """Screenshot the page's top-left w x h CSS px. Headless Chrome counts its own frame in --window-size, so the
    window is made taller than needed and the screenshot cropped to the exact size."""
    p = os.path.join(TMP, "p.html")
    with open(p, "w") as f:
        f.write(html)
    subprocess.run([CHROME, "--headless=new", "--no-sandbox", "--disable-gpu", "--hide-scrollbars",
                    "--allow-file-access-from-files", f"--window-size={w},{h + 400}", f"--force-device-scale-factor={scale}",
                    f"--screenshot={out}", "--virtual-time-budget=6000", "file://" + p],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    img = Image.open(out)
    if img.size[1] < h * scale:
        sys.exit(f"screenshot of {out} is {img.size}, shorter than {w}x{h} at {scale}x")
    img.crop((0, 0, round(w * scale), round(h * scale))).save(out)


def read_board(name):
    with open(os.path.join(BOARDS, name)) as f:
        return f.read()


def defs_of(s):
    return re.search(r'<svg width="0" height="0".*?</defs></svg>', s, re.S).group(0)


def element(s, start):
    depth = 0
    for t in re.finditer(r"<div\b|</div>", s[start:]):
        depth += 1 if t.group(0) == "<div" else -1
        if depth == 0:
            return s[start:start + t.end()]
    raise ValueError("unbalanced div")


def cutouts(s, w, h):
    """Full-size artboards on a board: <div style="width: Wpx; height: Hpx; ... background: ..."> in document order."""
    return [element(s, m.start()) for m in re.finditer(rf'<div style="width: {w}px; height: {h}px;[^"]*background:', s)]


def flat(el):
    """Drop the board's preview frame (rounded corners, border) so the export is a plain rectangle."""
    head, rest = el.split(">", 1)
    head = re.sub(r"border-radius: \d+px; ?", "", head)
    head = re.sub(r"border: 1px solid #[0-9A-Fa-f]{6}; ?", "", head)
    return head + ">" + rest


def page(defs, body):
    return (f'<!doctype html><html><head><meta charset="utf-8"><style>{FONTFACE}body{{margin:0;'
            f"font-family:'Inter Tight',sans-serif}}</style></head><body>{defs}{body}</body></html>")


def is_dark(el):
    """True when the element's own background colour is dark (relative luminance under 0.2)."""
    bg = re.search(r"background: #([0-9A-Fa-f]{6})", el.split(">", 1)[0]).group(1)
    r, g, b = (int(bg[i:i + 2], 16) / 255 for i in (0, 2, 4))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b < 0.2


hero = read_board("Brand7Hero.dc.html")
banners = cutouts(hero, 1200, 300)
for name, el in [("logo/readme-banner-dark.png", next(e for e in banners if is_dark(e))),
                 ("logo/readme-banner-light.png", next(e for e in banners if not is_dark(e)))]:
    shoot(page(defs_of(hero), flat(el)), 1200, 300, os.path.join(OUT, name), scale=2)

social = read_board("Brand8Social.dc.html")
gh, og = cutouts(social, 1280, 640), cutouts(social, 1200, 630)
for name, el, w, h in [("logo/zio-http-social-github.png", next(e for e in gh if is_dark(e)), 1280, 640),
                       ("logo/zio-http-social-github-light.png", next(e for e in gh if not is_dark(e)), 1280, 640),
                       ("logo/zio-http-social-og.png", og[0], 1200, 630)]:
    shoot(page(defs_of(social), flat(el)), w, h, os.path.join(OUT, name))


# ---------------------------------------------------------------- sheet/: the boards, as exported, plus a PNG of each
with open(os.path.join(BOARDS, "canvas.json")) as f:
    canvas = json.load(f)
order = canvas["order"]
sheet = os.path.join(OUT, "sheet")
shutil.rmtree(sheet, ignore_errors=True)
os.makedirs(sheet)
shutil.copy(os.path.join(BOARDS, "canvas.json"), os.path.join(sheet, "canvas.json"))


def board_parts(s, prefix):
    helmet = re.search(r"<helmet>(.*?)</helmet>", s, re.S).group(1)
    body = re.search(r"<x-dc>(.*?)</x-dc>", s, re.S).group(1)
    body = re.sub(r"<helmet>.*?</helmet>", "", body, flags=re.S)
    fonts = set(re.findall(r"family=([A-Za-z+]+)", helmet))
    unknown = fonts - {"Inter+Tight", "JetBrains+Mono", "Inter"}
    if unknown:
        sys.exit(f"board uses fonts the sheet does not embed: {unknown}")
    # Give every id but the shared mark symbols a per-board prefix, so the boards can share one page.
    for i in set(re.findall(r'\bid="([^"]+)"', body)) - {"zm", "zw"}:
        body = re.sub(rf'\bid="{re.escape(i)}"', f'id="{prefix}-{i}"', body)
        body = body.replace(f'href="#{i}"', f'href="#{prefix}-{i}"').replace(f"url(#{i})", f"url(#{prefix}-{i})")
    w, h = (int(x) for x in re.search(r'"\$preview":\{"width":(\d+),"height":(\d+)\}', s).groups())
    return body, w, h


texts, sections = [], []
for n, name in enumerate(order):
    s = read_board(name)
    shutil.copy(os.path.join(BOARDS, name), os.path.join(sheet, name))
    body, w, h = board_parts(s, f"b{n}")
    png = os.path.join(sheet, name.replace(".dc.html", ".png"))
    shoot(page("", body), w, h, png)
    # 256 colours, no dithering: the boards are flat graphics, and this keeps each PNG around 200 KB.
    Image.open(png).convert("RGB").quantize(256, Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(png, optimize=True)
    title = canvas["boards"][name].get("title", name)
    sections.append(f'<section aria-label="{title}" style="width:{w}px;height:{h}px;margin:0 auto 48px;'
                    f'box-shadow:0 1px 3px rgba(16,24,40,.12)">{body}</section>')
    texts.append(re.sub(r"<[^>]+>", " ", body))


# ---------------------------------------------------------------- brand-sheet.html: every board, subset fonts embedded
def woff2(path, text, features=("*",)):
    opts = subset.Options()
    opts.flavor = "woff2"
    opts.layout_features = list(features)
    opts.name_IDs = ["*"]
    font = TTFont(path)
    sub = subset.Subsetter(opts)
    sub.populate(text=text + "".join(chr(c) for c in range(0x20, 0x7F)))
    sub.subset(font)
    buf = io.BytesIO()
    font.flavor = "woff2"
    font.save(buf)
    return base64.b64encode(buf.getvalue()).decode()


import html as _html  # noqa: E402

all_text = _html.unescape(" ".join(texts))
faces = "".join(
    f"@font-face{{font-family:'{fam}';src:url(data:font/woff2;base64,{woff2(path, all_text, feats)}) format('woff2');"
    f"font-weight:{wr};font-display:block}}"
    for fam, path, wr, feats in [("Inter Tight", SANS, "100 900", ("*",)),
                                 # Code ligatures stay out of the embedded mono font: -> must read as two characters.
                                 ("JetBrains Mono", MONO, "100 800", ("kern", "mark", "mkmk", "ccmp", "locl")),
                                 ("Inter", SITE, "100 900", ("*",))])
symbols = defs_of(read_board(order[0]))
write("brand-sheet.html",
      '<!doctype html>\n<html lang="en"><head><meta charset="utf-8">'
      '<meta name="viewport" content="width=device-width, initial-scale=1"><title>ZIO HTTP brand sheet</title>'
      f'<style>{faces}body{{margin:0;padding:48px 24px;background:#E9ECF1;color:#0B1020;'
      "font-family:'Inter Tight',sans-serif}}"
      '[style*="JetBrains Mono"]{font-variant-ligatures:none;font-feature-settings:"calt" 0,"liga" 0}</style></head><body>'
      + symbols + "".join(sections) + "</body></html>\n")

shutil.rmtree(TMP, ignore_errors=True)
print("done:", OUT)
