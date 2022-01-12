# Streaming File
```scala
import zhttp.http._
import zhttp.service._
import zio._
import zio.stream._

import java.nio.file.Paths

object FileStreaming extends ZIOAppDefault {
  // Read the file as ZStream
  val content = HttpData.fromStream {
    ZStream.fromFile(Paths.get("README.md"))
  }

  // Create HTTP route
  val app = Http.collect[Request] {
    case Method.GET -> !! / "health" => Response.ok
    case Method.GET -> !! / "file"   => Response.http(content = content)
  }

  // Run it like any simple app
  val run =
    Server.start(8090, app.silent)
}

```