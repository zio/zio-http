package zhttp.experiment.internal

import io.netty.handler.codec.http.{HttpMessage, HttpResponse}
import zio.test.Assertion
import zio.test.Assertion.anything
import zio.test.AssertionM.Render.param

trait HttpMessageAssertion {
  implicit final class HttpMessageSyntax(m: HttpMessage) {
    def asString: String = m.toString.dropWhile(_ != '\n')
  }

  def isResponse[A](assertion: Assertion[HttpResponse]): Assertion[A] =
    Assertion.assertionRec("isResponse")(param(assertion))(assertion)(x => Option(x.asInstanceOf[HttpResponse]))

  def statusIs[A](code: Int): Assertion[HttpResponse] =
    Assertion.assertion("statusIs")()(_.status.code == code)

  def hasHeader[A](name: String, value: String, ignoreCase: Boolean = true): Assertion[HttpResponse] =
    Assertion.assertion("hasHeader")()(_.headers().contains(name, value, ignoreCase))

  def isAnyResponse: Assertion[Any] = isResponse(anything)

}
