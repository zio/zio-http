package zhttp.experiment

import io.netty.buffer.ByteBuf
import zhttp.experiment.HttpMessage.HResponse
import zhttp.http.Http

/**
 * It represents a set of "valid" types that the server can manage to decode a request into.
 */
sealed trait ServerEndpoint[-R, +E] { self => }

object ServerEndpoint {
  case object Empty extends ServerEndpoint[Any, Nothing]

  final case class Fail[E, A](cause: E) extends ServerEndpoint[Any, E]

  final case class HttpComplete[R, E](http: Http[R, E, CompleteRequest[ByteBuf], HResponse[R, E, ByteBuf]])
      extends ServerEndpoint[R, E]

  final case class HttpBuffered[R, E](http: Http[R, E, BufferedRequest[ByteBuf], HResponse[R, E, ByteBuf]])
      extends ServerEndpoint[R, E]

  final case class HttpAnyRequest[R, E](http: Http[R, E, AnyRequest, HResponse[R, E, ByteBuf]])
      extends ServerEndpoint[R, E]

  final case class HttpAny[R, E](http: Http[R, E, Any, HResponse[R, E, ByteBuf]]) extends ServerEndpoint[R, E]

  def empty: ServerEndpoint[Any, Nothing] = Empty

  def fail[E](error: E): ServerEndpoint[Any, E] = Fail(error)

  private[zhttp] sealed trait IsEndpoint[A] {
    def endpoint[R, E, B](http: Http[R, E, A, HResponse[R, E, ByteBuf]]): ServerEndpoint[R, E]
  }

  object IsEndpoint {
    implicit case object MountAnything extends IsEndpoint[Any] {
      override def endpoint[R, E, B](http: Http[R, E, Any, HResponse[R, E, ByteBuf]]): ServerEndpoint[R, E] =
        ServerEndpoint.HttpAny(http)
    }

    implicit case object MountComplete extends IsEndpoint[CompleteRequest[ByteBuf]] {
      override def endpoint[R, E, B](
        http: Http[R, E, CompleteRequest[ByteBuf], HResponse[R, E, ByteBuf]],
      ): ServerEndpoint[R, E] =
        ServerEndpoint.HttpComplete(http)
    }

    implicit case object MountBuffered extends IsEndpoint[BufferedRequest[ByteBuf]] {
      override def endpoint[R, E, B](
        http: Http[R, E, BufferedRequest[ByteBuf], HResponse[R, E, ByteBuf]],
      ): ServerEndpoint[R, E] =
        ServerEndpoint.HttpBuffered(http)
    }

    implicit case object MountAnyRequest extends IsEndpoint[AnyRequest] {
      override def endpoint[R, E, B](
        http: Http[R, E, AnyRequest, HResponse[R, E, ByteBuf]],
      ): ServerEndpoint[R, E] =
        ServerEndpoint.HttpAnyRequest(http)
    }
  }
}
