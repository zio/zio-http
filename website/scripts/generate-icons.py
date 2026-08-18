#!/usr/bin/env python3
"""Derive the website's icon set from the full-resolution logo artwork.

`website/assets/zio-http-logo.png` is the master: the 2048x2048 source artwork,
committed so every derived asset can be regenerated without hunting down the
original file. It lives outside `static/` so Docusaurus does not ship its 2.5MB
with every deploy. Everything this script writes is a downscaled copy of
it — never edit the derived files by hand, change the constants here and re-run.

Requires Pillow:

    pip install pillow
    python3 website/scripts/generate-icons.py

The social card is not generated here because it needs a browser to lay out
text; see website/scripts/README.md.
"""

from pathlib import Path

from PIL import Image, ImageDraw

WEBSITE = Path(__file__).resolve().parent.parent
IMG = WEBSITE / "static" / "img"
MASTER = WEBSITE / "assets" / "zio-http-logo.png"

# The mark occupies 1670x1252 of the master's 2048px frame. Squaring it by its
# full width leaves ~25% dead space and the "Z" becomes unrecognisable in a
# browser tab, so every icon is cropped to this square centred on the mark. It
# clips the thin outer strand tails but keeps the letterform filling the frame.
CENTER_X, CENTER_Y, CROP = 1022, 1010, 1320

# Navbar logo and favicon are rendered at 32px; 128px covers high-DPI displays.
LOGO_PX = 128
FAVICON_PX = 192
APPLE_TOUCH_PX = 180
ICO_SIZES = [(16, 16), (32, 32), (48, 48)]

TILE_RADIUS = 0.22  # fraction of tile width
TILE_INSET = 0.86  # mark size as a fraction of the tile
TILE_BG = (15, 23, 20, 255)


def square_mark(master: Image.Image) -> Image.Image:
    half = CROP // 2
    box = (CENTER_X - half, CENTER_Y - half, CENTER_X + half, CENTER_Y + half)
    return master.crop(box)


def dark_tile(mark: Image.Image) -> Image.Image:
    """The mark on a rounded dark tile.

    The artwork contains near-white strands that vanish against the white
    light-mode navbar, so light mode needs a dark backdrop to read against.
    """
    side = mark.width
    mask = Image.new("L", (side, side), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, side - 1, side - 1], radius=int(side * TILE_RADIUS), fill=255
    )

    tile = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    tile.paste(Image.new("RGBA", (side, side), TILE_BG), (0, 0), mask)

    inner = mark.resize((int(side * TILE_INSET),) * 2, Image.LANCZOS)
    offset = (side - inner.width) // 2
    tile.alpha_composite(inner, (offset, offset))
    return tile


def main() -> None:
    master = Image.open(MASTER).convert("RGBA")
    mark = square_mark(master)

    written = []

    def save(image: Image.Image, name: str, size: int, **kwargs) -> None:
        image.resize((size, size), Image.LANCZOS).save(IMG / name, **kwargs)
        written.append(name)

    # Navbar: bare mark for the dark theme, dark tile for the light theme.
    save(mark, "zio-http-logo-mark.png", LOGO_PX, optimize=True)
    save(dark_tile(mark), "zio-http-logo-tile.png", LOGO_PX, optimize=True)

    save(mark, "favicon.png", FAVICON_PX, optimize=True)
    save(mark, "favicon.ico", 48, sizes=ICO_SIZES)

    # iOS discards alpha, so flatten over black rather than letting it pick.
    opaque = Image.new("RGBA", mark.size, (0, 0, 0, 255))
    opaque.alpha_composite(mark)
    save(opaque.convert("RGB"), "apple-touch-icon.png", APPLE_TOUCH_PX, optimize=True)

    for name in written:
        print(f"{name}: {(IMG / name).stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
