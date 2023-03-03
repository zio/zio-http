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
sealed trait Location

object Location {

  /**
   * The Location header contains URL of the new Resource
   */
  final case class LocationValue(url: URL) extends Location

  /**
   * The URL header value is invalid.
   */
  case object EmptyLocationValue extends Location

  def fromLocation(urlLocation: Location): String = {
    urlLocation match {
      case LocationValue(url) => url.toJavaURL.fold("")(_.toString())
      case EmptyLocationValue => ""
    }

  }

  def toLocation(value: String): Location = {
    if (value == "") EmptyLocationValue
    else URL.fromString(value).fold(_ => EmptyLocationValue, url => LocationValue(url))
  }
}
