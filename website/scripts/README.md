# Website image assets

`website/assets/zio-http-logo.svg` is the **master** artwork: the request and
response arrows of the homepage design on a dark tile (the navbar logos use the bare arrows). Every icon below is
rasterised from it by `scripts/generate-icons.mjs`. Don't edit the derived files
by hand — change the generator and re-run it.

| Asset | Size | Used for |
| --- | --- | --- |
| `../assets/zio-http-logo.svg` | vector | master artwork, not served to the browser |
| `favicon.png` | 192x192 | `favicon` in `docusaurus.config.js` |
| `favicon.ico` | 16/32/48 | legacy browsers, bookmark bars |
| `apple-touch-icon.png` | 180x180 | iOS home screen |
| `zio-http-logo-tile.png` | 128x128 | navbar logo, light mode (deep mint arrows) |
| `zio-http-logo-mark.png` | 128x128 | navbar logo, dark mode (`srcDark`; mint arrows) |
| `zio-http-social-card.jpg` | 1200x630 | `og:image` / `twitter:image` |

## Regenerating the icons

```bash
node website/scripts/generate-icons.mjs
```

It needs a Chromium or Chrome binary (`CHROMIUM=/path/to/chromium` to override)
and no image library. The colours and every output size live in
`generate-icons.mjs`.

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
