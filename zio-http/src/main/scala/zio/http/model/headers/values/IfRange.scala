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

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import scala.util.Try

/**
 * The If-Range HTTP request header makes a range request conditional. Possible
 * values:
 *   - <day-name>, <day> <month> <year> <hour>:<minute>:<second> GMT
 *   - <etag> a string of ASCII characters placed between double quotes (Like
 *     "675af34563dc-tr34"). A weak entity tag (one prefixed by W/) must not be
 *     used in this header.
 */
sealed trait IfRange

object IfRange {
  private val webDateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  final case class ETag(value: String)            extends IfRange
  final case class DateTime(value: ZonedDateTime) extends IfRange

  def parse(value: String): Either[String, IfRange] =
    value match {
      case value if value.startsWith("\"") && value.endsWith("\"") =>
        Right(ETag(value.drop(1).dropRight(1)))
      case dateTime                                                =>
        Try(DateTime(ZonedDateTime.from(webDateTimeFormatter.parse(dateTime)))).toEither.left.map(_ =>
          "Invalid If-Range header",
        )
    }

  def render(ifRange: IfRange): String =
    ifRange match {
      case DateTime(value) => webDateTimeFormatter.format(value)
      case ETag(value)     => s""""$value""""
    }
}
