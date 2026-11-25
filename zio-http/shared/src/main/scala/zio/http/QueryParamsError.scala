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

/**
 * The error of reading a query parameter: it is either missing or it could not
 * be decoded.
 */
sealed trait QueryParamsError extends Exception with NoStackTrace {
  override def getMessage(): String = message

  /** The description of the error, which is also returned by `getMessage`. */
  def message: String
}

/**
 * The query parameter errors.
 */
object QueryParamsError {

  /**
   * The query parameter is not present.
   *
   * @param name
   *   the name of the missing parameter
   */
  final case class Missing(name: String) extends QueryParamsError {

    /** Returns `Missing query parameter with name` followed by the name. */
    def message = s"Missing query parameter with name $name"
  }

  /**
   * A value of the query parameter could not be decoded with the codec.
   *
   * @param name
   *   the name of the parameter
   * @param codec
   *   the codec that failed to decode
   * @param values
   *   the values of the parameter, as they were received
   */
  final case class Malformed(name: String, codec: TextCodec[_], values: NonEmptyChunk[String])
      extends QueryParamsError {

    /**
     * Returns a message with the name, the values in `[ ... ]` separated by
     * commas, and the description of the codec, for example
     * `Unable to decode query parameter n with values [ a, b ] using an integer codec`.
     */
    def message: String =
      s"Unable to decode query parameter $name with values [ ${values.mkString(", ")} ] using ${codec.describe} codec"
  }
}
