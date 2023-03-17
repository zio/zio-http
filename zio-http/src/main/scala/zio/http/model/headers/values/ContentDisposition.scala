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

sealed trait ContentDisposition

object ContentDisposition {
  final case class Attachment(filename: Option[String])             extends ContentDisposition
  final case class Inline(filename: Option[String])                 extends ContentDisposition
  final case class FormData(name: String, filename: Option[String]) extends ContentDisposition

  private val AttachmentRegex         = """attachment; filename="(.*)"""".r
  private val InlineRegex             = """inline; filename="(.*)"""".r
  private val FormDataRegex           = """form-data; name="(.*)"; filename="(.*)"""".r
  private val FormDataNoFileNameRegex = """form-data; name="(.*)"""".r

  def parse(contentDisposition: CharSequence): Either[String, ContentDisposition] = {
    val asString = contentDisposition.toString
    if (asString.startsWith("attachment")) {
      Right(contentDisposition match {
        case AttachmentRegex(filename) => Attachment(Some(filename))
        case _                         => Attachment(None)
      })
    } else if (asString.startsWith("inline")) {
      Right(contentDisposition match {
        case InlineRegex(filename) => Inline(Some(filename))
        case _                     => Inline(None)
      })
    } else if (asString.startsWith("form-data")) {
      contentDisposition match {
        case FormDataRegex(name, filename) => Right(FormData(name, Some(filename)))
        case FormDataNoFileNameRegex(name) => Right(FormData(name, None))
        case _                             => Left("Invalid form-data content disposition")
      }
    } else {
      Left("Invalid content disposition")
    }
  }

  def render(contentDisposition: ContentDisposition): String = {
    contentDisposition match {
      case Attachment(filename)     => s"attachment; ${filename.map("filename=" + _).getOrElse("")}"
      case Inline(filename)         => s"inline; ${filename.map("filename=" + _).getOrElse("")}"
      case FormData(name, filename) => s"form-data; name=$name; ${filename.map("filename=" + _).getOrElse("")}"
    }
  }

  val inline: ContentDisposition                                   = Inline(None)
  val attachment: ContentDisposition                               = Attachment(None)
  def inline(filename: String): ContentDisposition                 = Inline(Some(filename))
  def attachment(filename: String): ContentDisposition             = Attachment(Some(filename))
  def formData(name: String): ContentDisposition                   = FormData(name, None)
  def formData(name: String, filename: String): ContentDisposition = FormData(name, Some(filename))
}
