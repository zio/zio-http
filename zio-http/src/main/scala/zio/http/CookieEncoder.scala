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

sealed trait CookieEncoder[A] {
  final def apply(a: Cookie[A])(implicit unsafe: Unsafe): String =
    this.unsafe.encode(a, validate = false)

  trait UnsafeAPI {
    def encode(a: Cookie[A], validate: Boolean)(implicit unsafe: Unsafe): String
  }

  val unsafe: UnsafeAPI
}

object CookieEncoder {
  implicit object RequestCookieEncoder extends CookieEncoder[Request] {
    override final val unsafe: UnsafeAPI = new UnsafeAPI {
      override final def encode(cookie: Cookie[Request], validate: Boolean)(implicit unsafe: Unsafe): String = {
        CookieEncoding.default.encodeRequestCookie(cookie, validate)
      }
    }
  }

  implicit object ResponseCookieEncoder extends CookieEncoder[Response] {
    override final val unsafe: UnsafeAPI = new UnsafeAPI {
      override final def encode(cookie: Cookie[Response], validate: Boolean)(implicit unsafe: Unsafe): String = {
        CookieEncoding.default.encodeResponseCookie(cookie, validate)
      }
    }
  }
}
