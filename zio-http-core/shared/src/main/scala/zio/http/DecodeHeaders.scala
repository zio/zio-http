package zio.http

import zio.http.schema.HeaderCodec

final case class DecodeHeaders[A](value: A) extends AnyVal

object DecodeHeaders {
  def decode[A](request: Request)(implicit headerCodec: HeaderCodec[A]): Either[Halt, DecodeHeaders[A]] =
    headerCodec
      .decode(request.headers)
      .map(DecodeHeaders(_))
      .left
      .map(error => Halt(Response(Status.BadRequest, body = Body.fromString(error.message))))
}
