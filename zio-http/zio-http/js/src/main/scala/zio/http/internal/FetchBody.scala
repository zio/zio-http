package zio.http.internal

import scala.scalajs.js.typedarray.Uint8Array

import zio._

import zio.stream.ZStream

import zio.http._

import org.scalajs.dom.ReadableStream

case class FetchBody(
  content: ReadableStream[Uint8Array],
  contentType: Option[Body.ContentType],
) extends Body {

  /**
   * Returns an effect that decodes the content of the body as array of bytes.
   * Note that attempting to decode a large stream of bytes into an array could
   * result in an out of memory error.
   */
  override def asArray(implicit trace: Trace): Task[Array[Byte]] =
    ZIO.fromFuture { implicit ec =>
      content.getReader().read().toFuture.map { value =>
        value.value.map(_.toByte).toArray
      }
    }

  /**
   * Returns an effect that decodes the content of the body as a chunk of bytes.
   * Note that attempting to decode a large stream of bytes into a chunk could
   * result in an out of memory error.
   */
  override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] =
    asArray.map(Chunk.fromArray)

  /**
   * Returns a stream that contains the bytes of the body. This method is safe
   * to use with large bodies, because the elements of the returned stream are
   * lazily produced from the body.
   */
  override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
    ZStream.fromIterableZIO(asChunk)

  /**
   * Returns whether or not the bytes of the body have been fully read.
   */
  override def isComplete: Boolean =
    false // Seems to not be possible to check this with Fetch API

  /**
   * Returns whether or not the content length is known
   */
  override def knownContentLength: Option[Long] = None

  /**
   * Returns whether or not the body is known to be empty. Note that some bodies
   * may not be known to be empty until an attempt is made to consume them.
   */
  override def isEmpty: Boolean = false

  /**
   * Updates the media type attached to this body, returning a new Body with the
   * updated media type
   */
  override def contentType(newContentType: Body.ContentType): Body = copy(contentType = Some(newContentType))
}

object FetchBody {

  def fromResponse(result: org.scalajs.dom.Response, contentType: Option[Body.ContentType]): Body = {
    FetchBody(result.body, contentType)
  }
}
