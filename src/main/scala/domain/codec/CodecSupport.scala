package zio-http.domain.codec

import zio-http.domain.http.Http
import zio-http.domain.socket.Socket
import zio.stream.ZStream

sealed trait CodecSupport[F[-_, +_, -_, +_]] {
  def codec[R, E, A, B, C, D](f: F[R, E, B, C], codec: Codec[R, E, A, B, C, D]): F[R, E, A, D]
}

object CodecSupport {
  implicit object HttpCodecSupport extends CodecSupport[Http] {
    override def codec[R, E, A, B, C, D](f: Http[R, E, B, C], codec: Codec[R, E, A, B, C, D]): Http[R, E, A, D] =
      f.cmapM(codec.decode[A]).mapM(codec.encode(_))
  }

  implicit object SocketCodecSupport extends CodecSupport[Socket] {
    override def codec[R, E, A, B, C, D](
      f: Socket[R, E, B, C],
      codec: Codec[R, E, A, B, C, D],
    ): Socket[R, E, A, D] =
      Socket(a0 => ZStream.unwrap(codec.decode(a0).map(a => f(a).mapM(codec.encode(_)))))
  }
}
