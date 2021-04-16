package zhttp.socket

import zio.json.JsonDecoder

trait SocketDecoder[E, A] {
  def decode(a: WebSocketFrame): Either[E, A]
}

object SocketDecoder {
  trait Impl {
    implicit object WebSocketFrameDecoder extends SocketDecoder[Nothing, WebSocketFrame] {
      override def decode(a: WebSocketFrame): Either[Nothing, WebSocketFrame] = Right(a)
    }

    implicit object WebSocketTextFrameDecoder extends SocketDecoder[Throwable, String] {
      override def decode(a: WebSocketFrame): Either[Throwable, String] =
        a match {
          case WebSocketFrame.Text(text) => Right(text)
          case message                   => Left(DecodingError(message))
        }
    }

    implicit def fromJsonDecoder[A](implicit decoder: JsonDecoder[A]): SocketDecoder[Throwable, A] =
      new SocketDecoder[Throwable, A] {
        override def decode(a: WebSocketFrame): Either[Throwable, A] =
          a match {
            case m @ WebSocketFrame.Text(text) =>
              decoder.decodeJson(text) match {
                case Right(a)    => Right(a)
                case Left(error) => Left(DecodingError(m, Option(error)))
              }
            case message                       => Left(DecodingError(message))
          }
      }
  }

}
