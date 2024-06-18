package zio.http.tools

import zio._
import zio.json._
import scala.io.Source

/////////////////////////
// READING AND PARSING //
/////////////////////////

case class MimeType(
  source: Option[String],
  extensions: Option[List[String]] = None,
  compressible: Option[Boolean] = None,
  charset: Option[String] = None,
)

object MimeType {
  implicit val decoder: JsonDecoder[MimeType] = DeriveJsonDecoder.gen[MimeType]
}

case class MimeDb(mimeTypes: Map[String, MimeType]) extends AnyVal {
  def extend(extraTypes: Map[String, MimeType]): MimeDb = {
    MimeDb(mimeTypes ++ extraTypes)
  }
}

object MimeDb {
  implicit val decoder: JsonDecoder[MimeDb] = JsonDecoder.map[String, MimeType].map(MimeDb(_))

  //     lazy val `event-stream`: MediaType             = new MediaType("text", "event-stream", Compressible, NotBinary)
  val extraTypes = Map(
    "text/event-stream" -> MimeType(None, None, Some(true), None),
  )

  /**
   * Fetches the MIME types database from the jshttp/mime-db repository and
   * returns a MimeDb object
   */
  def fetch = {
    val url = "https://raw.githubusercontent.com/jshttp/mime-db/master/db.json"

    val source   = Source.fromURL(url)
    val jsonData =
      try { source.mkString }
      finally { source.close() }

    jsonData.fromJson[MimeDb] match {
      case Right(db)   => db.extend(extraTypes)
      case Left(error) => throw new RuntimeException(s"Failed to parse JSON: $error")
    }
  }
}

///////////////
// RENDERING //
///////////////

object RenderUtils {
  def snakeCase(s: String): String = {
    s.toLowerCase.replace("-", "_")
  }

  // hello -> "hello"
  def renderString(s: String): String = {
    "\"" + s + "\""
  }

  // hello there -> `hello there`
  def renderEscaped(s: String): String = {
    "`" + s + "`"
  }
}

case class MediaTypeInfo(
  mainType: String,
  subType: String,
  compressible: Boolean,
  extensions: List[String],
) {

  def binary: Boolean = {
    // If the main type is "image", "video", "audio", or "application" (excluding text-based applications), it is likely binary.
    // Additionally, if the MIME type is not compressible, it is likely binary.
    mainType match {
      case "image" | "video" | "audio" => true
      case "application" => !subType.startsWith("xml") && !subType.endsWith("json") && !subType.endsWith("javascript")
      case _             => !compressible
    }
  }

  /**
   * Renders the media type info as a Scala code snippet
   *
   * {{{
   * lazy val `${subType}`: MediaType =
   *   new MediaType("$mainType", "$subType", compressible = $compressible$extensionsString)
   * }}}
   */
  def render: String = {
    val extensionsString =
      if (extensions.isEmpty) ""
      else s", ${extensions.map { string => s""""$string"""" }.mkString("List(", ", ", ")")}"

    s"""
lazy val `${subType}`: MediaType =
  new MediaType("$mainType", "$subType", compressible = $compressible, binary = $binary$extensionsString)
    """
  }
}

object MediaTypeInfo {
  def fromMimeDb(mimeDb: MimeDb): List[MediaTypeInfo] = {
    mimeDb.mimeTypes.map { case (mimeType, details) =>
      val Array(mainType, subType) = mimeType.split('/')
      MediaTypeInfo(mainType, subType, details.compressible.getOrElse(false), details.extensions.getOrElse(List.empty))
    }.toList
  }
}

/**
 * Renders a group of media types as a Scala code snippet
 *
 * {{{
 * object <mainType> {
 *   <render each subType>
 *   lazy val all: List[MediaType] = List(<subTypes>)
 *   lazy val any: MediaType       = new MediaType("$mainType", "*")
 * }}}
 */
case class MediaTypeGroup(
  mainType: String,
  subTypes: List[MediaTypeInfo],
) {
  def render: String = {

    s"""
object ${RenderUtils.snakeCase(mainType)} {
  ${subTypes.map(_.render).mkString("\n")}
  lazy val all: List[MediaType] = List(${subTypes.map(t => RenderUtils.renderEscaped(t.subType)).mkString(", ")})
  lazy val any: MediaType       = new MediaType("$mainType", "*")
}
    """
  }
}

object GenerateMediaTypes extends ZIOAppDefault {
  val run = {
    val mediaTypes      = MediaTypeInfo.fromMimeDb(MimeDb.fetch)
    val mediaTypeGroups =
      mediaTypes
        .groupBy(_.mainType)
        .map { case (mainType, subTypes) =>
          MediaTypeGroup(mainType, subTypes)
        }
        .toList

    val file           = MediaTypeFile(mediaTypeGroups)
    val mediaTypesPath = "../zio-http/shared/src/main/scala/zio/http/MediaTypes.scala"
    ZIO.writeFile(mediaTypesPath, file.render)
  }
}

/**
 * Renders a list of media type groups as a Scala code snippet
 *
 * {{{
 * package zio.http
 *
 * private[zio] trait MediaTypesGenerated {
 *   lazy val allMediaTypes: List[MediaType] =
 *     group_1.all ++ group_2.all ++ group_3.all
 *   lazy val any: MediaType = new MediaType("*", "*")
 *
 *   <render each group>
 * }}}
 */
case class MediaTypeFile(
  groups: List[MediaTypeGroup],
) {
  def render: String = {
    s"""
// ⚠️ HEY YOU! IMPORTANT MESSAGE ⚠️
// ==============================
//
// THIS FILE IS AUTOMATICALLY GENERATED BY `GenerateMediaTypes.scala`
// So don't go editing it now, you hear? Otherwise your changes will 
// be overwritten the next time someone runs `sbt generateMediaTypes`

package zio.http

private[zio] trait MediaTypes {
  private[zio] lazy val allMediaTypes: List[MediaType] =
    ${groups.map(main => s"${RenderUtils.snakeCase(main.mainType)}.all").mkString(" ++ ")}
  lazy val any: MediaType = new MediaType("*", "*")

${groups.map(_.render).mkString("\n\n")}
}
    """
  }
}
