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

package zio.http.netty

import scala.jdk.CollectionConverters._

import zio.http.internal.CookieEncoding
import zio.http.model.Cookie
import zio.http.model.Cookie.SameSite
import zio.http.{Path, Request, Response}

import io.netty.handler.codec.http.{cookie => jCookie}

private[http] object NettyCookieEncoding extends CookieEncoding {
  override final def encodeRequestCookie(cookie: Cookie[Request], validate: Boolean): String = {
    val encoder = if (validate) jCookie.ClientCookieEncoder.STRICT else jCookie.ClientCookieEncoder.LAX
    val builder = new jCookie.DefaultCookie(cookie.name, cookie.content)
    encoder.encode(builder)
  }

  override final def decodeRequestCookie(header: String, validate: Boolean): List[Cookie[Request]] = {
    val decoder = if (validate) jCookie.ServerCookieDecoder.STRICT else jCookie.ServerCookieDecoder.LAX
    decoder.decodeAll(header).asScala.toList.map { cookie =>
      Cookie(cookie.name(), cookie.value()).toRequest
    }
  }

  override final def encodeResponseCookie(cookie: Cookie[Response], validate: Boolean): String = {
    val builder = new jCookie.DefaultCookie(cookie.name, cookie.content)

    val encoder = if (validate) jCookie.ServerCookieEncoder.STRICT else jCookie.ServerCookieEncoder.LAX

    cookie.domain.foreach(builder.setDomain)
    cookie.path.foreach(i => builder.setPath(i.encode))
    cookie.maxAge.foreach(i => builder.setMaxAge(i.getSeconds))
    cookie.sameSite.foreach {
      case SameSite.Strict => builder.setSameSite(jCookie.CookieHeaderNames.SameSite.Strict)
      case SameSite.Lax    => builder.setSameSite(jCookie.CookieHeaderNames.SameSite.Lax)
      case SameSite.None   => builder.setSameSite(jCookie.CookieHeaderNames.SameSite.None)
    }

    builder.setHttpOnly(cookie.isHttpOnly)
    builder.setSecure(cookie.isSecure)

    encoder.encode(builder)
  }

  override final def decodeResponseCookie(header: String, validate: Boolean): Cookie[Response] = {
    val decoder = if (validate) jCookie.ClientCookieDecoder.STRICT else jCookie.ClientCookieDecoder.LAX

    val cookie = decoder.decode(header).asInstanceOf[jCookie.DefaultCookie]

    Cookie(
      name = cookie.name(),
      content = cookie.value(),
      domain = Option(cookie.domain()),
      path = Option(cookie.path()).map(Path.decode),
      maxAge = Option(cookie.maxAge()).filter(_ >= 0),
      isSecure = cookie.isSecure,
      isHttpOnly = cookie.isHttpOnly,
      sameSite = cookie.sameSite() match {
        case jCookie.CookieHeaderNames.SameSite.Strict => Option(SameSite.Strict)
        case jCookie.CookieHeaderNames.SameSite.Lax    => Option(SameSite.Lax)
        case jCookie.CookieHeaderNames.SameSite.None   => Option(SameSite.None)
        case null                                      => None
      },
    )
  }
}
