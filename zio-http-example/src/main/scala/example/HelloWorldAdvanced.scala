package example

import zio._
import zio.http.ServerConfig.LeakDetectionLevel
import zio.http._
import zio.http.model.Method

import scala.util.Try

object HelloWorldAdvanced extends ZIOAppDefault {
  // Set a port
  private val PORT = 0

  private val fooBar: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "foo" => Response.text("bar")
    case Method.GET -> !! / "bar" => Response.text("foo")
  }

  private val app = Http.collectZIO[Request] {
    case Method.GET -> !! / "random" => Random.nextString(10).map(Response.text(_))
    case Method.GET -> !! / "utc"    => Clock.currentDateTime.map(s => Response.text(s.toString))
  }

  val run = ZIOAppArgs.getArgs.flatMap { args =>
    // Configure thread count using CLI
    val nThreads: Int = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(0)

    val config      = ServerConfig.default
      .port(PORT)
      .leakDetection(LeakDetectionLevel.PARANOID)
      .maxThreads(nThreads)
    val configLayer = ServerConfig.live(config)

    (Server.install(fooBar ++ app).flatMap { port =>
      Console.printLine(s"Started server on port: $port")
    } *> ZIO.never)
      .provide(configLayer, Server.live)
  }
}
