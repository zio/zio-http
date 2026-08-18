# Website image assets

`website/assets/zio-http-logo.png` is the **master** artwork: the
full-resolution 2048x2048 source, committed so the derived assets can always be
regenerated. It sits outside `static/` so its 2.5MB is not copied into every
site build.
Every icon below is a downscaled crop of it. Don't edit the derived files by
hand — change the generator and re-run it.

| Asset | Size | Used for |
| --- | --- | --- |
| `../assets/zio-http-logo.png` | 2048x2048 | master artwork, not served to the browser |
| `favicon.png` | 192x192 | `favicon` in `docusaurus.config.js` |
| `favicon.ico` | 16/32/48 | legacy browsers, bookmark bars |
| `apple-touch-icon.png` | 180x180 | iOS home screen |
| `zio-http-logo-tile.png` | 128x128 | navbar logo, light mode |
| `zio-http-logo-mark.png` | 128x128 | navbar logo, dark mode (`srcDark`) |
| `zio-http-social-card.jpg` | 1200x630 | `og:image` / `twitter:image` |

## Regenerating the icons

```bash
pip install pillow
python3 website/scripts/generate-icons.py
```

The crop box and every output size live in `generate-icons.py`.

## Regenerating the social card

The card carries text, so it is laid out by a browser instead of an image
library — that way the title is set in Inter, the site's own typeface, loaded
from `node_modules`. Run `yarn install` in `website/` first, then from the
repository root:

```bash
chromium --headless --allow-file-access-from-files \
  --window-size=1200,630 --screenshot=card.png \
  website/scripts/social-card.html
```

Save the screenshot as JPEG at roughly quality 92 over
`static/img/zio-http-social-card.jpg`. JPEG rather than PNG: the gradient and
glow land around 67KB that way versus 448KB as an optimised PNG, with no visible
artifacts around the text.
