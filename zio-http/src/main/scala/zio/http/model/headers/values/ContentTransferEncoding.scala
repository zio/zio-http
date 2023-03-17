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

sealed trait ContentTransferEncoding

object ContentTransferEncoding {
  case object SevenBit                   extends ContentTransferEncoding
  case object EightBit                   extends ContentTransferEncoding
  case object Binary                     extends ContentTransferEncoding
  case object QuotedPrintable            extends ContentTransferEncoding
  case object Base64                     extends ContentTransferEncoding
  final case class XToken(token: String) extends ContentTransferEncoding

  private val XRegex = "x-(.*)".r

  def parse(s: CharSequence): Either[String, ContentTransferEncoding] =
    s.toString.toLowerCase match {
      case "7bit"             => Right(SevenBit)
      case "8bit"             => Right(EightBit)
      case "binary"           => Right(Binary)
      case "quoted-printable" => Right(QuotedPrintable)
      case "base64"           => Right(Base64)
      case XRegex(token)      => Right(XToken(token))
      case _                  => Left("Invalid Content-Transfer-Encoding header")
    }

  def render(contentTransferEncoding: ContentTransferEncoding): String =
    contentTransferEncoding match {
      case SevenBit        => "7bit"
      case EightBit        => "8bit"
      case Binary          => "binary"
      case QuotedPrintable => "quoted-printable"
      case Base64          => "base64"
      case XToken(token)   => s"x-$token"
    }
}
