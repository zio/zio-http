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
import java.time.{ZoneOffset, ZonedDateTime}

sealed trait IfUnmodifiedSince

/**
 * If-Unmodified-Since request header makes the request for the resource
 * conditional: the server will send the requested resource or accept it in the
 * case of a POST or another non-safe method only if the resource has not been
 * modified after the date specified by this HTTP header.
 */
object IfUnmodifiedSince {
  final case class UnmodifiedSince(value: ZonedDateTime) extends IfUnmodifiedSince

  case object InvalidUnmodifiedSince extends IfUnmodifiedSince

  private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME

  def toIfUnmodifiedSince(value: String): IfUnmodifiedSince =
    try {
      UnmodifiedSince(ZonedDateTime.parse(value, formatter).withZoneSameInstant(ZoneOffset.UTC))
    } catch {
      case _: Throwable =>
        InvalidUnmodifiedSince
    }

  def fromIfUnmodifiedSince(ifModifiedSince: IfUnmodifiedSince): String = ifModifiedSince match {
    case UnmodifiedSince(value) => formatter.format(value)
    case InvalidUnmodifiedSince => ""
  }

}
