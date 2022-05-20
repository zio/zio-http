package zhttp.socket

import io.netty.buffer.{ByteBuf, ByteBufUtil, Unpooled}
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, _}
import zio.Chunk

sealed trait WebSocketFrame extends Product with Serializable { self =>
  final def toWebSocketFrame: JWebSocketFrame = WebSocketFrame.toJFrame(self)
  def isFinal: Boolean                        = true
}

object WebSocketFrame {

  case class Binary(bytes: Chunk[Byte]) extends WebSocketFrame { override val isFinal: Boolean = true }
  object Binary {
    def apply(bytes: Chunk[Byte], isFinal: Boolean): Binary        = {
      val arg = isFinal
      new Binary(bytes) { override val isFinal: Boolean = arg }
    }
    def unapply(frame: WebSocketFrame.Binary): Option[Chunk[Byte]] = Some(frame.bytes)
  }

  case class Text(text: String) extends WebSocketFrame { override val isFinal: Boolean = true }
  object Text {
    def apply(text: String, isFinal: Boolean): Text         = {
      val arg = isFinal
      new Text(text) { override val isFinal: Boolean = arg }
    }
    def unapply(frame: WebSocketFrame.Text): Option[String] = Some(frame.text)
  }

  final case class Close(status: Int, reason: Option[String]) extends WebSocketFrame

  case object Ping extends WebSocketFrame
  case object Pong extends WebSocketFrame

  case class Continuation(buffer: ByteBuf) extends WebSocketFrame { override val isFinal: Boolean = true }
  object Continuation {
    def apply(buffer: ByteBuf, isFinal: Boolean): Continuation       = {
      val arg = isFinal
      new Continuation(buffer) { override val isFinal: Boolean = arg }
    }
    def unapply(frame: WebSocketFrame.Continuation): Option[ByteBuf] = Some(frame.buffer)
  }

  def text(string: String): WebSocketFrame =
    WebSocketFrame.Text(string)

  def close(status: Int, reason: Option[String] = None): WebSocketFrame =
    WebSocketFrame.Close(status, reason)

  def binary(bytes: Chunk[Byte]): WebSocketFrame = WebSocketFrame.Binary(bytes)

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
        Option(Binary(Chunk.fromArray(ByteBufUtil.getBytes(m.content())), m.isFinalFragment))
      case m: TextWebSocketFrame         =>
        Option(Text(m.text(), m.isFinalFragment))
      case m: CloseWebSocketFrame        =>
        Option(Close(m.statusCode(), Option(m.reasonText())))
      case m: ContinuationWebSocketFrame =>
        Option(Continuation(m.content(), m.isFinalFragment))

      case _ => None
    }

  def toJFrame(frame: WebSocketFrame): JWebSocketFrame =
    frame match {
      case b: Binary                 =>
        new BinaryWebSocketFrame(b.isFinal, 0, Unpooled.wrappedBuffer(b.bytes.toArray))
      case t: Text                   =>
        new TextWebSocketFrame(t.isFinal, 0, t.text)
      case Close(status, Some(text)) =>
        new CloseWebSocketFrame(status, text)
      case Close(status, None)       =>
        new CloseWebSocketFrame(status, null)
      case Ping                      =>
        new PingWebSocketFrame()
      case Pong                      =>
        new PongWebSocketFrame()
      case c: Continuation           =>
        new ContinuationWebSocketFrame(c.isFinal, 0, c.buffer)
    }
}
