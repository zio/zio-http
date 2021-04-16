package zhttp.socket

import zio.json.JsonEncoder

trait SocketEncoder[A] {
  def encode(a: A): WebSocketFrame
}

object SocketEncoder {
  trait Impl {
    implicit object WebSocketFrameEncoder extends SocketEncoder[WebSocketFrame] {
      override def encode(a: WebSocketFrame): WebSocketFrame = a
    }

    implicit object WebSocketTextFrameEncoder extends SocketEncoder[String] {
      override def encode(a: String): WebSocketFrame = WebSocketFrame.text(a)
    }

    implicit def fromJsonEncoder[A](implicit encoder: JsonEncoder[A]): SocketEncoder[A] =
      new SocketEncoder[A] {
        override def encode(a: A): WebSocketFrame =
          WebSocketFrame.text(encoder.encodeJson(a, None).toString())
      }
  }
}
