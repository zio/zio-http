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

package zio.http.internal

import scala.collection.immutable.BitSet

import zio.Chunk

import zio.http.Cookie

private[http] object CookieEncoding {
  private val validNameCharSet: BitSet  = BitSet(
    (33 to 126).filter { c =>
      "!#$%&'*+-.^_`|~0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".contains(c.toChar)
    }: _*,
  )
  private val validValueCharSet: BitSet = BitSet(
    (33 to 126).filter { c =>
      (c == 33) || (35 to 43).contains(c) || (45 to 58).contains(c) || (60 to 91).contains(c) || (93 to 126).contains(c)
    }: _*,
  )

  private def isValidInBitSet(s: String, set: BitSet): Boolean = {
    var i = 0
    while (i < s.length) {
      if (!set.contains(s.charAt(i).toInt)) return false
      i += 1
    }
    true
  }

  def encodeRequestCookie(cookie: Cookie.Request, validate: Boolean): String = {
    val name = if (validate) {
      require(isValidInBitSet(cookie.name, validNameCharSet), s"Invalid cookie name: ${cookie.name}")
      cookie.name
    } else {
      cookie.name
    }

    require(cookie.content != null, "Cookie value cannot be null")

    val value = if (validate) {
      require(isValidInBitSet(cookie.content, validValueCharSet), s"Invalid cookie value: ${cookie.content}")
      cookie.content
    } else {
      cookie.content
    }
    s"$name=$value"
  }

  def decodeRequestCookie(header: String, validate: Boolean): Chunk[Cookie.Request] = {
    def isValidName(s: String)  = isValidInBitSet(s, validNameCharSet)
    def isValidValue(s: String) = isValidInBitSet(s, validValueCharSet)
    Chunk.fromIterator(
      header.split(";").iterator.map(_.trim).filter(_.nonEmpty).flatMap { pair =>
        pair.split("=", 2) match {
          case Array(name, value) =>
            if (validate) {
              if (isValidName(name) && isValidValue(value))
                Some(Cookie.Request(name, value))
              else None
            } else {
              Some(Cookie.Request(name, value))
            }
          case _                  => None
        }
      },
    )
  }

  def encodeResponseCookie(cookie: Cookie.Response, validate: Boolean): String = {
    val name = if (validate) {
      require(isValidInBitSet(cookie.name, validNameCharSet), s"Invalid cookie name: ${cookie.name}")
      cookie.name
    } else {
      cookie.name
    }

    require(cookie.content != null, "Cookie value cannot be null")

    val value = if (validate) {
      require(isValidInBitSet(cookie.content, validValueCharSet), s"Invalid cookie value: ${cookie.content}")
      cookie.content
    } else {
      cookie.content
    }
    val b     = new StringBuilder(s"$name=$value")
    cookie.domain.foreach(d => b.append(s"; Domain=$d"))
    cookie.path.foreach(p => b.append(s"; Path=${p.encode}"))
    cookie.maxAge.foreach(m => b.append(s"; Max-Age=${m.getSeconds}"))
    if (cookie.isSecure) b.append("; Secure")
    if (cookie.isHttpOnly) b.append("; HTTPOnly")
    cookie.sameSite.foreach {
      case Cookie.SameSite.Strict => b.append("; SameSite=Strict")
      case Cookie.SameSite.Lax    => b.append("; SameSite=Lax")
      case Cookie.SameSite.None   => b.append("; SameSite=None")
    }
    b.result()
  }

  def decodeResponseCookie(header: String, validate: Boolean): Cookie.Response = {
    val parts                              = header.split(";").map(_.trim)
    val (name, value)                      = parts.headOption.flatMap { nv =>
      nv.split("=", 2) match {
        case Array(n, v) => Some((n, v))
        case _           => None
      }
    }.getOrElse(throw new IllegalArgumentException("Invalid Set-Cookie header"))
    if (validate) {
      require(isValidInBitSet(name, validNameCharSet), s"Invalid cookie name: $name")
      require(isValidInBitSet(value, validValueCharSet), s"Invalid cookie value: $value")
    }
    var domain: Option[String]             = None
    var path: Option[zio.http.Path]        = None
    var maxAge: Option[java.time.Duration] = None
    var isSecure                           = false
    var isHttpOnly                         = false
    var sameSite: Option[Cookie.SameSite]  = None
    parts.tail.foreach { attr =>
      val lower = attr.toLowerCase
      if (lower.startsWith("domain=")) domain = Some(attr.substring(7))
      else if (lower.startsWith("path=")) path = Some(zio.http.Path.decode(attr.substring(5)))
      else if (lower.startsWith("max-age=")) {
        val v = attr.substring(8)
        try maxAge = Some(java.time.Duration.ofSeconds(v.toLong))
        catch { case _: NumberFormatException => }
      } else if (lower == "secure") isSecure = true
      else if (lower == "httponly") isHttpOnly = true
      else if (lower.startsWith("samesite=")) {
        attr.substring(9).toLowerCase match {
          case "strict" => sameSite = Some(Cookie.SameSite.Strict)
          case "lax"    => sameSite = Some(Cookie.SameSite.Lax)
          case "none"   => sameSite = Some(Cookie.SameSite.None)
          case _        =>
            if (validate) {
              throw new IllegalArgumentException(s"Invalid SameSite value: ${attr.substring(9)}")
            }
        }
      }
    }
    Cookie.Response(
      name = name,
      content = value,
      domain = domain,
      path = path,
      isSecure = isSecure,
      isHttpOnly = isHttpOnly,
      maxAge = maxAge,
      sameSite = sameSite,
    )
  }
}
