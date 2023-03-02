package zio.http.model

import scala.annotation.tailrec

import zio.stacktracer.TracingImplicits.disableAutoTrace

final case class MediaType(
  mainType: String,
  subType: String,
  compressible: Boolean = false,
  binary: Boolean = false,
  fileExtensions: List[String] = Nil,
  extensions: Map[String, String] = Map.empty,
  parameters: Map[String, String] = Map.empty,
) {
  def fullType: String = s"$mainType/$subType"
}

object MediaType extends MimeDB {
  private val extensionMap: Map[String, MediaType]   = allMediaTypes.flatMap(m => m.fileExtensions.map(_ -> m)).toMap
  private val contentTypeMap: Map[String, MediaType] = allMediaTypes.map(m => m.fullType -> m).toMap

  def forContentType(contentType: String): Option[MediaType] = {
    val index = contentType.indexOf(";")
    if (index == -1)
      contentTypeMap.get(contentType)
    else {
      val (contentType1, parameter) = contentType.splitAt(index)
      contentTypeMap
        .get(contentType1)
        .map(_.copy(parameters = parseOptionalParameters(parameter.tail.split(";"))))
    }
  }

  def forFileExtension(ext: String): Option[MediaType] = extensionMap.get(ext)

  def parseCustomMediaType(customMediaType: String): Option[MediaType] = {
    val contentTypeParts = customMediaType.split('/')
    if (contentTypeParts.length == 2) {
      val subtypeParts = contentTypeParts(1).split(';')
      if (subtypeParts.length >= 1) {
        Some(
          MediaType(
            mainType = contentTypeParts.head,
            subType = subtypeParts.head,
            parameters = if (subtypeParts.length >= 2) parseOptionalParameters(subtypeParts.tail) else Map.empty,
          ),
        )
      } else None
    } else None
  }

  private def parseOptionalParameters(parameters: Array[String]): Map[String, String] = {
    @tailrec
    def loop(parameters: Seq[String], parameterMap: Map[String, String]): Map[String, String] = parameters match {
      case Seq(parameter, tail @ _*) =>
        val parameterParts = parameter.split("=")
        val newMap         =
          if (parameterParts.length == 2) parameterMap + (parameterParts.head -> parameterParts(1))
          else parameterMap
        loop(tail, newMap)
      case _                         => parameterMap
    }

    loop(parameters.toIndexedSeq, Map.empty)
  }
}
