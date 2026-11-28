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

package zio.http.h1

object H1ModelConversion {
  def toFieldPairs(headers: H1Headers): List[(String, String)] =
    headers.fields.map(header => (header.name, header.value))

  def fromFieldPairs(fields: List[(String, String)]): Either[H1Error, H1Headers] = {
    val builder = List.newBuilder[H1Header]
    var rest    = fields
    while (rest.nonEmpty) {
      val (name, value) = rest.head
      H1Header.fromStrings(name, value) match {
        case Left(error)   => return Left(error)
        case Right(header) => builder += header
      }
      rest = rest.tail
    }
    Right(H1Headers(builder.result()))
  }

  def connectionPreference(headers: H1Headers): H1ConnectionPreference = {
    val values = headers.headerValues("Connection")
    var i      = 0
    while (i < values.length) {
      val tokens = values(i).split(",")
      var j      = 0
      while (j < tokens.length) {
        if (H1Validation.equalsIgnoreCase(H1Validation.trimOWS(tokens(j)), "close"))
          return H1ConnectionPreference.Close
        j += 1
      }
      i += 1
    }
    H1ConnectionPreference.KeepAlive
  }
}
