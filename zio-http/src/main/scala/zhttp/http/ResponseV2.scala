package zhttp.http

import io.netty.handler.codec.http.{HttpResponse => JHttpResponse}
import zhttp.socket.SocketApp

import scala.annotation.{implicitNotFound, unused}

object ResponseV2 {

//  object version_1 {
//    sealed trait Response[-R, +E] {
//      self =>
//      def ++[R1 <: R, E1 >: E](other: Response[R1, E1]): Response[R1, E1] = Response.Combine(self, other)
//    }
//
//    object Response {
//      case class Complete[R, E](status: Status, headers: List[Header], content: HttpData[R, E]) extends Response[R, E]
//      case class ResponseSocket[R, E](socket: SocketApp[R, E])                                  extends Response[R, E]
//      case class ResponseStatus(status: Status)                                                 extends Response[Any, Nothing]
//      case class ResponseHeader(header: Header)                                                 extends Response[Any, Nothing]
//      case class ResponseContent[R, E](data: HttpData[R, E])                                    extends Response[R, E]
//      case class Combine[R, E](a: Response[R, E], b: Response[R, E])                            extends Response[R, E]
//
//      def status(status: Status): Response[Any, Nothing]                          = ResponseStatus(status)
//      def header(name: CharSequence, value: CharSequence): Response[Any, Nothing] = ResponseHeader(Header(name, value))
//      def header(header: Header): Response[Any, Nothing]                          = ResponseHeader(header)
//      def content[R, E](data: HttpData[R, E]): Response[R, E]                     = ResponseContent(data)
//    }
//
//    val eg0 = Response.status(Status.OK) ++
//      Response.status(Status.NOT_FOUND) ++
//      Response.content(HttpData.fromString("Hello World!")) ++
//      Response.content(HttpData.fromString("Bye Bye World"))
//  }

  object version_2 {
    sealed trait CanCombine[X, Y, A]
    implicit def combineL[A]: CanCombine[A, Nothing, A]                = null
    implicit def combineR[A]: CanCombine[Nothing, A, A]                = null
    implicit def combineNothing: CanCombine[Nothing, Nothing, Nothing] = null

    sealed trait Response[+S, +A] {
      self =>
      def ++[S1 >: S, S2, S3, A1 >: A, A2, A3](other: Response[S2, A2])(implicit
        @unused @implicitNotFound("Content is already set once.")
        a: CanCombine[A1, A2, A3],
        @unused @implicitNotFound("Status is already set once.")
        s: CanCombine[S1, S2, S3],
      ): Response[S3, A3] = Response.Combine(self, other)

      def dropHeader(name: CharSequence): Response[Nothing, Nothing]                   = ???
      def updateContent[R, E](data: HttpData[R, E]): Response[Nothing, HttpData[R, E]] = ???
      def updateStatus(status: Status): Response[Status, A]                            = ???

      // Answered Questions
      // - How do we update status? (ok - specialized operator)
      // - How do I delete a Header? (ok - specialized operator)
      // - How do I update content? (ok - specialized operator)
      //
    }

    object Response {
      sealed trait ResponseWithStatus[A] extends Response[Status, A]

      // Option 3
      // case class Complete[A](status: Status, header: List[Header], content: A) extends Out[A]
      // Response using ++ operator (fast)
      //
      // Evaluate Response => Complete (slow)
      // Evaluate Complete => JHttpResponse (fast)

      // Option 4
      // Response using ++ (fast)
      //
      // Evaluate Response => CompleteResponse(JHttpResponse) (fast)

      case class CompleteResponse[A](jResponse: JHttpResponse, content: A) extends ResponseWithStatus[A]
      case class SocketResponse[R, E](socket: SocketApp[R, E])             extends ResponseWithStatus[SocketApp[R, E]]

      case class ResponseStatus(status: Status)                                            extends Response[Status, Nothing]
      case class ResponseHeader(header: Header)                                            extends Response[Nothing, Nothing]
      case class ResponseContent[R, E](data: HttpData[R, E])                               extends Response[Nothing, HttpData[R, E]]
      case class Combine[S1, A1, S2, A2, S3, A3](a: Response[S1, A1], b: Response[S2, A2]) extends Response[S3, A3]

      def status(status: Status): Response[Status, Nothing]                           = ResponseStatus(status)
      def header(name: CharSequence, value: CharSequence): Response[Nothing, Nothing] = ResponseHeader(
        Header(name, value),
      )
      def header(header: Header): Response[Nothing, Nothing]                          = ResponseHeader(header)
      def content[R, E](data: HttpData[R, E]): Response[Nothing, HttpData[R, E]]      = ResponseContent(data)

      // TODO: @shruti
      def evaluate[A](response: Response[Status, A]): ResponseWithStatus[A] = ???
    }

//    implicit def toResponse(status: Status): Response[Status, Nothing]                     = Response.status(status)
//    implicit def toResponse[R, E](data: HttpData[R, E]): Response[Nothing, HttpData[R, E]] = Response.content(data)
//    implicit def toResponse(header: Header): Response[Nothing, Nothing]                    = Response.header(header)
//    implicit def toResponse(kv: (CharSequence, CharSequence)): Response[Nothing, Nothing]  =
//      Response.header(kv._1, kv._2)

    // Response[Status, Nothing] ++ Response[Status, Nothing]

    // CanCombine[Status, Status, ?]
    // CanCombine[Any, Status, ?]
    // CanCombine[Any, Any, ?]

    val eg0: Response[Status, HttpData[Nothing, Nothing]] =
      Response.status(Status.NOT_FOUND) ++
        Response.content(HttpData.fromString("ok!")) ++
        Response.header("A", "B") ++
        Response.header("A", "B") ++
        Response.header("A", "B")

//    def asJsonResponse[S, A](response: Response[S, A]) = response ++ ("content-type" -> "application/json")

//      Response.status(Status.NOT_FOUND) ++ Response.status(Status.NOT_FOUND)

//      Response.status(Status.NOT_FOUND)
    /*Response.status(Status.NOT_FOUND) ++
      Response.content(HttpData.fromString("Hello World!")) ++
      Response.content(HttpData.fromString("Bye Bye World"))*/
  }
}
