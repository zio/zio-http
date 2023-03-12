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

import scala.util.{Success, Try}

/**
 * Max-Forwards header value
 */
sealed trait MaxForwards
object MaxForwards {

  final case class MaxForwardsValue(value: Int) extends MaxForwards
  case object InvalidMaxForwardsValue           extends MaxForwards

  def toMaxForwards(value: String): MaxForwards = {
    Try(value.toInt) match {
      case Success(value) if value >= 0L => MaxForwardsValue(value)
      case _                             => InvalidMaxForwardsValue
    }
  }

  def fromMaxForwards(maxForwards: MaxForwards): String =
    maxForwards match {
      case MaxForwardsValue(value) => value.toString
      case InvalidMaxForwardsValue => ""
    }
}
