package zio.http.model.headers.values

import zio.Chunk
import zio.http.model.MediaType

import scala.util.Try

/** Accept header value. */
sealed trait Accept

object Accept {

  /**
   * The Accept header value one or more MIME types optionally weighed with
   * quality factor.
   */
  final case class AcceptValue(mimeTypes: Chunk[MediaTypeWithQFactor]) extends Accept

  final case class MediaTypeWithQFactor(mediaType: MediaType, qFactor: Option[Double])

  /** The Accept header value is invalid. */
  case object InvalidAcceptValue extends Accept

  def fromAccept(header: Accept): Chunk[(String, Double)] = header match {
    case AcceptValue(mimeTypes) =>
      mimeTypes.map { case MediaTypeWithQFactor(mime, maybeQFactor) =>
        (mime.toString, maybeQFactor.getOrElse(1.0))
      }
    case InvalidAcceptValue     => Chunk.empty
  }

  def toAccept(
    values: Chunk[(String, Double)],
  ): Accept = {
    val acceptHeaderValues: Chunk[MediaTypeWithQFactor] = values.map { subValue =>
      MediaType
        .forContentType(subValue._1)
        .map(mt => MediaTypeWithQFactor(mt, extractQFactor(mt)))
        .getOrElse {
          MediaType
            .parseCustomMediaType(subValue._1)
            .map(mt => MediaTypeWithQFactor(mt, extractQFactor(mt)))
            .orNull
        }
    }

    if (acceptHeaderValues.nonEmpty && acceptHeaderValues.length == acceptHeaderValues.count(_ != null))
      AcceptValue(acceptHeaderValues)
    else InvalidAcceptValue
  }

  private def extractQFactor(mediaType: MediaType): Option[Double] =
    mediaType.parameters.get("q").flatMap(qFactor => Try(qFactor.toDouble).toOption)
}
