package zio.http.internal

import scala.scalajs.js.typedarray.Uint8Array

import zio._

import zio.stream.ZStream

import zio.http._

import org.scalajs.dom.ReadableStream

/**
 * A [[Body]] over the `ReadableStream` of a Fetch API response, used by the
 * Scala.js client.
 */
@deprecated("use FetchBodyInternal", "3.6.0")
case class FetchBody(
  content: ReadableStream[Uint8Array],
  contentType: Option[Body.ContentType],
) extends Body {

  /**
   * Returns an effect that reads one chunk from the stream's reader and returns
   * its bytes as an array; content in later chunks is not read. Note that
   * attempting to decode a large stream of bytes into an array could result in
   * an out of memory error.
   */
  override def asArray(implicit trace: Trace): Task[Array[Byte]] =
    ZIO.fromFuture { implicit ec =>
      content.getReader().read().toFuture.map { value =>
        value.value.map(_.toByte).toArray
      }
    }

  /**
   * Returns an effect that reads the bytes as a chunk, in the way of `asArray`.
   * Note that attempting to decode a large stream of bytes into a chunk could
   * result in an out of memory error.
   */
  override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] =
    asArray.map(Chunk.fromArray)

  /**
   * Returns a stream that emits the bytes that `asChunk` reads when the stream
   * runs.
   */
  override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
    ZStream.fromIterableZIO(asChunk)

  /**
   * Returns `false`; the Fetch API offers no check for whether the stream has
   * been read fully.
   */
  override def isComplete: Boolean =
    false // Seems to not be possible to check this with Fetch API

  /**
   * Returns `None`; the content length is not known for this body.
   */
  override def knownContentLength: Option[Long] = None

  /**
   * Returns `false`, whether or not the stream has content.
   */
  override def isEmpty: Boolean = false

  /**
   * Updates the media type attached to this body, returning a new Body with the
   * updated media type
   */
  override def contentType(newContentType: Body.ContentType): Body = copy(contentType = Some(newContentType))
}

/** Companion of [[FetchBody]]. */
object FetchBody {

  /**
   * Returns a [[Body]] that streams the bytes of the response, taking the
   * content length from its `Content-Length` header when that header parses.
   */
  def fromResponse(result: org.scalajs.dom.Response, contentType: Option[Body.ContentType]): Body =
    FetchBodyInternal.fromResponse(result, contentType)

}
