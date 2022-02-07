package zhttp.http.middleware

import zhttp.http._
import zhttp.internal.HttpAppTestExtensions
import zio._
import zio.test.TestAspect.{after, ignore, runtimeConfig, sequential}
import zio.test.{TestClock, ZIOSpecDefault, assertTrue}

object LoggingMiddlewareSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  val logOutput: Ref[Chunk[String]] = Runtime.default.unsafeRun(Ref.make(Chunk.empty))

  def appendLogMessage(s: String): Unit =
    Runtime.default.unsafeRun(logOutput.update(_ :+ s))

  val testStringLogFormatter: ZLogger[String, String] =
    (
      trace: ZTraceElement,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => String,
      _: Map[FiberRef.Runtime[_], AnyRef],
      spans: List[LogSpan],
      _: ZTraceElement,
      _: Map[String, String],
    ) => s"[$fiberId][${logLevel.label}]:{${fiberId.threadName}} ${message()} ${spans.mkString("-")}, trace: $trace"

  lazy val testStringLogger: ZLogger[String, Unit]    = testStringLogFormatter.map(appendLogMessage)
  lazy val testCauseLogger: ZLogger[Cause[Any], Unit] = testStringLogger.contramap[Cause[Any]](_.prettyPrint)

  lazy val testLoggerSet: ZLogger.Set[String & Cause[Any], Any] =
    testStringLogger.toSet[String] ++ testCauseLogger.toSet[Cause[Any]]

  lazy val testLoggerAspect: RuntimeConfigAspect =
    RuntimeConfigAspect { config =>
      config.copy(loggers = testLoggerSet)
    }

  def assertLogged[R, T](log: ZIO[R, Option[Throwable], T])(substrings: String*) =
    for {
      _      <- log
      output <- logOutput.get
    } yield assertTrue(substrings.forall(substring => output.exists(_.contains(substring))))

  val logger = Middleware.log()

  override def spec = suite("LogMiddleware") {
    suite("log") {
      test("GET request should generate proper logging") {
        val program = run(app @@ logger, URL(!! / "plaintext"))
        assertLogged(program)(
          "Status: OK",
        )
      } +
        test("GET request should generate proper logging when server responds with 500") {
          val program = run(app @@ logger, URL(!! / "fail"))
          assertLogged(program)(
            "Status: INTERNAL_SERVER_ERROR",
          )
        } @@ ignore +
        test("GET request should generate proper logging when server responds with 502") {
          val program = run(app @@ logger, URL(!! / "bad_gateway"))
          assertLogged(program)(
            "Status: BAD_GATEWAY",
          )
        }
    } @@ sequential @@ runtimeConfig(testLoggerAspect) @@ after(logOutput.set(Chunk.empty))
  }

  private val app = Http.collectZIO[Request] {
    case Method.GET -> !! / "plaintext"   =>
      ZIO.succeed(Response.ok).delay(10 seconds)
    case Method.GET -> !! / "fail"        =>
      ZIO.fail(new RuntimeException("Error"))
    case Method.GET -> !! / "bad_gateway" =>
      ZIO.succeed(Response.status(Status.BAD_GATEWAY))
  }

  private def run[R, E](app: HttpApp[R, E], url: URL): ZIO[TestClock with R, Option[E], Response] = {
    for {
      fib <- app { Request(url = url) }.fork
      _   <- TestClock.adjust(10 seconds)
      res <- fib.join
    } yield res
  }
}
