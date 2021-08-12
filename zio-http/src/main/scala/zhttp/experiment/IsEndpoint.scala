package zhttp.experiment

import io.netty.buffer.ByteBuf
import zhttp.experiment.HEndpoint.ServerEndpoint
import zhttp.experiment.HttpMessage.HResponse
import zhttp.http.Http

private [zhttp] sealed trait IsEndpoint[A] {
  def endpoint[R, E, B](http: Http[R, E, A, HResponse[R, E, ByteBuf]]): ServerEndpoint[R, E]
}

object IsEndpoint {
  implicit case object MountAnything extends IsEndpoint[Any] {
    override def endpoint[R, E, B](http: Http[R, E, Any, HResponse[R, E, ByteBuf]]): ServerEndpoint[R, E] =
      ServerEndpoint.from(http)
  }

  implicit case object MountComplete extends IsEndpoint[CompleteRequest[ByteBuf]] {
    override def endpoint[R, E, B](
      http: Http[R, E, CompleteRequest[ByteBuf], HResponse[R, E, ByteBuf]],
    ): ServerEndpoint[R, E] =
      ServerEndpoint.from(http)
  }

  implicit case object MountBuffered extends IsEndpoint[BufferedRequest[ByteBuf]] {
    override def endpoint[R, E, B](
      http: Http[R, E, BufferedRequest[ByteBuf], HResponse[R, E, ByteBuf]],
    ): ServerEndpoint[R, E] =
      ServerEndpoint.from(http)
  }

  implicit case object MountAnyRequest extends IsEndpoint[AnyRequest] {
    override def endpoint[R, E, B](
      http: Http[R, E, AnyRequest, HResponse[R, E, ByteBuf]],
    ): ServerEndpoint[R, E] =
      ServerEndpoint.from(http)
  }
}
