package zio.http.model.headers.values

import zio.http.model.MediaType

import scala.util.Try

/** Accept header value. */
sealed trait Accept

object Accept {

  /**
   * The Accept header value one or more MIME types optionally weighed with
   * quality factor.
   */
  final case class AcceptValue(mimeTypes: List[MediaTypeWithQFactor]) extends Accept

  final case class MediaTypeWithQFactor(mediaType: MediaType, qFactor: Option[Double])

  /** The Accept header value is invalid. */
  case object InvalidAcceptValue extends Accept

  def fromAccept(header: Accept): String = header match {
    case AcceptValue(mimeTypes) =>
      mimeTypes.map { case MediaTypeWithQFactor(mime, maybeQFactor) =>
        s"${mime.fullType}${maybeQFactor.map(qFactor => s";q=$qFactor").getOrElse("")}"
      }.mkString(", ")
    case InvalidAcceptValue     => ""
  }

  def toAccept(value: String): Accept = {
    val acceptHeaderValues: Array[MediaTypeWithQFactor] = value
      .split(',')
      .map(_.trim)
      .map { subvalue =>
        val parts = subvalue.split(';')
        if (parts.length >= 1) {
          val qFactor = if (parts.length == 2) {
            val qFactorParts = parts.tail.head.split('=')
            if (qFactorParts.length == 2) {
              Try(qFactorParts.tail.head.toDouble).toOption
            } else None
          } else None

          MediaType
            .forContentType(parts.head)
            .map(MediaTypeWithQFactor(_, qFactor))
            .getOrElse {
              val mediaTypeParts = parts.head.split('/')
              if (mediaTypeParts.length == 2) {
                MediaTypeWithQFactor(MediaType(mediaTypeParts.head, mediaTypeParts.tail.head), qFactor)
              } else null
            }
        } else null
      }

    if (acceptHeaderValues.nonEmpty && acceptHeaderValues.length == acceptHeaderValues.count(_ != null))
      AcceptValue(acceptHeaderValues.toList)
    else InvalidAcceptValue
  }
}
