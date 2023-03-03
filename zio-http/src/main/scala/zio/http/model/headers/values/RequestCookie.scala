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

import zio.http.CookieEncoder._
import zio.http.{CookieDecoder, Request, model}

sealed trait RequestCookie

/**
 * The Cookie HTTP request header contains stored HTTP cookies associated with
 * the server.
 */
object RequestCookie {

  final case class CookieValue(value: List[model.Cookie[Request]]) extends RequestCookie
  final case class InvalidCookieValue(error: Exception)            extends RequestCookie

  def toCookie(value: String): zio.http.model.headers.values.RequestCookie = {
    implicit val decoder = CookieDecoder.RequestCookieDecoder
    model.Cookie.decode(value) match {
      case Left(value)  => InvalidCookieValue(value)
      case Right(value) =>
        if (value.isEmpty) InvalidCookieValue(new Exception("invalid cookie"))
        else
          CookieValue(value)
    }
  }

  def fromCookie(cookie: RequestCookie): String = cookie match {
    case CookieValue(value)    =>
      value.map(_.encode.getOrElse("")).mkString("; ")
    case InvalidCookieValue(_) => ""
  }
}
