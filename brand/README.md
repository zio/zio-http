# ZIO HTTP brand

The ZIO HTTP logo, icons, social cards, colour tokens and brand sheet. This is reference material: nothing here is
served by the website yet. The live site's colours are the `--zh-*` tokens in `website/src/css/custom.css`, and its
logo and favicon files in `website/static/img/` are generated from `website/assets/zio-http-logo.svg` by
`website/scripts/generate-icons.mjs`.

## Brand sheet

- `brand-sheet.html`: every board of the sheet on one page. Fonts are embedded, so it works offline.
- `sheet/`: the boards as exported from the Claude Design canvas ("ZIO HTTP Brand Sheet"), one `.dc.html` per board
  plus `canvas.json`, and a PNG of each board for viewing on GitHub. The boards follow the ZIO family format:
  0 Overview, 1 Logo, 2 Icon and favicon, 3 Colour, 4 Typography, 5 Voice and tone, 6 Visual and imagery style,
  7 Hero banner, 8 Social cards, 9 In the browser, 10 Assets and tokens.

## Mark

- Two arrows on a 32-unit grid: a request goes out along the top, a response comes back along the bottom. The top
  bar runs from x 5 to 25 at y 11.5, the bottom bar from x 27 to 7 at y 21.5; arrowheads are 4.5 units at 45°; stroke
  3 with round caps and joins.
- One colour in every version: mint `#3DDC97` on dark, deep mint `#0B7A50` on light. The bare mark is the primary
  symbol; the navy tile is for square spaces only (app icon, avatar, favicon).
- Clear space: half the mark's height on every side. Minimum size: 16 px as a favicon, 24 px in interfaces.

## Wordmark

- The arrows are the Z of "ZIO", as in every ZIO library logo: never set the mark beside a full "ZIO HTTP".
- "IO HTTP" is Inter Tight Bold at −3 % tracking. The arrows, cropped to their outline, stand on the baseline and reach
  the cap height (0.7275 em); their stroke is 0.127 em, the weight of the T's crossbar; 0.055 em separates them from
  the I.
- Ink `#0B1020` text with deep mint arrows on light, `#E8EAF0` text with mint arrows on dark, or one colour for both.
  Horizontal is the default; stacked puts "ZIO" over "HTTP".
- Clear space around the wordmark: half the height of the Z (0.364 em). Below 15 px type, use the mark alone.

## Files

| File | Use |
| --- | --- |
| `logo/zio-http-logo.svg`, `-on-dark.svg` | Horizontal wordmark on light and on dark, with clear space |
| `logo/zio-http-logo-bare.svg` | Horizontal wordmark on light, cropped tight |
| `logo/zio-http-logo-stacked.svg`, `-stacked-on-dark.svg` | Stacked wordmark ("ZIO" over "HTTP") for square spaces |
| `logo/zio-http-logo-mono-black.svg`, `-mono-white.svg` | One-colour wordmark |
| `logo/zio-http-mark.svg` | The mark, deep mint |
| `logo/zio-http-mark-favicon.svg` | The mark on a dark rounded tile |
| `logo/zio-http-mark-knockout.svg` | Mint tile with the mark cut out |
| `logo/readme-banner-dark.png`, `-light.png` | README banner, 1200 × 300 at 2× |
| `logo/zio-http-social-github.png`, `-light.png` | GitHub social preview, 1280 × 640 |
| `logo/zio-http-social-og.png` | Open Graph card, 1200 × 630 |
| `icons/favicon.svg` | Mark in mint, for dark tabs |
| `icons/favicon-light.svg` | Mark in deep mint, for light tabs (`favicon-light-bg.svg` is the same file, kept for existing references) |
| `icons/favicon-adaptive.svg` | Deep mint, switching to mint when the browser prefers a dark scheme |
| `icons/favicon-mono.svg` | Mark in `currentColor` |
| `icons/icon-tile.svg` | Navy rounded tile, mint mark: app icon, avatar and the recommended favicon (10.7:1 on any tab) |
| `icons/icon-maskable-512.svg` | PWA maskable icon, mark at 60 % so platform masks never clip an arrowhead |
| `theme-tokens.css` | Every colour for dark (default) and light, and the three font stacks |

All SVG text is converted to outlines, so no font is needed to open them, and the files carry no editor metadata.

## Colour and type

- Accent: mint `#3DDC97` on dark, deep mint `#0B7A50` on light (5.4:1 on white). Dark mode is the default. Every
  colour is a token in `theme-tokens.css`; board 3 of the sheet lists the contrast of each text pair.
- Fonts: Inter Tight (wordmark, headings, brand material), JetBrains Mono (code and labels, ligatures off), Inter
  (site content below the hero). All three are free and open source (OFL) and load from Google Fonts.

## Rebuilding

`tools/build_brand.py` writes `icons/`, `logo/`, `sheet/` and `brand-sheet.html` from the canvas export. It needs
Chromium and the variable fonts `InterTight[wght].ttf`, `JetBrainsMono[wght].ttf` and `Inter[opsz,wght].ttf` from
[google/fonts](https://github.com/google/fonts) (not committed). From the repository root:

```sh
uv run --with fonttools --with uharfbuzz --with brotli --with pillow \
  python brand/tools/build_brand.py <fonts dir> <canvas export dir>
```

## Don't

Set the mark beside a full "ZIO HTTP"; flip the arrows' directions; square off their ends; stretch, rotate or recolour
one arrow; use bright mint on a light ground; place the mark on a busy image.
