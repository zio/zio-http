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

import zio.Unsafe

import zio.http.internal.CookieEncoding
import zio.http.model.Cookie

sealed trait CookieDecoder[A] {
  type Out

  final def apply(cookie: String)(implicit unsafe: Unsafe): Out =
    this.unsafe.decode(cookie, validate = false)

  trait UnsafeAPI {
    def decode(header: String, validate: Boolean)(implicit unsafe: Unsafe): Out
  }

  val unsafe: UnsafeAPI
}

object CookieDecoder {
  implicit object RequestCookieDecoder extends CookieDecoder[Request] {
    override type Out = List[Cookie[Request]]

    override final val unsafe: UnsafeAPI = new UnsafeAPI {
      override final def decode(header: String, validate: Boolean)(implicit unsafe: Unsafe): List[Cookie[Request]] = {
        CookieEncoding.default.decodeRequestCookie(header, validate)
      }
    }
  }

  implicit object ResponseCookieDecoder extends CookieDecoder[Response] {
    override type Out = Cookie[Response]

    override final val unsafe: UnsafeAPI = new UnsafeAPI {
      override final def decode(header: String, validate: Boolean)(implicit unsafe: Unsafe): Cookie[Response] = {
        CookieEncoding.default.decodeResponseCookie(header, validate)
      }
    }
  }
}
