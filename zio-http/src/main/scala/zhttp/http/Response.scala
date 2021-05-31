package zhttp.http

import zhttp.socket.SocketApp
import zhttp.core.{Direction, HBuf1}
import zio.ZIO
import io.netty.handler.codec.http.{HttpResponse => JHttpResponse}
import zio.stm.TQueue

sealed trait Response[-R, +E] extends Product with Serializable

object Response extends ResponseHelpers {
  def apply[R, E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    content: HttpData[R, E] = HttpData.empty,
  ): Response[R, E] = HttpResponse(status, headers, content)

  private[zhttp] final case class SocketResponse[-R, +E](socket: SocketApp[R, E] = SocketApp.empty)
      extends Response[R, E]
  private[zhttp] final case class Decode[R, E, A](decoder: Decoder[A], cb: A => Response[R, E]) extends Response[R, E]
  private[zhttp] final case class Effect[R, E, A](response: ZIO[R, E, Response[R, E]])          extends Response[R, E]
  private[zhttp] final case class FromJResponse(jRes: JHttpResponse)                            extends Response[Any, Nothing]
  private[zhttp] final case class HttpResponse[-R, +E](
    status: Status,
    headers: List[Header],
    content: HttpData[R, E],
  )                                                                                             extends Response[R, E]
      with HasHeaders
      with HeadersHelpers

  private[zhttp] sealed trait Decoder[+A]
  private[zhttp] object Decoder {
    case class Buffered(size: Int) extends Decoder[TQueue[HBuf1[Direction.In]]]
    case class Complete(size: Int) extends Decoder[HBuf1[Direction.In]]
  }
}
