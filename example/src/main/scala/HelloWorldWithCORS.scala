import zhttp.http._
import zhttp.service.Server
import zio._

object HelloWorldWithCORS extends App {
  // Create HTTP route with CORS enabled
  val app: HttpApp[Any, Nothing] = CORS(
    HttpApp.collect {
      case Method.GET -> Root / "text" => Response.text("Hello World!")
      case Method.GET -> Root / "json" =>
        Response(
          content = HttpData("""{"greetings": "Hello World!"}"""),
          headers = Header.contentTypeJson :: Nil,
        )
    },
    config = CORSConfig(anyOrigin = true),
  )

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
