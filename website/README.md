# Website

This website is built using [Docusaurus 2](https://docusaurus.io/), a modern static website generator.

### Installation

```
$ yarn
```

### Local Development

```
$ yarn start
```

This command starts a local development server and opens up a browser window. Most changes are reflected live without having to restart the server.

### Build

```
$ yarn build
```

This command generates static content into the `build` directory and can be served using any static contents hosting service.

### Tests

```
$ yarn test
```

Runs the unit tests in `test/` with Node's test runner.

### API reference at `/api`

`sbt apiDocs` (an alias for `++3.x docs/generateApiDocs`, see `../aliases.sbt`) builds one unified Scaladoc
(sbt-unidoc, Scala 3) for every published library module and copies it into `static/api` (gitignored), so `yarn build`
publishes it at `/api/` next to the docs. zio-http contributes its JVM side, since JVM and JS share the public API. The
module list is `ScalaUnidoc / unidoc / unidocProjectFilter` on the `docs` project in `../build.sbt`; a new published
module must be added there. Source links point at the commit the docs were built from. `../build.sh` (Netlify and the
**Build and Deploy Website** workflow) runs `sbt mdoc apiDocs` before `yarn build`.

The task also fixes the generated output (all in `generateApiDocs`):

- Links to other zio-http types stay on `/api`. sbt-api-mappings, which comes with zio-sbt-website, would otherwise
  send them to javadoc.io, which has no page for a snapshot.
- Scaladoc's `-dynamic-side-menu` (without it, every page repeats the whole package tree) comes with bugs in
  `scripts/ux.js` that break navigation, patched in place: menu links joined with commas under a sub-path
  (`,api/zio/http.html`), link clicks that depend on jQuery from a CDN (`$.get`), Back/Forward leaving the menu
  highlight on the page you left, and a URL naming a section (`#attributes`) aborting the page setup, which skipped
  code highlighting. Each patch fails the task when its target text disappears, so a Scaladoc upgrade gets them
  reviewed.

Scaladoc is a separate generator with its own HTML, stylesheets and scripts, so after `docusaurus build`
`scripts/theme-api.mjs` gives every page in `build/api` the site's look (`scripts/lib/api-theme.mjs`, assets in
`src/api-theme/`). It reads each page from `static/api` and writes it over its copy in `build/api`, so it can be rerun:

- Scaladoc's header is replaced by a copy of the site navbar (logo and title, the same links on the same sides, the
  onboard-agent button and colour-mode toggle, collapsing into the mobile menu at 996px), keeping the ids Scaladoc's
  scripts look up (`#search-toggle`, `#mobile-menu-toggle`, a hidden version dropdown). Scaladoc's search button takes
  the place of the Algolia search. A unit test keeps the links, logo and title in step with `docusaurus.config.js`.
- URLs have no `.html`, like the docs pages: `/api/zio/http/Body`, not `Body.html` (an index page is its folder). The
  files keep their names, since Netlify and `docusaurus serve` serve `Body.html` for `/…/Body`; the relative links in
  every page, the search index (`scripts/searchData.js`) and the side menu's link builder in `scripts/ux.js` drop the
  extension. Old `.html` links still work.
- Nested types get folders: Scaladoc files `Header.Accept` as `Header$$Accept.html`, the site serves it at
  `/api/zio/http/Header/Accept` (`nestedTypePaths` reads each nested type's parent off the side menu; `relocatePage`
  rewrites a moved page's links, root path and location for its new folder, and every link to it). Objects keep
  Scaladoc's `$` (`Body$` is the companion of `Body`).
- The site favicon (`favicon` in `docusaurus.config.js`) replaces Scaladoc's `favicon.ico`, and the site fonts (Inter
  and JetBrains Mono, copied from `@fontsource-variable` into `build/api/site-fonts`) replace Scaladoc's.
- The window scrolls, as on a docs page, so its scrollbar runs the whole height beside a fixed navbar. `api.css` puts
  `#main` back in the page flow and fixes the navbar, sidebar and overlays; `theme.js` sends Scaladoc's `#main.scrollTo`
  calls to the window.
- The index page sends `/api` to `/api/` first thing in `<head>` (with `<base href="/api/">` for the requests the
  browser starts before that runs): without the slash, Scaladoc's relative paths resolve against the site root and the
  page comes up unstyled. A Netlify redirect rule cannot do it, since a rule is skipped when a file answers the path.
- Scaladoc's "Generated with Scaladoc" footers are removed, and the sidebar runs to the bottom of the window.
- The breadcrumbs become the docs breadcrumbs (home icon, API, the packages as the side menu names them, the page) at
  the top of the text column.
- The side menu starts at `zio.http` (`checkSideMenu` fails the build if another top-level package appears), which
  `theme.js` keeps unfolded across page loads unless the reader folds it. It is drawn as the docs sidebar: Infima's
  rows, colours and chevron caret, 12px per level, Scaladoc's kind icons, the browser's own scrollbars, no Docs/API
  switcher.
- The columns follow the docs page grid: a 300px sidebar shown down to 997px, a container of at most 1320px (1450px
  from 1200px, as `src/css/custom.css` widens it), a 75%/25% text/contents split with 1rem gutters, and the contents
  hidden below 997px.
- `src/api-theme/theme.js` replaces Scaladoc's `scripts/theme.js`: the colour mode is the one Docusaurus keeps in
  `localStorage.theme` (light when nothing is stored, as `colorMode` is left at its defaults), so switching it on
  either side carries over. It also keeps the navbar's site links out of Scaladoc's in-place page loader.

`yarn start` serves `static/api` as generated, without this skin.

The navbar and footer link to it with `pathname:///api/`: Scaladoc is plain HTML outside the Docusaurus router, and
the `pathname://` protocol makes the link a full page load and keeps it out of the broken-link checker. Without a prior
`sbt apiDocs` the build still succeeds, but `/api/` is a 404.

### Deployment

Using SSH:

```
$ USE_SSH=true yarn deploy
```

Not using SSH:

```
$ GIT_USER=<Your GitHub username> yarn deploy
```

If you are using GitHub pages for hosting, this command is a convenient way to build the website and push to the `gh-pages` branch.
