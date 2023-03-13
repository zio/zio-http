package example

import scala.util.Try

import zio._

import zio.http._
import zio.http.model.Method
import zio.http.netty.NettyServerConfig
import zio.http.netty.NettyServerConfig.LeakDetectionLevel

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

    val config           = ServerConfig.default
      .port(PORT)
      .maxThreads(nThreads)
    val nettyConfig      = NettyServerConfig.default
      .leakDetection(LeakDetectionLevel.PARANOID)
    val configLayer      = ServerConfig.live(config)
    val nettyConfigLayer = NettyServerConfig.live(nettyConfig)

    (Server.install(fooBar ++ app).flatMap { port =>
      Console.printLine(s"Started server on port: $port")
    } *> ZIO.never)
      .provide(configLayer, nettyConfigLayer, Server.customized)
  }
}
