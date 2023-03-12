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

sealed trait DNT
object DNT {
  case object InvalidDNTValue            extends DNT
  case object TrackingAllowedDNTValue    extends DNT
  case object TrackingNotAllowedDNTValue extends DNT
  case object NotSpecifiedDNTValue       extends DNT

  def toDNT(value: String): DNT = {
    value match {
      case "null" => NotSpecifiedDNTValue
      case "1"    => TrackingNotAllowedDNTValue
      case "0"    => TrackingAllowedDNTValue
      case _      => InvalidDNTValue
    }
  }

  def fromDNT(dnt: DNT): String =
    dnt match {
      case NotSpecifiedDNTValue       => null
      case TrackingAllowedDNTValue    => "0"
      case TrackingNotAllowedDNTValue => "1"
      case InvalidDNTValue            => ""
    }
}
