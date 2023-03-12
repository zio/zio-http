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

import io.netty.handler.codec.http.{cookie => jCookie}
import zio.Unsafe
import zio.http.model.Cookie
import zio.http.model.Cookie.SameSite

import scala.jdk.CollectionConverters._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

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
        val decoder = if (validate) jCookie.ServerCookieDecoder.STRICT else jCookie.ServerCookieDecoder.LAX
        decoder.decodeAll(header).asScala.toList.map { cookie =>
          Cookie(cookie.name(), cookie.value()).toRequest
        }
      }
    }
  }

  implicit object ResponseCookieDecoder extends CookieDecoder[Response] {
    override type Out = Cookie[Response]

    override final val unsafe: UnsafeAPI = new UnsafeAPI {
      override final def decode(header: String, validate: Boolean)(implicit unsafe: Unsafe): Cookie[Response] = {
        val decoder = if (validate) jCookie.ClientCookieDecoder.STRICT else jCookie.ClientCookieDecoder.LAX

        val cookie = decoder.decode(header).asInstanceOf[jCookie.DefaultCookie]

        Cookie(
          name = cookie.name(),
          content = cookie.value(),
          domain = Option(cookie.domain()),
          path = Option(cookie.path()).map(Path.decode),
          maxAge = Option(cookie.maxAge()).filter(_ >= 0),
          isSecure = cookie.isSecure(),
          isHttpOnly = cookie.isHttpOnly(),
          sameSite = cookie.sameSite() match {
            case jCookie.CookieHeaderNames.SameSite.Strict => Option(SameSite.Strict)
            case jCookie.CookieHeaderNames.SameSite.Lax    => Option(SameSite.Lax)
            case jCookie.CookieHeaderNames.SameSite.None   => Option(SameSite.None)
            case null                                      => None
          },
        )
      }
    }
  }
}
