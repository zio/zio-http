package zhttp.http

import zhttp.http.Status.OK
import io.netty.handler.codec.http.{
  DefaultHttpResponse => JDefaultHttpResponse,
  HttpResponse => JHttpResponse,
  HttpVersion => JHttpVersion,
}

import scala.annotation.{implicitAmbiguous, implicitNotFound, unused}
import scala.language.implicitConversions

sealed trait HttpResponseBuilder[+S, +A] { self =>
  import HttpResponseBuilder._

  def ++[S1 >: S, S2, S3, A1 >: A, A2, A3](other: HttpResponseBuilder[S2, A2])(implicit
    @unused @implicitNotFound("Content is already set once")
    a: CanCombine[A1, A2, A3],
    @unused @implicitNotFound("Status is already set once")
    s: CanCombine[S1, S2, S3],
  ): HttpResponseBuilder[S3, A3] =
    HttpResponseBuilder.Combine(
      self.asInstanceOf[HttpResponseBuilder[S3, A3]],
      other.asInstanceOf[HttpResponseBuilder[S3, A3]],
    )

  def widen[S1, A1](implicit evS: S <:< S1, evA: A <:< A1): HttpResponseBuilder[S1, A1] =
    self.asInstanceOf[HttpResponseBuilder[S1, A1]]

  private[zhttp] def jHttpResponse[S1 >: S](implicit ev: S1 <:< Status): JHttpResponse = {
    val jResponse: JHttpResponse = new JDefaultHttpResponse(JHttpVersion.HTTP_1_1, OK.toJHttpStatus)

    def loop(response: HttpResponseBuilder[Status, A]): Unit = {
      response match {
        case HttpResponseStatus(status) => jResponse.setStatus(status.toJHttpStatus)
        case HttpResponseHeader(header) => jResponse.headers().set(header.name, header.value)
        case Combine(a, b)              => loop(a); loop(b)
        case _                          => ()
      }
      ()
    }

    loop(self.widen)
    jResponse
  }

  private[zhttp] def content[A1 >: A](implicit @unused ev: HasContent[A1]): A1 = {
    val nullA = null.asInstanceOf[A1]
    def loop(response: HttpResponseBuilder[S, A1]): A1 = {
      response match {
        case Combine(a, b)                =>
          val a0 = loop(a)
          if (a0 == null) loop(b) else a0
        case HttpResponseContent(content) => content
        case _                            => nullA
      }
    }
    loop(self)
  }

  private[zhttp] def asHttpResponse[S1 >: S, A1 >: A, R, E](implicit
    evS: S1 <:< Status,
    @unused evStatus: HasStatus[S1],
    evA: HasContent[A1],
    evD: A <:< HttpData[R, E],
  ): Response[R, E] = Response.FromResponseBuilder(self.widen)
}

object HttpResponseBuilder {
  private final case class HttpResponseStatus[S](status: S)   extends HttpResponseBuilder[S, Nothing]
  private final case class HttpResponseHeader(header: Header) extends HttpResponseBuilder[Nothing, Nothing]
  private final case class HttpResponseContent[A](data: A)    extends HttpResponseBuilder[Nothing, A]
  private final case class Combine[S, A](a: HttpResponseBuilder[S, A], b: HttpResponseBuilder[S, A])
      extends HttpResponseBuilder[S, A]

  sealed trait CanCombine[X, Y, A]

  object CanCombine {
    implicit def combineL[A]: CanCombine[A, Nothing, A]                = null
    implicit def combineR[A]: CanCombine[Nothing, A, A]                = null
    implicit def combineNothing: CanCombine[Nothing, Nothing, Nothing] = null
  }

  @implicitNotFound("Response doesn't have status set")
  sealed trait HasStatus[S]
  implicit object HasStatus extends HasStatus[Status]

  @implicitAmbiguous("Response doesn't have status set")
  implicit object HasNoStatus0 extends HasStatus[Nothing]
  implicit object HasNoStatus1 extends HasStatus[Nothing]

  trait HasContent[A]
  object HasContent {
    implicit object NoContent0 extends HasContent[Nothing]
    implicit def hasContent[A]: HasContent[A] = null.asInstanceOf[HasContent[A]]
  }

  implicit def status(status: Status): HttpResponseBuilder[Status, Nothing]                      = HttpResponseStatus(status)
  implicit def header(header: Header): HttpResponseBuilder[Nothing, Nothing]                     = HttpResponseHeader(header)
  implicit def content[R, E](data: HttpData[R, E]): HttpResponseBuilder[Nothing, HttpData[R, E]] = HttpResponseContent(
    data,
  )

}
