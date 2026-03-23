# Form Codec Implementation Learnings

## Key Patterns
- `BinaryCodecWithSchema` has two `apply` overloads: `(CodecConfig => BinaryCodec[A], Schema[A])` and `(BinaryCodec[A], Schema[A])` (wraps in `_ => codec`)
- `HttpContentCodec.Choices(ListMap.empty)` crashes because `defaultMediaType` etc are eager `val`s that throw on empty choices
- `internal` is ambiguous between `zio.http.internal` and `zio.stream.internal` — must use fully-qualified `zio.http.internal.*`

## StringSchemaCodec.queryFromSchema
- `encode(input: A, target: Target): Target` — builds QueryParams from value
- `decode(target: Target): A` — decodes from QueryParams (throws on error, doesn't return Either)
- `name` parameter: passing `null` prevents single-field records from being wrapped into a new CaseClass1 with a different key name
- Throws `IllegalArgumentException` for unsupported schemas like Enum (sealed traits)

## QueryParams.encode
- Returns string with leading `?` (e.g., `?key=value&key2=value2`)
- Must `.drop(1)` for form body encoding

## fromSchema graceful degradation
- Not all schemas support form encoding (e.g., Enum/sealed traits)
- Use try-catch around `form.only[A]` in `fromSchema` to gracefully skip form codec for unsupported types
- Cannot return empty Choices as fallback — eager val initialization crashes

## Body.fromString
- Does NOT have a `mediaType` parameter
- Use `.contentType(mediaType)` method to set content type on body
