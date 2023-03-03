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

/** Pragma header value. */
sealed trait Pragma

object Pragma {

  /** Pragma no-cache value. */
  case object PragmaNoCacheValue extends Pragma

  /** Invalid pragma value. */
  case object InvalidPragmaValue extends Pragma

  def toPragma(value: String): Pragma =
    value.toLowerCase match {
      case "no-cache" => PragmaNoCacheValue
      case _          => InvalidPragmaValue
    }

  def fromPragma(pragma: Pragma): String =
    pragma match {
      case PragmaNoCacheValue => "no-cache"
      case InvalidPragmaValue => ""
    }
}
