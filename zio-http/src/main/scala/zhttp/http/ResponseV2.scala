package zhttp.http

import io.netty.handler.codec.http.{
  DefaultHttpResponse => JDefaultHttpResponse,
  HttpResponse => JHttpResponse,
  HttpVersion => JHttpVersion,
}
import zhttp.http.Status.OK
import zhttp.socket.SocketApp

import scala.annotation.{implicitAmbiguous, implicitNotFound, unused}

object ResponseV2 {

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

  trait HasContent[A] {
    def isEmpty: Boolean
  }

  implicit object NoContent0 extends HasContent[Nothing] {
    override def isEmpty: Boolean = true
  }

  implicit def hasContent[A]: HasContent[A] = new HasContent[A] {
    override def isEmpty: Boolean = false
  }

  sealed trait HttpResponse[+S, +A] { self =>
    import HttpResponse._

    def ++[S1 >: S, S2, S3, A1 >: A, A2, A3](other: HttpResponse[S2, A2])(implicit
      @unused @implicitNotFound("Content is already set once")
      a: CanCombine[A1, A2, A3],
      @unused @implicitNotFound("Status is already set once")
      s: CanCombine[S1, S2, S3],
    ): HttpResponse[S3, A3] =
      HttpResponse.Combine(self.asInstanceOf[HttpResponse[S3, A3]], other.asInstanceOf[HttpResponse[S3, A3]])

    def widen[S1, A1](implicit evS: S <:< S1, evA: A <:< A1): HttpResponse[S1, A1] =
      self.asInstanceOf[HttpResponse[S1, A1]]

    private def jHttpResponse[S1 >: S](implicit ev: S1 <:< Status): JHttpResponse = {
      val jResponse: JHttpResponse = new JDefaultHttpResponse(JHttpVersion.HTTP_1_1, OK.toJHttpStatus)

      def loop(response: HttpResponse[Status, A]): Unit = {
        response match {
          case ResponseStatus(status) => jResponse.setStatus(status.toJHttpStatus)
          case ResponseHeader(header) => jResponse.headers().set(header.name, header.value)
          case Combine(a, b)          => loop(a); loop(b)
          case _                      => ()
        }
        ()
      }

      loop(self.widen)
      jResponse
    }

    private def content[A1 >: A](implicit @unused ev: HasContent[A1]): A1 = {
      val nullA = null.asInstanceOf[A1]
      def loop(response: HttpResponse[S, A1]): A1 = {
        response match {
          case Combine(a, b)            =>
            val a0 = loop(a)
            if (a0 == null) loop(b) else a0
          case ResponseContent(content) => content
          case _                        => nullA
        }
      }
      loop(self)
    }

    def asResponse[S1 >: S, A1 >: A, R, E](implicit
      evS: S1 <:< Status,
      @unused evStatus: HasStatus[S1],
      evA: HasContent[A1],
      evD: A <:< HttpData[R, E],
    ): Response[R, E] = {
      if (evA.isEmpty) Response.SimpleResponse(self.jHttpResponse)
      else Response.ContentResponse(self.jHttpResponse, self.content)
    }
  }

  sealed trait Response[-R, +E]

  object Response {
    case class ContentResponse[R, E](jResponse: JHttpResponse, content: HttpData[R, E]) extends Response[R, E]
    case class SimpleResponse(jResponse: JHttpResponse)                                 extends Response[Any, Nothing]
    case class SocketResponse[R, E](socket: SocketApp[R, E])                            extends Response[R, E]
  }

  object HttpResponse {
    def status(status: Status): HttpResponse[Status, Nothing]                           = ResponseStatus(status)
    def header(name: CharSequence, value: CharSequence): HttpResponse[Nothing, Nothing] = ResponseHeader(
      Header(name, value),
    )
    def header(header: Header): HttpResponse[Nothing, Nothing]                          = ResponseHeader(header)
    def content[R, E](data: HttpData[R, E]): HttpResponse[Nothing, HttpData[R, E]]      = ResponseContent(data)

    case class ResponseStatus[S](status: S)                                extends HttpResponse[S, Nothing]
    case class ResponseHeader(header: Header)                              extends HttpResponse[Nothing, Nothing]
    case class ResponseContent[A](data: A)                                 extends HttpResponse[Nothing, A]
    case class Combine[S, A](a: HttpResponse[S, A], b: HttpResponse[S, A]) extends HttpResponse[S, A]
  }

  object Example {
//    val eg0: Response[Status, HttpData[Nothing, Nothing]] =
//      Response.status(Status.NOT_FOUND) ++
//        Response.content(HttpData.fromString("ok!")) ++
//        Response.header("A", "B") ++
//        Response.header("A", "B") ++
//        Response.header("A", "B")

    val eg0 = HttpResponse.header("Server", "ZIO Http") ++
      HttpResponse.content(HttpData.fromString("ABC")) ++
      HttpResponse.status(Status.OK)

    eg0.asResponse

    val eg1 = HttpResponse.status(Status.OK).asResponse
  }
}
