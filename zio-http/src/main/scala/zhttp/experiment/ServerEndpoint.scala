package zhttp.experiment

import io.netty.buffer.ByteBuf
import zhttp.http.Http

/**
 * It represents a set of "valid" types that the server can manage to decode a request into.
 */
sealed trait ServerEndpoint[-R, +E] { self => }

object ServerEndpoint {
  case object Empty extends ServerEndpoint[Any, Nothing]

  final case class HttpComplete[R, E](http: Http[R, E, CompleteRequest[ByteBuf], AnyResponse[R, E, ByteBuf]])
      extends ServerEndpoint[R, E]

  final case class HttpBuffered[R, E](http: Http[R, E, BufferedRequest[ByteBuf], AnyResponse[R, E, ByteBuf]])
      extends ServerEndpoint[R, E]

  final case class HttpAnyRequest[R, E](http: Http[R, E, AnyRequest, AnyResponse[R, E, ByteBuf]])
      extends ServerEndpoint[R, E]

  final case class HttpAny[R, E](http: Http[R, E, Any, AnyResponse[R, E, ByteBuf]]) extends ServerEndpoint[R, E]

  final case class HttpLazy[R, E](http: Http[R, E, LazyRequest, AnyResponse[R, E, ByteBuf]])
      extends ServerEndpoint[R, E]

  def empty: ServerEndpoint[Any, Nothing] = Empty

  def fail[E](error: E): ServerEndpoint[Any, E] = HttpAny(Http.fail(error))

  private[zhttp] sealed trait CanDecode[A] extends Serializable with Product {
    def endpoint[R, E, B](http: Http[R, E, A, AnyResponse[R, E, ByteBuf]]): ServerEndpoint[R, E]
  }

  object CanDecode {
    implicit case object MountAnything extends CanDecode[Any] {
      override def endpoint[R, E, B](http: Http[R, E, Any, AnyResponse[R, E, ByteBuf]]): ServerEndpoint[R, E] =
        ServerEndpoint.HttpAny(http)
    }

    implicit case object MountComplete extends CanDecode[CompleteRequest[ByteBuf]] {
      override def endpoint[R, E, B](
        http: Http[R, E, CompleteRequest[ByteBuf], AnyResponse[R, E, ByteBuf]],
      ): ServerEndpoint[R, E] =
        ServerEndpoint.HttpComplete(http)
    }

    implicit case object MountBuffered extends CanDecode[BufferedRequest[ByteBuf]] {
      override def endpoint[R, E, B](
        http: Http[R, E, BufferedRequest[ByteBuf], AnyResponse[R, E, ByteBuf]],
      ): ServerEndpoint[R, E] =
        ServerEndpoint.HttpBuffered(http)
    }

    implicit case object MountAnyRequest extends CanDecode[AnyRequest] {
      override def endpoint[R, E, B](
        http: Http[R, E, AnyRequest, AnyResponse[R, E, ByteBuf]],
      ): ServerEndpoint[R, E] =
        ServerEndpoint.HttpAnyRequest(http)
    }

    implicit case object MountLazyRequest extends CanDecode[LazyRequest] {
      override def endpoint[R, E, B](
        http: Http[R, E, LazyRequest, AnyResponse[R, E, ByteBuf]],
      ): ServerEndpoint[R, E] =
        ServerEndpoint.HttpLazy(http)
    }
  }
}
