package zio.http.internal

import zio._

import zio.stream.ZStream

import zio.http._

// A batched (fully materialized) body for the JS Fetch client. The bytes are
// eagerly fetched in the driver before constructing this instance, so all
// decoding operations are pure and do not attempt to re-consume the underlying
// Fetch Response body (which can only be consumed once).
private[http] final case class FetchBodyBatched(
  data: Array[Byte],
  contentType: Option[Body.ContentType],
  contentLength: Option[Header.ContentLength],
) extends Body {

  override def asArray(implicit trace: Trace): Task[Array[Byte]] = ZIO.succeed(data)

  override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] = ZIO.succeed(Chunk.fromArray(data))

  override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
    ZStream.fromIterable(data)

  override def isComplete: Boolean = true

  override def knownContentLength: Option[Long] = contentLength.map(_.length).orElse(Some(data.length.toLong))

  override def isEmpty: Boolean = data.isEmpty || contentLength.exists(_.length == 0)

  override def contentType(newContentType: Body.ContentType): Body = copy(contentType = Some(newContentType))
}

private[http] object FetchBodyBatched {
  def apply(data: Array[Byte], ct: Option[Body.ContentType], cl: Option[Header.ContentLength]): FetchBodyBatched =
    new FetchBodyBatched(data, ct, cl)
}
