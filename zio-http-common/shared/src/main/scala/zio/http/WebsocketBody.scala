package zio.http

import zio._
import zio.stream.ZStream

private[zio] final case class WebsocketBody(socketApp: WebSocketApp[Any]) extends Body {
  def asArray(implicit trace: Trace): Task[Array[Byte]] =
    Body.zioEmptyArray

  def asChunk(implicit trace: Trace): Task[Chunk[Byte]] =
    Body.zioEmptyChunk

  def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
    ZStream.empty

  private[zio] def boundary: Option[Boundary] = None

  def isComplete: Boolean = true

  def isEmpty: Boolean = true

  def mediaType: Option[MediaType] = None

  def contentType(newMediaType: zio.http.MediaType): zio.http.Body = this

  def contentType(newMediaType: zio.http.MediaType, newBoundary: zio.http.Boundary): zio.http.Body = this

  override def knownContentLength: Option[Long] = Some(0L)

}
