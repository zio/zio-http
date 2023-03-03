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

import scala.util.Try

/**
 * ContentLength header value
 */
sealed trait ContentLength

object ContentLength {

  /**
   * The Content-Length header indicates the size of the message body, in bytes,
   * sent to the recipient.
   */
  final case class ContentLengthValue(length: Long) extends ContentLength

  /**
   * The ContentLength header value is invalid.
   */
  case object InvalidContentLengthValue extends ContentLength

  def fromContentLength(contentLength: ContentLength): String =
    contentLength match {
      case ContentLengthValue(length) => length.toString
      case InvalidContentLengthValue  => ""
    }

  def toContentLength(value: String): ContentLength =
    Try(value.trim.toLong).fold(_ => InvalidContentLengthValue, toContentLength)

  def toContentLength(value: Long): ContentLength =
    if (value >= 0) ContentLengthValue(value) else InvalidContentLengthValue

}
