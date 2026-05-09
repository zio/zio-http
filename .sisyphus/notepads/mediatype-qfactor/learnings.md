## Learnings

### MediaType: Two Different Types
- `zio.http.MediaType` — local, used throughout codec files. Has `mainType`, `subType`, `fullType`, `matches()`, `compressible`, `binary`, etc.
- `zio.blocks.mediatype.MediaType` — blocks version. Has `fullType`, `mainType`, `subType`.
- `Accept.MediaRange.mediaType` returns the BLOCKS `MediaType`, NOT the local one.
- To convert: `zio.http.MediaType.forContentType(blocksMediaType.fullType)` or `zio.http.MediaType(blocksMediaType.mainType, blocksMediaType.subType)`.

### Headers API (blocks)
- `Headers.apply(pairs: (String, String)*)` — ONLY takes string tuples, NOT typed headers.
- To add typed headers to Headers: use `h.headerName -> h.renderedValue` pattern.
- `Headers.empty` exists. `Headers.++` exists for merging.

### CORS Headers (blocks)
- `AccessControlAllowOrigin.Specific(origin: String)` — takes String, NOT Origin object.
- `AccessControlAllowMethods(methods: Chunk[Method])` — NO `.All`, NO `.contains()`.
- `AccessControlAllowHeaders(headers: Chunk[String])` — NO `.All`, `.Some`, `.None` subtypes.
- `AccessControlAllowCredentials(allow: Boolean)` — NO `.Allow`.
- `AccessControlExposeHeaders(headers: Chunk[String])` — NO `.All`.
- `AccessControlMaxAge(seconds: Long)`.

### Origin (blocks)
- `sealed trait Origin` with `Null_` and `Value(scheme, host, port)`.
- Has `.renderedValue` method.

### Scala 2.13
- Use `_` not `*` for imports.
- `NonEmptyChunk` is from ZIO (`zio.NonEmptyChunk`).
- `zio.blocks.chunk.Chunk` is the blocks Chunk, different from `zio.Chunk`.
