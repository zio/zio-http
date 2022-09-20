package zio.http.socket

import io.netty.buffer.{ByteBuf, ByteBufUtil, Unpooled}
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, _}
import zio.{Chunk, Unsafe}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

sealed trait WebSocketFrame extends Product with Serializable { self =>
  def isFinal: Boolean = true

  final def toWebSocketFrame: JWebSocketFrame = WebSocketFrame.toJFrame(self)
}

object WebSocketFrame {

  private[zio] object unsafe {
    final def fromJFrame(jFrame: JWebSocketFrame)(implicit unsafe: Unsafe): WebSocketFrame =
      jFrame match {
        case _: PingWebSocketFrame   => Ping
        case _: PongWebSocketFrame   => Pong
        case m: BinaryWebSocketFrame => Binary(Chunk.fromArray(ByteBufUtil.getBytes(m.content())), m.isFinalFragment)
        case m: TextWebSocketFrame   => Text(m.text(), m.isFinalFragment)
        case m: CloseWebSocketFrame  => Close(m.statusCode(), Option(m.reasonText()))
        case m: ContinuationWebSocketFrame => Continuation(m.content(), m.isFinalFragment)
        case _                             => null
      }
  }

  def binary(bytes: Chunk[Byte]): WebSocketFrame = WebSocketFrame.Binary(bytes)

  def close(status: Int, reason: Option[String] = None): WebSocketFrame =
    WebSocketFrame.Close(status, reason)

  def continuation(chunks: ByteBuf): WebSocketFrame = WebSocketFrame.Continuation(chunks)

  def fromJFrame(jFrame: JWebSocketFrame): Option[WebSocketFrame] =
    Option(unsafe.fromJFrame(jFrame)(Unsafe.unsafe))

  def ping: WebSocketFrame = WebSocketFrame.Ping

  def pong: WebSocketFrame = WebSocketFrame.Pong

  def text(string: String): WebSocketFrame =
    WebSocketFrame.Text(string)

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

  case class Binary(bytes: Chunk[Byte]) extends WebSocketFrame { override val isFinal: Boolean = true }

  case class Text(text: String) extends WebSocketFrame { override val isFinal: Boolean = true }

  final case class Close(status: Int, reason: Option[String]) extends WebSocketFrame

  case class Continuation(buffer: ByteBuf) extends WebSocketFrame { override val isFinal: Boolean = true }

  object Binary {
    def apply(bytes: Chunk[Byte], isFinal: Boolean): Binary        = {
      val arg = isFinal
      new Binary(bytes) { override val isFinal: Boolean = arg }
    }
    def unapply(frame: WebSocketFrame.Binary): Option[Chunk[Byte]] = Some(frame.bytes)
  }

  object Text {
    def apply(text: String, isFinal: Boolean): Text         = {
      val arg = isFinal
      new Text(text) { override val isFinal: Boolean = arg }
    }
    def unapply(frame: WebSocketFrame.Text): Option[String] = Some(frame.text)
  }

  case object Ping extends WebSocketFrame

  case object Pong extends WebSocketFrame

  object Continuation {
    def apply(buffer: ByteBuf, isFinal: Boolean): Continuation       = {
      val arg = isFinal
      new Continuation(buffer) { override val isFinal: Boolean = arg }
    }
    def unapply(frame: WebSocketFrame.Continuation): Option[ByteBuf] = Some(frame.buffer)
  }
}
