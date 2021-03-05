package zhttp.socket

import scala.annotation.{implicitAmbiguous, implicitNotFound}

@implicitNotFound(
  "Your socket of type Socket[${R}, ${E}, ${A}, ${B}] could not be converted into a Http Response because" +
    " it's not a sub type of Socket[Any, SocketError, WebSocketFrame, WebSocketFrame]",
)
sealed trait IsResponse[+R, -E, +A, -B] {
  def apply[R1 >: R, E1 <: E, A1 >: A, B1 <: B](
    socket: Socket[R1, E1, A1, B1],
  ): Socket[R1, E1, WebSocketFrame, WebSocketFrame]
}
object IsResponse extends IsResponse[Nothing, Any, Nothing, Any] {

  implicit def isResponse[R, E <: Throwable, A >: WebSocketFrame, B <: WebSocketFrame]: IsResponse[R, E, A, B] =
    IsResponse

  @implicitAmbiguous(
    "Your socket could not be converted into a Http Response because it's not a sub type of Socket[Any, Throwable, WebSocketFrame, WebSocketFrame]",
  )
  implicit def isResponse0[R, E <: Throwable]: IsResponse[R, E, Any, Nothing] = IsResponse

  override def apply[R1 >: Nothing, E1 <: Any, A1 >: Nothing, B1 <: Any](
    socket: Socket[R1, E1, A1, B1],
  ): Socket[R1, E1, WebSocketFrame, WebSocketFrame] =
    socket.asInstanceOf[Socket[R1, E1, WebSocketFrame, WebSocketFrame]]
}
