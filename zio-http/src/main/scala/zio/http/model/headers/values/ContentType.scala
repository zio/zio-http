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

package zio.http.model.headers.values

import zio.http.model.MediaType

sealed trait ContentType extends Product with Serializable { self =>
  def toStringValue: String = ContentType.fromContentType(self)
}

object ContentType {
  final case class ContentTypeValue(value: MediaType) extends ContentType
  case object InvalidContentType                      extends ContentType

  def toContentType(s: CharSequence): ContentType =
    MediaType.forContentType(s.toString) match {
      case Some(value) => ContentTypeValue(value)
      case None        => InvalidContentType
    }

  def fromContentType(contentType: ContentType): String =
    contentType match {
      case ContentTypeValue(value) => value.fullType
      case InvalidContentType      => ""
    }

  def fromMediaType(mediaType: MediaType): ContentType = ContentTypeValue(mediaType)

}
