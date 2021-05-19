package zhttp.http

import io.netty.handler.codec.http.HttpVersion.HTTP_1_0
import io.netty.handler.codec.http.{
  DefaultHttpResponse => JDefaultHttpResponse,
  HttpHeaderNames => JHttpHeaderNames,
  HttpResponse => JHttpResponse,
}
import zhttp.http.Status.OK
import zhttp.socket.SocketApp

import scala.annotation.{implicitNotFound, unused}

object ResponseV2 {

  sealed trait CanCombine[X, Y, A]

  implicit def combineL[A]: CanCombine[A, Nothing, A] = null

  implicit def combineR[A]: CanCombine[Nothing, A, A] = null

  implicit def combineNothing: CanCombine[Nothing, Nothing, Nothing] = null

  sealed trait Response[+S, +A] {
    self =>
    def ++[S1 >: S, S2, S3, A1 >: A, A2, A3](other: Response[S2, A2])(implicit
      @unused @implicitNotFound("Content is already set once.")
      a: CanCombine[A1, A2, A3],
      @unused @implicitNotFound("Status is already set once.")
      s: CanCombine[S1, S2, S3],
    ): Response[S3, A3] = Response.Combine(self.asInstanceOf[Response[S3, A3]], other.asInstanceOf[Response[S3, A3]])

  }

  object Response {

    val eg0: Response[Status, HttpData[Nothing, Nothing]] =
      Response.status(Status.NOT_FOUND) ++
        Response.content(HttpData.fromString("ok!")) ++
        Response.header("A", "B") ++
        Response.header("A", "B") ++
        Response.header("A", "B")

    def status(status: Status): Response[Status, Nothing] = ResponseStatus(status)

    def header(name: CharSequence, value: CharSequence): Response[Nothing, Nothing] = ResponseHeader(
      Header(name, value),
    )

    def header(header: Header): Response[Nothing, Nothing] = ResponseHeader(header)

    def containsHTTPContent[A](response: Response[Status, A]): Boolean = response match {
      case ResponseContent(_) => true
      case Combine(a, b)      => {
        containsHTTPContent(a) || containsHTTPContent(b)
      }
      case _                  => false
    }

    def content[R, E](data: HttpData[R, E]): Response[Nothing, HttpData[R, E]] = ResponseContent(data)

    // Evaluate Response => CompleteResponse(JHttpResponse) (fast)

    // TODO: @shruti
    def evaluate[A](response: Response[Status, A]): CompleteResponse[HttpData[Any, Nothing]] = {

      val jResponse: JHttpResponse = new JDefaultHttpResponse(HTTP_1_0, OK.toJHttpStatus)

      def loop1(response: Response[Status, A]): Unit =
        response match {
          case ResponseStatus(status) => {
            jResponse.setStatus(status.toJHttpStatus)
            ()
          }
          case ResponseHeader(header) => {
            jResponse.headers().set(header.name, header.value)
            ()
          }
          case ResponseContent(data)  =>
            data match {
              case HttpData.Empty              => {
                jResponse.headers().set(JHttpHeaderNames.CONTENT_LENGTH, 0)
                ()
              }
              case HttpData.CompleteData(data) => {
                jResponse.headers().set(JHttpHeaderNames.CONTENT_LENGTH, data.length)
                ()
              }
              case HttpData.StreamData(_)      => {
                ()
              }
            }
          case Combine(a, b)          => {
            loop1(a)
            loop1(b)
          }
          case _                      => ()
        }

      loop1(response)

      def loop2(response: Response[Status, A]): HttpData[Any, Nothing] =
        if (Response.containsHTTPContent(response)) {
          response match {
            case ResponseContent(data) =>
              data match {
                case HttpData.Empty              => HttpData.empty
                case HttpData.CompleteData(data) => HttpData.CompleteData(data)
                case HttpData.StreamData(_)      => HttpData.empty
              }
            case Combine(a, b)         => {
              val c: HttpData[Any, Nothing] = loop2(a)
              if (c.equals(HttpData.empty)) loop2(b) else c
            }
            case _                     => HttpData.empty
          }
        } else {
          throw new Exception("Response doesn't contain content")
        }

      CompleteResponse(jResponse, loop2(response))
    }

    sealed trait ResponseWithStatus[A] extends Response[Status, A]

    case class CompleteResponse[A](jResponse: JHttpResponse, content: A) extends ResponseWithStatus[A]

    case class SocketResponse[R, E](socket: SocketApp[R, E]) extends ResponseWithStatus[SocketApp[R, E]]

    case class ResponseStatus(status: Status) extends Response[Status, Nothing]

    case class ResponseHeader(header: Header) extends Response[Nothing, Nothing]

    case class ResponseContent[R, E](data: HttpData[R, E]) extends Response[Nothing, HttpData[R, E]]

    case class Combine[S, A](a: Response[S, A], b: Response[S, A]) extends Response[S, A]

  }

}
