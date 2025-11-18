package zio.http.internal

import zio._
import zio.stream.{ZChannel, ZStream}
import zio.http._
import org.scalajs.dom.Response

private[http] final case class FetchBodyInternal(
  result: Response,
  contentType: Option[Body.ContentType],
  contentLength: Option[Header.ContentLength],
) extends Body {

  /**
   * Returns an effect that decodes the content of the body as array of bytes.
   * Note that attempting to decode a large stream of bytes into an array could
   * result in an out of memory error.
   */
  override def asArray(implicit trace: Trace): Task[Array[Byte]] =
    asChunk.map(_.toArray)

  /**
   * Returns an effect that decodes the content of the body as a chunk of bytes.
   * Note that attempting to decode a large stream of bytes into a chunk could
   * result in an out of memory error.
   */
  override def asChunk(implicit trace: Trace): Task[Chunk[Byte]] =
    asStream.runCollect

  /**
   * Returns a stream that contains the bytes of the body. This method is safe
   * to use with large bodies, because the elements of the returned stream are
   * lazily produced from the body.
   */
  override def asStream(implicit trace: Trace): ZStream[Any, Throwable, Byte] =
    ZStream.fromZIO { ZIO.attempt { result.body.getReader() } }.flatMap { reader =>
      lazy val loop: ZChannel[Any, Any, Any, Any, Throwable, Chunk[Byte], Unit] =
        ZChannel.fromZIO { ZIO.fromPromiseJS { reader.read() } }.flatMap { result =>
          val out: Chunk[Byte] = Chunk.fromIterable(result.value.map(_.toByte))

          if (result.done) ZChannel.write(out) *> ZChannel.unit
          else ZChannel.write(out) *> loop
        }

      ZStream.fromChannel(loop)
    }

  /**
   * Returns whether or not the bytes of the body have been fully read.
   */
  override def isComplete: Boolean = result.bodyUsed

  /**
   * Returns whether or not the content length is known
   */
  override def knownContentLength: Option[Long] = contentLength.map(_.length)

  /**
   * Returns whether or not the body is known to be empty. Note that some bodies
   * may not be known to be empty until an attempt is made to consume them.
   */
  override def isEmpty: Boolean =
    contentLength match {
      case None                          => false
      case Some(Header.ContentLength(0)) => true
      case _                             => false
    }

  /**
   * Updates the media type attached to this body, returning a new Body with the
   * updated media type
   */
  override def contentType(newContentType: Body.ContentType): Body = copy(contentType = Some(newContentType))

}
private[http] object FetchBodyInternal {

  def fromResponse(result: Response, contentType: Option[Body.ContentType]): Body = {
    val contentLength =
      if (result.headers.has(Header.ContentLength.name))
        Header.ContentLength.parse(result.headers.get(Header.ContentLength.name)).toOption
      else
        None
    FetchBodyInternal(result, contentType, contentLength)
  }

}
