package zio.http.model.headers.values

import zio.Chunk
import zio.http.model.MediaType

sealed trait AcceptPatch

object AcceptPatch {

  case class AcceptPatchValue(mediaTypes: Chunk[MediaType]) extends AcceptPatch

  case object InvalidAcceptPatchValue extends AcceptPatch

  def fromAcceptPatch(acceptPatch: AcceptPatch): String = acceptPatch match {
    case AcceptPatchValue(mediaTypes) => mediaTypes.map(_.fullType).mkString(",")
    case InvalidAcceptPatchValue      => ""
  }

  def toAcceptPatch(value: String): AcceptPatch = {
    if (value.nonEmpty) {
      val parsedMediaTypes = Chunk.fromArray(
        value
          .split(",")
          .map(mediaTypeStr =>
            MediaType
              .forContentType(mediaTypeStr)
              .getOrElse(
                MediaType
                  .parseCustomMediaType(mediaTypeStr)
                  .orNull
              )
          )
      )
      if (parsedMediaTypes.length == parsedMediaTypes.count(_ != null))
        AcceptPatchValue(parsedMediaTypes)
      else
        InvalidAcceptPatchValue
    } else InvalidAcceptPatchValue
  }

}
