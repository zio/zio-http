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
  def fullType: String = s"$mainType/$subType"
}

object MediaType extends MediaTypes {
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
        .map(_.copy(parameters = parseOptionalParameters(parameter.split(";"))))
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

    loop(parameters.toIndexedSeq, Map.empty).map { case (key, value) => key.trim -> value.trim }
  }
}
