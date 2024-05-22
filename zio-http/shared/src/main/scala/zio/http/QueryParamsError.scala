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

package zio.http

import scala.util.control.NoStackTrace

import zio.NonEmptyChunk

import zio.http.codec.TextCodec

sealed trait QueryParamsError extends Exception with NoStackTrace {
  override def getMessage(): String = message
  def message: String
}
object QueryParamsError {
  final case class Missing(name: String) extends QueryParamsError {
    def message = s"Missing query parameter with name $name"
  }

  final case class Malformed(name: String, codec: TextCodec[_], values: NonEmptyChunk[String])
      extends QueryParamsError {
    def message: String =
      s"Unable to decode query parameter $name with values [ ${values.mkString(", ")} ] using ${codec.describe} codec"
  }
}
