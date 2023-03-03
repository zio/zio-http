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

import scala.util.Try

/**
 * Age header value.
 */
sealed trait Age

object Age {

  /**
   * The Age header contains the time in seconds the object was in a proxy
   * cache.
   */
  final case class AgeValue(seconds: Int) extends Age

  /**
   * The Age header value is invalid.
   */
  case object InvalidAgeValue extends Age

  def fromAge(age: Age): String =
    age match {
      case AgeValue(seconds) => seconds.toString
      case InvalidAgeValue   => ""
    }

  def toAge(value: String): Age =
    Try(value.trim.toInt).fold(_ => InvalidAgeValue, value => if (value > 0) AgeValue(value) else InvalidAgeValue)
}
