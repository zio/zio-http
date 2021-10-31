package zhttp.middleware

import java.io.IOException
import zhttp.http._
import zhttp.http.middleware.LogMiddleware._
import zhttp.service.EventLoopGroup
import zio.clock.Clock
import zio.console._
import zio.test.environment.TestConsole
import zio.test.Assertion.{containsString, isEmpty, isNonEmpty, not}
import zio.test.{DefaultRunnableSpec, assertM}
import zio.{UIO, ZIO}

object LogMiddlewareSpec extends DefaultRunnableSpec {
  private val env = EventLoopGroup.auto(1)

  val requestLogger = RequestLogger(
    logMethod = true,
    logHeaders = true,
    mapHeaders = _.filter(header => header.name == "Host" || header.name == "Authorization")
      .map(header => if (header.name == "Authorization") header.copy(value = "****") else header),
  )

  val responseLogger =
    ResponseLogger(logMethod = true, logHeaders = true)

  val options = Options.Skip { case ((_, url, _), _) => url.path.startsWith(Path("test")) }

  def createApp(logMethod: Boolean = true, logHeaders: Boolean = true): HttpApp[Clock with Console, IOException] =
    HttpApp.collectM {
      case Method.GET -> !! / "ping" =>
        UIO(Response.text("pong"))
      case Method.GET -> !! / "test" =>
        UIO(Response.ok)
    } @@ log(
      request = requestLogger.copy(logHeaders = logHeaders, logMethod = logMethod),
      response = responseLogger.copy(logHeaders = logHeaders, logMethod = logMethod),
      options = options,
    ) { zio.console.putStrLn(_) }

  def run[R, E](app: HttpApp[R, E], endpoint: String): ZIO[Clock with Console with R, Option[E], Response[R, E]] = {
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

  def spec = suite("LogMiddleware") {
    testM("log GET request") {
      run(createApp(), "ping") *> assertM(TestConsole.output)(isNonEmpty)
    } + testM("skip specific request from being logged") {
      run(createApp(), "test") *> assertM(TestConsole.output)(isEmpty)
    } + testM("log http headers") {
      run(createApp(), "ping") *> assertM(TestConsole.output.map(_.mkString("\n")))(containsString("Host,localhost"))
    } + testM("change the way some specific header is displayed") {
      run(createApp(), "ping") *> assertM(TestConsole.output.map(_.mkString("\n")))(
        containsString("Authorization,****"),
      )
    } + testM("skip not allowed header") {
      run(createApp(), "ping") *> assertM(TestConsole.output.map(_.mkString("\n")))(not(containsString("Accepts")))
    } + testM("do not log http headers") {
      run(createApp(logHeaders = false), "ping") *> assertM(TestConsole.output.map(_.mkString("\n")))(
        not(containsString("Headers")),
      )
    } + testM("do not log http method") {
      run(createApp(logMethod = false), "ping") *> assertM(TestConsole.output.map(_.mkString("\n")))(
        not(containsString("Method=GET")),
      )
    }
  }.provideCustomLayer(env)
}
