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
 * The Expect HTTP request header indicates expectations that need to be met by
 * the server to handle the request successfully. There is only one defined
 * expectation: 100-continue
 */
sealed trait Expect {
  val value: String
}

object Expect {
  case object `100-continue` extends Expect {
    val value = "100-continue"
  }

  def fromExpect(expect: Expect): String =
    expect.value

  def toExpect(value: String): Either[String, Expect] =
    value match {
      case `100-continue`.value => Right(`100-continue`)
      case _                    => Left("Invalid Expect header")
    }
}
