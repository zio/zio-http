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

import scala.util.{Failure, Success, Try}

/**
 * The Content-Length header indicates the size of the message body, in bytes,
 * sent to the recipient.
 */
final case class ContentLength(length: Long)

object ContentLength {

  def parse(value: String): Either[String, ContentLength] =
    Try(value.trim.toLong) match {
      case Failure(_)     => Left("Invalid Content-Length header")
      case Success(value) => fromLong(value)
    }

  def render(contentLength: ContentLength): String =
    contentLength.length.toString

  private def fromLong(value: Long): Either[String, ContentLength] =
    if (value >= 0)
      Right(ContentLength(value))
    else
      Left("Invalid Content-Length header")

}
