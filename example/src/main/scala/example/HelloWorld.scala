package example

import zhttp.http._
import zhttp.service.Server
import zio._
object HelloWorld extends App {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
    case Method.GET -> !! / "json" => Response.json("""{"greetings": "Hello World!"}""")
  }
//
//  val increment = Middleware.codec[Int, Int](decoder = a => Right(a + 1), encoder = b => Right(b + 1))
//
//  val middleware: Middleware[Any, Nothing, Nothing, Any, Any, Int] = Middleware.succeed(3)
//  val mid = middleware.flatMap((m: Int) => Middleware.succeed(m.toString))
//  val app = Http.identity[Int] @@ mid

  val middleware = Middleware.succeed("yes")
  val mid = Middleware.ifThenElse [Any, Nothing, String]((str: String) => UIO(str.length > 2))
  val mid2 = middleware.whenZIO[Any, Nothing, String]((str: String) => UIO(str.length > 2))


  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
