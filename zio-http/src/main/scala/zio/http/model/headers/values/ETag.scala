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

sealed trait ETag
object ETag {
  case object InvalidETagValue                  extends ETag
  case class StrongETagValue(validator: String) extends ETag
  case class WeakETagValue(validator: String)   extends ETag
  def toETag(value: String): ETag = {
    value match {
      case str if str.startsWith("w/\"") && str.endsWith("\"") => WeakETagValue(str.drop(3).dropRight(1))
      case str if str.startsWith("W/\"") && str.endsWith("\"") => WeakETagValue(str.drop(3).dropRight(1))
      case str if str.startsWith("\"") && str.endsWith("\"")   => StrongETagValue(str.drop(1).dropRight(1))
      case _                                                   => InvalidETagValue
    }
  }

  def fromETag(eTag: ETag): String = {
    eTag match {
      case WeakETagValue(value)   => s"""W/"$value""""
      case StrongETagValue(value) => s""""$value""""
      case InvalidETagValue       => ""
    }
  }
}
