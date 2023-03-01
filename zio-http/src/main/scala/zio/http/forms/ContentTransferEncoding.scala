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

package zio.http.forms

import zio.Chunk

import zio.http.model.Headers

/**
 * Represents the Content-Transfer-Encoding header.
 *
 * https://www.w3.org/Protocols/rfc1341/5_Content-Transfer-Encoding.html
 */
sealed trait ContentTransferEncoding { self =>
  val name: String = self match {
    case ContentTransferEncoding.Binary          => "binary"
    case ContentTransferEncoding.QuotedPrintable => "quoted-printable"
    case ContentTransferEncoding.Base64          => "base64"
    case ContentTransferEncoding.`7bit`          => "7bit"
    case ContentTransferEncoding.`8bit`          => "8bit"
  }

  def asHeaders: Headers = Headers.contentTransferEncoding(name)
}

object ContentTransferEncoding {

  def parse(value: String): Option[ContentTransferEncoding] = value.toLowerCase match {
    case "binary"           => Some(Binary)
    case "7bit"             => Some(`7bit`)
    case "8bit"             => Some(`8bit`)
    case "quoted-printable" => Some(QuotedPrintable)
    case "base64"           => Some(Base64)
    case _                  => None
  }

  case object Binary          extends ContentTransferEncoding
  case object `7bit`          extends ContentTransferEncoding
  case object `8bit`          extends ContentTransferEncoding
  case object QuotedPrintable extends ContentTransferEncoding
  case object Base64          extends ContentTransferEncoding
}
