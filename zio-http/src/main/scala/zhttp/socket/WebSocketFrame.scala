package zhttp.socket

import zhttp.core.{ByteBuf, _}

sealed trait WebSocketFrame extends Product with Serializable { self =>
  def toJWebSocketFrame: JWebSocketFrame = WebSocketFrame.toJFrame(self)
}
object WebSocketFrame {

  final case class Binary(buffer: ByteBuf)                    extends WebSocketFrame
  final case class Text(text: String)                         extends WebSocketFrame
  final case class Close(status: Int, reason: Option[String]) extends WebSocketFrame
  final case object Ping                                      extends WebSocketFrame
  final case object Pong                                      extends WebSocketFrame
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
      case _: JPingWebSocketFrame         =>
        Option(Ping)
      case _: JPongWebSocketFrame         =>
        Option(Pong)
      case m: JBinaryWebSocketFrame       =>
        Option(Binary(ByteBuf(m.content())))
      case m: JTextWebSocketFrame         =>
        Option(Text(m.text()))
      case m: JCloseWebSocketFrame        =>
        Option(Close(m.statusCode(), Option(m.reasonText())))
      case m: JContinuationWebSocketFrame =>
        Option(Continuation(ByteBuf(m.content())))

      case _ => None
    }

  def toJFrame(frame: WebSocketFrame): JWebSocketFrame =
    frame match {
      case Binary(buffer)            =>
        new JBinaryWebSocketFrame(buffer.asJava)
      case Text(text)                =>
        new JTextWebSocketFrame(text)
      case Close(status, Some(text)) =>
        new JCloseWebSocketFrame(status, text)
      case Close(status, None)       =>
        new JCloseWebSocketFrame(status, null)
      case Ping                      =>
        new JPingWebSocketFrame()
      case Pong                      =>
        new JPongWebSocketFrame()
      case Continuation(buffer)      =>
        new JContinuationWebSocketFrame(buffer.asJava)
    }
}
