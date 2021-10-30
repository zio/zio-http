package zhttp.middleware

import java.io.IOException
import zhttp.http._
import zhttp.http.middleware.LogMiddleware._
import zhttp.service.EventLoopGroup
import zio.clock.Clock
import zio.logging.{LogContext, LogFormat, LogLevel, Logger, Logging}
import zio.test.Assertion.{containsString, isEmpty, isNonEmpty, not}
import zio.test.{DefaultRunnableSpec, assertM}
import zio.{FiberRef, Has, Layer, Ref, UIO, ZIO, ZLayer}

object LogMiddlewareSpec extends DefaultRunnableSpec {
  private val logEnv =
    Logging.console(
      logLevel = LogLevel.Info,
      format = LogFormat.ColoredLogFormat(),
    ) >>> Logging.withRootLoggerName("zio-http") ++ EventLoopGroup.auto(1)

  val requestLogger  = RequestLogger(
    logMethod = true,
    logHeaders = true,
    level = LogLevel.Debug,
    mapHeaders = _.filter(header => header.name == "Host" || header.name == "Authorization")
      .map(header => if (header.name == "Authorization") header.copy(value = "****") else header),
  )

  val responseLogger =
    ResponseLogger(logMethod = true, logHeaders = true, level = LogLevel.Info)

  val options = Options.Skip { case ((_, url, _), _) => url.path.startsWith(Path("test")) }

  def createApp(logMethod: Boolean = true, logHeaders: Boolean = true): HttpApp[Clock with Logging, IOException] =
    HttpApp.collectM {
      case Method.GET -> !! / "ping" =>
        UIO(Response.text("pong"))
      case Method.GET -> !! / "test" =>
        UIO(Response.ok)
    } @@ log(
      request = requestLogger.copy(logHeaders = logHeaders, logMethod = logMethod),
      response = responseLogger.copy(logHeaders = logHeaders, logMethod = logMethod),
      options = options,
    )

  def run[R, E](app: HttpApp[R, E], endpoint: String): ZIO[Clock with Logging with R, Option[E], Response[R, E]] = {
    val headers = List(
      Header(name = "Host", value = "localhost"),
      Header(name = "Accepts", value = "application/json"),
      Header(name = "Authorization", value = "123456"),
    )

    for {
      fib <- app { Request(url = URL(!! / endpoint), headers = headers) }.fork
      res <- fib.join
    } yield res
  }

  // IMPORTANT:
  // Original code taken from zio-logging/LoggerSpec (zio-logging/core/shared/src/test/scala/zio/logging/LoggerSpec.scala)
  // Added linesAsString to convert Vector[(LogContext, String)] => String
  object TestLogger {
    type TestLogging = Has[TestLogger.Service]
    trait Service extends Logger[String] {
      def lines: UIO[Vector[(LogContext, String)]]
      def linesAsString: UIO[String]
    }
    def make: Layer[Nothing, TestLogging with Logging] =
      ZLayer.fromEffectMany(for {
        data   <- Ref.make(Vector.empty[(LogContext, String)])
        logger <- FiberRef
          .make(LogContext.empty)
          .map { ref =>
            new Logger[String] with TestLogger.Service {
              def locally[R1, E, A](f: LogContext => LogContext)(zio: ZIO[R1, E, A]): ZIO[R1, E, A] =
                ref.get.flatMap(context => ref.locally(f(context))(zio))

              def log(line: => String): UIO[Unit] =
                ref.get.flatMap(context => data.update(_ :+ ((context, line))).unit)

              def logContext: UIO[LogContext] = ref.get

              def lines: UIO[Vector[(LogContext, String)]] = data.get

              def linesAsString: UIO[String] = for {
                lines <- data.get
              } yield lines.map { case (_, l) => l }.mkString("\n")
            }
          }

      } yield Has.allOf[Logger[String], TestLogger.Service](logger, logger))

    def lines: ZIO[TestLogging, Nothing, Vector[(LogContext, String)]] = ZIO.accessM[TestLogging](_.get.lines)
    def linesAsString: ZIO[TestLogging, Nothing, String]               = ZIO.accessM[TestLogging](_.get.linesAsString)
  }

  def spec = suite("LogMiddleware") {
    testM("log GET request") {
      run(createApp(), "ping") *> assertM(TestLogger.lines)(isNonEmpty)
    } + testM("skip specific request from being logged") {
      run(createApp(), "test") *> assertM(TestLogger.lines)(isEmpty)
    } + testM("log http headers") {
      run(createApp(), "ping") *> assertM(TestLogger.linesAsString)(containsString("Host,localhost"))
    } + testM("change the way some specific header is displayed") {
      run(createApp(), "ping") *> assertM(TestLogger.linesAsString)(containsString("Authorization,****"))
    } + testM("skip not allowed header") {
      run(createApp(), "ping") *> assertM(TestLogger.linesAsString)(not(containsString("Accepts")))
    } + testM("do not log http headers") {
      run(createApp(logHeaders = false), "ping") *> assertM(TestLogger.linesAsString)(not(containsString("Headers")))
    } + testM("do not log http method") {
      run(createApp(logMethod = false), "ping") *> assertM(TestLogger.linesAsString)(not(containsString("Method=GET")))
    }
  }.provideCustomLayer(logEnv ++ TestLogger.make)
}
