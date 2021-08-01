import zhttp.experiment.HttpMessage.{BufferedResponse, CompleteResponse, HResponse}
import zhttp.experiment._
import zhttp.http._
import zhttp.service.Server
import zio._

object HelloWorld extends App {

  val h1: HApp[Any, Nothing] = HApp(Root / "text") {
    Http.collect[CompleteRequest]({ case req => CompleteResponse(content = req.content) })
  }

  val h2: HApp[Any, Nothing] = HApp(Root / "upload") {
    Http.collect[AnyRequest]({ case req => HResponse(headers = req.headers, content = HContent.echo) })
  }

  val h3: HApp[Any, Nothing] = HApp(Root / "upload") {
    Http.collect[BufferedRequest]({ case req => BufferedResponse(content = req.content) })
  }

  val h4: HApp[Any, Nothing] = HApp {
    Http.collect[CompleteRequest] {
      case req if req.url.path == Root / "health" => CompleteResponse(content = Chunk.fromArray("Ok".getBytes()))
    }
  }

  val h: HApp[Any, Nothing] = h1 +++ h2 +++ h3 +++ h4
  
  // Create HTTP route

  val app: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> Root / "text" => Response.text("Hello World!")
    case Method.GET -> Root / "json" => Response.jsonString("""{"greetings": "Hello World!"}""")
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
