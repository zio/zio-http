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

import java.time.format.DateTimeFormatter
import java.time.{Duration, ZonedDateTime}

import scala.util.{Failure, Success, Try}

sealed trait RetryAfter

/**
 * The RetryAfter HTTP header contains the date/time after which to retry
 *
 * RetryAfter: <Date>
 *
 * Date: <day-name>, <day> <month> <year> <hour>:<minute>:<second> GMT
 *
 * Example:
 *
 * Expires: Wed, 21 Oct 2015 07:28:00 GMT
 *
 * Or RetryAfter the delay seconds.
 */
object RetryAfter {
  final case class ByDate(date: ZonedDateTime) extends RetryAfter

  final case class ByDuration(delay: Duration) extends RetryAfter

  private val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  def parse(dateOrSeconds: String): Either[String, RetryAfter] =
    Try(dateOrSeconds.toLong) match {
      case Failure(_)     =>
        Try(ZonedDateTime.parse(dateOrSeconds, formatter)) match {
          case Success(value) => Right(ByDate(value))
          case Failure(_)     => Left("Invalid RetryAfter")
        }
      case Success(value) =>
        if (value >= 0)
          Right(ByDuration(Duration.ofSeconds(value)))
        else
          Left("Invalid RetryAfter")
    }

  def render(retryAfter: RetryAfter): String =
    retryAfter match {
      case ByDate(date)         => formatter.format(date)
      case ByDuration(duration) =>
        duration.toSeconds.toString
    }
}
