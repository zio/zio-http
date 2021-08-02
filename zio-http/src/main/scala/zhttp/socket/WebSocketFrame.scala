package zhttp.socket

import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, _}
import zhttp.core.ByteBuf

sealed trait WebSocketFrame extends Product with Serializable { self =>
  def toWebSocketFrame: JWebSocketFrame = WebSocketFrame.toJFrame(self)
}
object WebSocketFrame {

  final case class Binary(buffer: ByteBuf)                    extends WebSocketFrame
  final case class Text(text: String)                         extends WebSocketFrame
  final case class Close(status: Int, reason: Option[String]) extends WebSocketFrame
  case object Ping                                            extends WebSocketFrame
  case object Pong                                            extends WebSocketFrame
  final case class Continuation(buffer: ByteBuf)              extends WebSocketFrame

  def text(string: String): WebSocketFrame =
    WebSocketFrame.Text(string)

  def close(status: Int, reason: Option[String] = None): WebSocketFrame =
    WebSocketFrame.Close(status, reason)

  def binary(chunks: ByteBuf): WebSocketFrame = WebSocketFrame.Binary(chunks)

  def ping: WebSocketFrame = WebSocketFrame.Ping

  def pong: WebSocketFrame = WebSocketFrame.Pong

  def continuation(chunks: ByteBuf): WebSocketFrame = WebSocketFrame.Continuation(chunks)

  def fromJFrame(jFrame: JWebSocketFrame): Option[WebSocketFrame] =
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

  def toJFrame(frame: WebSocketFrame): JWebSocketFrame =
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
