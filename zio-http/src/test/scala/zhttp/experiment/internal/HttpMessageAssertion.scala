package zhttp.experiment.internal

import io.netty.handler.codec.http.{HttpContent, HttpMessage, HttpResponse, LastHttpContent}
import zio.test.Assertion
import zio.test.Assertion.anything
import zio.test.AssertionM.Render.param

import java.nio.charset.Charset

trait HttpMessageAssertion {
  implicit final class HttpMessageSyntax(m: HttpMessage) {
    def asString: String = m.toString.dropWhile(_ != '\n')
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
    Assertion.assertion("body")(param(data))(_.content().toString(charset) == data)

  def header[A](name: String, value: String, ignoreCase: Boolean = true): Assertion[HttpResponse] =
    Assertion.assertion("header")(param(s"$name: $value"))(_.headers().contains(name, value, ignoreCase))

  def isAnyResponse: Assertion[Any] = isResponse(anything)
}
