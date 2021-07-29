package zhttp.socket

import io.netty.handler.codec.http.websocketx._
import zhttp.core.ByteBuf

sealed trait HWebSocketFrame extends Product with Serializable { self =>
  def toWebSocketFrame: WebSocketFrame = HWebSocketFrame.toJFrame(self)
}
object HWebSocketFrame {

  final case class Binary(buffer: ByteBuf)                    extends HWebSocketFrame
  final case class Text(text: String)                         extends HWebSocketFrame
  final case class Close(status: Int, reason: Option[String]) extends HWebSocketFrame
  case object Ping                                            extends HWebSocketFrame
  case object Pong                                            extends HWebSocketFrame
  final case class Continuation(buffer: ByteBuf)              extends HWebSocketFrame

  def text(string: String): HWebSocketFrame =
    HWebSocketFrame.Text(string)

  def close(status: Int, reason: Option[String] = None): HWebSocketFrame =
    HWebSocketFrame.Close(status, reason)

  def binary(chunks: ByteBuf): HWebSocketFrame = HWebSocketFrame.Binary(chunks)

  def ping: HWebSocketFrame = HWebSocketFrame.Ping

  def pong: HWebSocketFrame = HWebSocketFrame.Pong

  def continuation(chunks: ByteBuf): HWebSocketFrame = HWebSocketFrame.Continuation(chunks)

  def fromJFrame(jFrame: WebSocketFrame): Option[HWebSocketFrame] =
    jFrame match {
      case _: PingWebSocketFrame         =>
        Option(Ping)
      case _: PongWebSocketFrame         =>
        Option(Pong)
      case m: BinaryWebSocketFrame       =>
        Option(Binary(ByteBuf(m.content())))
      case m: TextWebSocketFrame         =>
        Option(Text(m.text()))
      case m: CloseWebSocketFrame        =>
        Option(Close(m.statusCode(), Option(m.reasonText())))
      case m: ContinuationWebSocketFrame =>
        Option(Continuation(ByteBuf(m.content())))

      case _ => None
    }

  def toJFrame(frame: HWebSocketFrame): WebSocketFrame =
    frame match {
      case Binary(buffer)            =>
        new BinaryWebSocketFrame(buffer.asJava)
      case Text(text)                =>
        new TextWebSocketFrame(text)
      case Close(status, Some(text)) =>
        new CloseWebSocketFrame(status, text)
      case Close(status, None)       =>
        new CloseWebSocketFrame(status, null)
      case Ping                      =>
        new PingWebSocketFrame()
      case Pong                      =>
        new PongWebSocketFrame()
      case Continuation(buffer)      =>
        new ContinuationWebSocketFrame(buffer.asJava)
    }
}
