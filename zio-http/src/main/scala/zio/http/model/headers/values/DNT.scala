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
  case object TrackingAllowed    extends DNT
  case object TrackingNotAllowed extends DNT
  case object NotSpecified       extends DNT

  def toDNT(value: String): Either[String, DNT] = {
    value match {
      case "null" => Right(NotSpecified)
      case "1"    => Right(TrackingNotAllowed)
      case "0"    => Right(TrackingAllowed)
      case _      => Left("Invalid DNT header")
    }
  }

  def fromDNT(dnt: DNT): String =
    dnt match {
      case NotSpecified       => "null"
      case TrackingAllowed    => "0"
      case TrackingNotAllowed => "1"
    }
}
