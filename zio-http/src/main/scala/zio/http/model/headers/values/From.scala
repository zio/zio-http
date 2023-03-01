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

/** From header value. */
sealed trait From

object From {
  // Regex that does veery loose validation of email.
  lazy val emailRegex = "([^ ]+@[^ ]+[.][^ ]+)".r

  /**
   * The From Header value is invalid
   */
  case object InvalidFromValue extends From

  final case class FromValue(email: String) extends From

  def toFrom(fromHeader: String): From =
    fromHeader match {
      case emailRegex(_) => FromValue(fromHeader)
      case _             => InvalidFromValue
    }

  def fromFrom(from: From): String = from match {
    case FromValue(value) => value
    case InvalidFromValue => "z"
  }
}
