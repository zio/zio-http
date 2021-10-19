import Util.{Metrics, runtime}
import io.prometheus.client.CollectorRegistry
import zhttp.http._
import zhttp.service.Server
import zio.console.Console
import zio.metrics.prometheus._
import zio.metrics.prometheus.exporters._
import zio.metrics.prometheus.helpers._
import zio.{Has, Layer, RIO, Runtime, Task, ZIO, ZLayer}

object Util {
  type Env = Registry with Exporters with Console
  val runtime = Runtime.unsafeFromLayer(Registry.live ++ Exporters.live ++ Console.live)
  type Metrics = Has[SampleMetrics.Service]

}

object SampleMetrics {
  trait Service {
    def getRegistry(): Task[CollectorRegistry]
    def inc(tags: Array[String]): Task[Unit]
    def inc(amount: Double, tags: Array[String]): Task[Unit]
  }

  val live: Layer[Nothing, Metrics] = ZLayer.succeed(new Service {
    private val myCounter = runtime.unsafeRun(
      for {
        c <- counter.register("http_request_total", help = "Total HTTP requests served")
      } yield c,
    )

    def getRegistry(): Task[CollectorRegistry]               = getCurrentRegistry().provideLayer(Registry.live)
    def inc(tags: Array[String]): zio.Task[Unit]             = inc(1.0, tags)
    def inc(amount: Double, tags: Array[String]): Task[Unit] = myCounter.inc(amount, tags)
  })

}

object HelloMetrics {
  def app: HttpApp[Exporters with Metrics, Throwable] = {
    def flush(content: Option[String] = None) = for {
      metrics <- ZIO.environment[Metrics]
      _       <- metrics.get.inc(Array.empty)
      r       <- metrics.get.getRegistry()
      message <- write004(r)
    } yield Response.text(content match {
      case Some(text) => text
      case None       => message
    })

    HttpApp.collectM {
      case Method.GET -> !! / "health"  => flush(Some("Status: Up!"))
      case Method.GET -> !! / "metrics" => flush()
    }
  }

  def main(args: Array[String]): Unit = {
    val startup = RIO.environment[Metrics].flatMap { metrics =>
      metrics.get.getRegistry().flatMap { registry =>
        initializeDefaultExports(registry)
      }
    } *> Server.start(8080, app)

    runtime.unsafeRun(startup.provideSomeLayer[Registry with Exporters with Console](SampleMetrics.live))
  }
}
