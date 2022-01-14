# Simple Server

```scala
import zhttp.http._
import zhttp.service.Server
import zio._

object HelloWorld extends ZIOAppDefault {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
    case Method.GET -> !! / "json" => Response.jsonString("""{"greetings": "Hello World!"}""")
  }

  // Run it like any simple app
  val run =
    Server.start(8090, app.silent)
}
```