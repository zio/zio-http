```scala mdoc:passthrough
import zio.http.docs.ConfigReference
import zio.http.gen.openapi

println(ConfigReference.referencePageFor(openapi.Config))
```