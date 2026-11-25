**QueryCodec**
- removed methods that start with `param` use methods starting with `query` instead
- renamed `queryAs` to `queryTo`

**QueryParam**
- renamed all methods that return typed params from `as` to `to`
- to align with header and to offer operations for query parameters directly on the `Request`,
  the methods are now called `queryParam` instead of `get` and `queryParams` instead of `getAll`

**URL**
- renamed `queryParams` to `setQueryParams` 
