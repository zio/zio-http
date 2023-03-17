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

/**
 * The Accept-Ranges HTTP response header is a marker used by the server to
 * advertise its support for partial requests from the client for file
 * downloads. The value of this field indicates the unit that can be used to
 * define a range. By default the RFC 7233 specification supports only 2
 * possible values.
 */
sealed trait AcceptRanges {
  val name: String
}

object AcceptRanges {
  case object Bytes extends AcceptRanges {
    override val name = "bytes"
  }
  case object None  extends AcceptRanges {
    override val name = "none"
  }

  def parse(value: String): Either[String, AcceptRanges] =
    value match {
      case Bytes.name => Right(Bytes)
      case None.name  => Right(None)
      case _          => Left("Invalid Accept-Ranges header")
    }

  def render(acceptRangers: AcceptRanges): String =
    acceptRangers.name
}
