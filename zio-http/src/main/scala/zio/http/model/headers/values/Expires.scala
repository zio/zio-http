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

import java.time._
import java.time.format.DateTimeFormatter

import scala.util.Try

final case class Expires(value: ZonedDateTime)

/**
 * The Expires HTTP header contains the date/time after which the response is
 * considered expired.
 *
 * Invalid expiration dates with value 0 represent a date in the past and mean
 * that the resource is already expired.
 *
 * Expires: <Date>
 *
 * Date: <day-name>, <day> <month> <year> <hour>:<minute>:<second> GMT
 *
 * Example:
 *
 * Expires: Wed, 21 Oct 2015 07:28:00 GMT
 */
object Expires {
  private val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  def parse(date: String): Either[String, Expires] =
    Try(Expires(ZonedDateTime.parse(date, formatter))).toEither.left.map(_ => "Invalid Expires header")

  def render(expires: Expires): String =
    formatter.format(expires.value)
}
