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

import zio.http.URL

/**
 * Location header value.
 */
final case class Location(url: URL)

object Location {

  def fromLocation(urlLocation: Location): String =
    urlLocation.url.toJavaURL.fold("")(_.toString())

  def toLocation(value: String): Either[String, Location] = {
    if (value == "") Left("Invalid Location header")
    else
      URL
        .fromString(value)
        .left
        .map(_ => "Invalid Location header")
        .map(url => Location(url))
  }
}
