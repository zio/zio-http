package zhttp.experiment.internal

import io.netty.handler.codec.http._
import zhttp.experiment.HApp
import zhttp.service.EventLoopGroup
import zio.ZIO
import zio.test.Assertion.anything
import zio.test.AssertionM.Render.param
import zio.test.{Assertion, TestResult, assertM}

import java.nio.charset.Charset

trait HttpMessageAssertion {
  implicit final class HttpMessageSyntax(m: HttpObject) {
    def asString: String = m.toString.dropWhile(_ != '\n')
  }

  implicit final class HAppSyntax[R, E](app: HApp[R, Throwable]) {
    def ===(assertion: Assertion[HttpObject]): ZIO[R with EventLoopGroup, Nothing, TestResult] =
      assertM(execute(app))(assertion)
  }

  def isResponse[A](assertion: Assertion[HttpResponse]): Assertion[A] =
    Assertion.assertionRec("isResponse")(param(assertion))(assertion)({
      case msg: HttpResponse => Option(msg)
      case _                 => None
    })

  def isContent[A](assertion: Assertion[HttpContent]): Assertion[A] =
    Assertion.assertionRec("isContent")(param(assertion))(assertion)({
      case msg: HttpContent => Option(msg)
      case _                => None
    })

  def isLastContent[A](assertion: Assertion[LastHttpContent]): Assertion[A] =
    Assertion.assertionRec("isLastContent")(param(assertion))(assertion)({
      case msg: LastHttpContent => Option(msg)
      case _                    => None
    })

  def status(code: Int): Assertion[HttpResponse] =
    Assertion.assertion("status")(param(code))(_.status().code() == code)

  def bodyText(data: String, charset: Charset = Charset.defaultCharset()): Assertion[HttpContent] =
    Assertion.assertion("body")(param(data))(_.content().toString(charset).contains(data))

  def header(name: String, value: String, ignoreCase: Boolean = true): Assertion[HttpResponse] =
    Assertion.assertion("header")(param(s"$name: $value"))(_.headers().contains(name, value, ignoreCase))

  def header(name: String): Assertion[HttpResponse] =
    Assertion.assertion("header")(param(s"$name: ???"))(_.headers().contains(name))

  def noHeader: Assertion[HttpResponse] = Assertion.assertion("no header")()(_.headers().size() == 0)

  def version(name: String): Assertion[HttpResponse] =
    Assertion.assertion("version")(param(name))(_.protocolVersion().toString == name)

  def isAnyResponse: Assertion[Any] = isResponse(anything)

  def execute[R](
    app: HApp[R, Throwable],
    req: HttpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"),
  ): ZIO[R with EventLoopGroup, Nothing, HttpObject] =
    for {
      proxy <- ChannelProxy.make(app)
      _     <- proxy.dispatch(req)
      res   <- proxy.receive
    } yield res
}
