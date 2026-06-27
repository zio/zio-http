package zio.http

import zio.http.schema.QueryCodec

final case class DecodeQuery[A](value: A) extends AnyVal

object DecodeQuery {
  def decode[A](request: Request)(implicit queryCodec: QueryCodec[A]): Either[Halt, DecodeQuery[A]] =
    queryCodec
      .decode(request.url.queryParams)
      .map(DecodeQuery(_))
      .left
      .map(error => Halt(Response(Status.BadRequest, body = Body.fromString(error.message))))
}
