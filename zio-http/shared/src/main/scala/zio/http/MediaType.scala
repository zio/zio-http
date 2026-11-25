/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import zio.schema.Schema

/**
 * A media type, such as `text/html`, with its parameters. The predefined media
 * types are members of the companion object, grouped by main type, for example
 * `MediaType.text.html` and `MediaType.application.json`.
 *
 * {{{
 * val html   = MediaType.forFileExtension("html") // Some(MediaType.text.html)
 * val json   = MediaType.forContentType("application/json; charset=utf-8")
 * val plain  = MediaType.text.plain.matches(MediaType.text.any) // true
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/headers/ Headers]]
 * @param mainType
 *   the part before the `/`, such as `text`
 * @param subType
 *   the part after the `/`, such as `html`
 * @param compressible
 *   whether content of this type is usually compressible
 * @param binary
 *   whether content of this type is treated as binary rather than text
 * @param fileExtensions
 *   the file extensions of this type, without the leading dot
 * @param extensions
 *   extra entries attached to the media type, empty for the predefined types
 * @param parameters
 *   the parameters of the media type, such as `charset`, with lowercase names
 */
final case class MediaType(
  mainType: String,
  subType: String,
  compressible: Boolean = false,
  binary: Boolean = false,
  fileExtensions: List[String] = Nil,
  extensions: Map[String, String] = Map.empty,
  parameters: Map[String, String] = Map.empty,
) {

  /** The `mainType` and the `subType` joined with `/`, without parameters. */
  val fullType: String = s"$mainType/$subType"

  /**
   * Returns `true` when this media type matches `other`. The main types match
   * when they are equal ignoring case or when either one is `*`, and the same
   * rule applies to the sub types. Unless `ignoreParameters` is `true`, every
   * parameter of this media type must also be in `other` with the same value,
   * so the result can differ when the two are swapped.
   */
  def matches(other: MediaType, ignoreParameters: Boolean = false): Boolean =
    (mainType == "*" || other.mainType == "*" || mainType.equalsIgnoreCase(other.mainType)) &&
      (subType == "*" || other.subType == "*" || subType.equalsIgnoreCase(other.subType)) &&
      (ignoreParameters || parameters.forall { case (key, value) => other.parameters.get(key).contains(value) })
}

/**
 * The predefined media types, grouped by main type, and functions to find or
 * parse a media type.
 */
object MediaType extends MediaTypes {
  private val extensionMap: Map[String, MediaType] =
    // Some extensions are mapped to multiple media types.
    // We prefer the text media types, since this is the correct default for the most common extensions
    // like html, xml, javascript, etc.
    allMediaTypes.flatMap(m => m.fileExtensions.map(_ -> m)).toMap ++
      text.all.flatMap(m => m.fileExtensions.map(_ -> m)).toMap
  private[http] val contentTypeMap: Map[String, MediaType] = allMediaTypes.map(m => m.fullType -> m).toMap

  /**
   * A map from each main type to one of the predefined media types that has it.
   */
  val mainTypeMap = allMediaTypes.map(m => m.mainType -> m).toMap

  /**
   * A schema that writes a media type as its `fullType` string, without
   * parameters, and reads it with `forContentType`. An unknown string is
   * rejected with `Invalid media type: ` followed by the string.
   */
  implicit val schema: zio.schema.Schema[MediaType] = Schema[String].transformOrFail(
    str => MediaType.forContentType(str).toRight(s"Invalid media type: $str"),
    mediaType => Right(mediaType.fullType),
  )

  /**
   * Parses the value of a `Content-Type` header. The part before the first `;`
   * is lowercased and looked up among the predefined media types, and when it
   * is not one of them it is parsed with `parseCustomMediaType`. The result is
   * `None` when that part is not of the form `main/sub`. The parts after the
   * `;` are read as `name=value` pairs, with lowercase names and trimmed
   * values; a part that is not a `name=value` pair is ignored. When there is a
   * `;`, these parameters replace the ones of the predefined media type. White
   * space around the media type itself is not removed.
   */
  def forContentType(contentType: String): Option[MediaType] = {
    val index = contentType.indexOf(';')
    if (index == -1) {
      val contentTypeLC = contentType.toLowerCase
      contentTypeMap.get(contentTypeLC).orElse(parseCustomMediaType(contentTypeLC))
    } else {
      val (contentType1, parameter) = contentType.splitAt(index)
      val contentTypeLC             = contentType1.toLowerCase
      contentTypeMap
        .get(contentTypeLC)
        .orElse(parseCustomMediaType(contentTypeLC))
        .map(_.copy(parameters = parseOptionalParameters(parameter.split(';'))))
    }
  }

  /**
   * Returns the media type of a file extension given without the dot, such as
   * `html`, or `None` when it is unknown. The extension is matched in the case
   * it is written in, so only lowercase extensions are found. When several
   * media types share an extension, a `text` media type is preferred.
   */
  def forFileExtension(ext: String): Option[MediaType] = extensionMap.get(ext)

  /**
   * Parses a string of the form `main/sub` with optional `;name=value`
   * parameters into a media type that is not looked up among the predefined
   * ones. The result is `None` when the part before the first `;` does not have
   * exactly one `/`. The media type is marked as binary unless it starts with
   * `text/` or is `*` and `*`, and it is not marked as compressible.
   */
  def parseCustomMediaType(customMediaType: String): Option[MediaType] = {
    val contentTypeParts = customMediaType.split('/')
    if (contentTypeParts.length == 2) {
      val subtypeParts = contentTypeParts(1).split(';')
      // Default binary to true for unknown types unless they belong to text families
      val isBinary     = customMediaType != "*/*" && customMediaType != "text/*" && !customMediaType.startsWith("text/")
      if (subtypeParts.length >= 1) {
        Some(
          MediaType(
            mainType = contentTypeParts.head,
            subType = subtypeParts.head,
            binary = isBinary,
            parameters = if (subtypeParts.length >= 2) parseOptionalParameters(subtypeParts.tail) else Map.empty,
          ),
        )
      } else None
    } else None
  }

  private[http] def unsafeParseCustomMediaType(customMediaType: String): MediaType = {
    val contentTypeParts = customMediaType.split('/')
    if (contentTypeParts.length == 2) {
      val subtypeParts = contentTypeParts(1).split(';')
      val isBinary     = customMediaType != "*/*" && customMediaType != "text/*" && !customMediaType.startsWith("text/")
      if (subtypeParts.length >= 1) {
        MediaType(
          mainType = contentTypeParts.head,
          subType = subtypeParts.head,
          binary = isBinary,
          parameters = if (subtypeParts.length >= 2) parseOptionalParameters(subtypeParts.tail) else Map.empty,
        )
      } else throw new IllegalArgumentException(s"Invalid media type $customMediaType")
    } else throw new IllegalArgumentException(s"Invalid media type $customMediaType")
  }

  private def parseOptionalParameters(parameters: Array[String]): Map[String, String] = {
    val builder = Map.newBuilder[String, String]
    val size    = parameters.length
    var i       = 0
    while (i < size) {
      val parameter = parameters(i)
      val parts     = parameter.split('=')
      if (parts.length == 2) builder += ((parts(0).trim.toLowerCase, parts(1).trim))
      i += 1
    }
    builder.result()
  }
}
