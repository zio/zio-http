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

import scala.annotation.tailrec

final case class MediaType(
  mainType: String,
  subType: String,
  compressible: Boolean = false,
  binary: Boolean = false,
  fileExtensions: List[String] = Nil,
  extensions: Map[String, String] = Map.empty,
  parameters: Map[String, String] = Map.empty,
) {
  lazy val fullType: String = s"$mainType/$subType"
}

object MediaType extends MediaTypes {
  private val extensionMap: Map[String, MediaType]   = allMediaTypes.flatMap(m => m.fileExtensions.map(_ -> m)).toMap
  private val contentTypeMap: Map[String, MediaType] = allMediaTypes.map(m => m.fullType -> m).toMap
  val mainTypeMap                                    = allMediaTypes.map(m => m.mainType -> m).toMap

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
