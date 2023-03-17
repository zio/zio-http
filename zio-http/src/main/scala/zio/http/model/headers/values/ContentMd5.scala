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

final case class ContentMd5(value: String)

object ContentMd5 {
  private val MD5Regex = """[A-Fa-f0-9]{32}""".r

  def toContentMd5(value: CharSequence): Either[String, ContentMd5] =
    value match {
      case MD5Regex() => Right(ContentMd5(value.toString))
      case _          => Left("Invalid Content-MD5 header")
    }

  def fromContentMd5(contentMd5: ContentMd5): String =
    contentMd5.value
}
