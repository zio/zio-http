import zio._
import zio.console._
import zio.clock._
import zio.logging.{LogFormat, LogLevel, Logging}
import zhttp.endpoint._
import zhttp.http._
import zhttp.http.Method.GET
import zhttp.http.middleware.LogMiddleware._
import zhttp.service.Server

object LogMiddlewareSample extends App {
  val env =
    Logging.console(
      logLevel = LogLevel.Info,
      format = LogFormat.ColoredLogFormat(),
    ) >>> Logging.withRootLoggerName("zio-http")

  def echo = GET / *[String] to { a =>
    Response.text(a.params + "\n")
  }

  def test = GET / "test" to { _ =>
    Response.text("TEST")
  }

  val requestLogger  = RequestLogger(
    logMethod = true,
    logHeaders = true,
    level = LogLevel.Debug,
    mapHeaders = _.filter(h => h.name == "Host"),
  )
  val responseLogger =
    ResponseLogger(logMethod = true, logHeaders = true, level = LogLevel.Info)

  val options = Options.Skip { case ((_, url, _), _) => url.path.startsWith(Path("test")) }

  def app: HttpApp[Console with Clock with Logging, Throwable] =
    (test +++ echo) @@ log(
      request = requestLogger,
      response = responseLogger,
      options = options,
    )

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).provideCustomLayer(env).exitCode
}
