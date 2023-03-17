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
  final case class Strong(validator: String) extends ETag
  final case class Weak(validator: String)   extends ETag

  def parse(value: String): Either[String, ETag] = {
    value match {
      case str if str.startsWith("w/\"") && str.endsWith("\"") => Right(Weak(str.drop(3).dropRight(1)))
      case str if str.startsWith("W/\"") && str.endsWith("\"") => Right(Weak(str.drop(3).dropRight(1)))
      case str if str.startsWith("\"") && str.endsWith("\"")   => Right(Strong(str.drop(1).dropRight(1)))
      case _                                                   => Left("Invalid ETag header")
    }
  }

  def render(eTag: ETag): String = {
    eTag match {
      case Weak(value)   => s"""W/"$value""""
      case Strong(value) => s""""$value""""
    }
  }
}
