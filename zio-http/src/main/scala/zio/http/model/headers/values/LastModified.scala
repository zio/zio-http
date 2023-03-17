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

final case class LastModified(value: ZonedDateTime)

object LastModified {
  private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME

  def parse(value: String): Either[String, LastModified] = {
    try {
      Right(LastModified(ZonedDateTime.parse(value, formatter).withZoneSameInstant(ZoneOffset.UTC)))
    } catch {
      case _: Throwable =>
        Left("Invalid Last-Modified header")
    }
  }

  def render(lastModified: LastModified): String =
    formatter.format(lastModified.value)
}
