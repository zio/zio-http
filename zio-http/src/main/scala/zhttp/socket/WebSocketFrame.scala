package zhttp.socket

import zhttp.core.{HBuf, _}

sealed trait WebSocketFrame extends Product with Serializable { self =>
  def toJWebSocketFrame: JWebSocketFrame = WebSocketFrame.toJFrame(self)
}
object WebSocketFrame {
  final case class Binary(buffer: HBuf[Nat.One, Direction.Out])       extends WebSocketFrame
  final case class Text(text: String)                                 extends WebSocketFrame
  final case class Close(status: Int, reason: Option[String])         extends WebSocketFrame
  case object Ping                                                    extends WebSocketFrame
  case object Pong                                                    extends WebSocketFrame
  final case class Continuation(buffer: HBuf[Nat.One, Direction.Out]) extends WebSocketFrame

  def text(string: String): WebSocketFrame =
    WebSocketFrame.Text(string)

  def close(status: Int, reason: Option[String] = None): WebSocketFrame =
    WebSocketFrame.Close(status, reason)

  def binary(buffer: HBuf[Nat.One, Direction.In]): WebSocketFrame = WebSocketFrame.Binary(buffer.flip)

  def ping: WebSocketFrame = WebSocketFrame.Ping

  def pong: WebSocketFrame = WebSocketFrame.Pong

  def continuation(buffer: HBuf[Nat.One, Direction.In]): WebSocketFrame = WebSocketFrame.Continuation(buffer.flip)

  def fromJFrame(jFrame: JWebSocketFrame): WebSocketFrame =
    jFrame match {
      case _: JPingWebSocketFrame         =>
        Ping
      case _: JPongWebSocketFrame         =>
        Pong
      case m: JBinaryWebSocketFrame       =>
        Binary(HBuf.one(m.content()))
      case m: JTextWebSocketFrame         =>
        Text(m.text())
      case m: JCloseWebSocketFrame        =>
        Close(m.statusCode(), Option(m.reasonText()))
      case m: JContinuationWebSocketFrame =>
        Continuation(HBuf.one(m.content()))
      case _                              => throw new Error(s"WebSocketFrame could not be made from: ${jFrame}")
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
