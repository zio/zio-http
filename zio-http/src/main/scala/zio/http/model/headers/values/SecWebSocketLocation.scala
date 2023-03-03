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

sealed trait SecWebSocketLocation

object SecWebSocketLocation {
  final case class LocationValue(url: URL) extends SecWebSocketLocation

  case object EmptyLocationValue extends SecWebSocketLocation

  def fromSecWebSocketLocation(urlLocation: SecWebSocketLocation): String = {
    urlLocation match {
      case LocationValue(url) => url.encode
      case EmptyLocationValue => ""
    }

  }

  def toSecWebSocketLocation(value: String): SecWebSocketLocation = {
    if (value.trim == "") EmptyLocationValue
    else URL.fromString(value).fold(_ => EmptyLocationValue, url => LocationValue(url))
  }
}
