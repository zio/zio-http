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

import zio.Chunk

import zio.http.CookieEncoder._
import zio.http.{CookieDecoder, Request, model}

final case class RequestCookie(value: Chunk[model.Cookie[Request]])

/**
 * The Cookie HTTP request header contains stored HTTP cookies associated with
 * the server.
 */
object RequestCookie {

  def parse(value: String): Either[String, RequestCookie] = {
    implicit val decoder = CookieDecoder.RequestCookieDecoder

    model.Cookie.decode(value) match {
      case Left(value)  => Left(s"Invalid Cookie header: ${value.getMessage}")
      case Right(value) =>
        if (value.isEmpty) Left("Invalid Cookie header")
        else Right(RequestCookie(value))
    }
  }

  def render(cookie: RequestCookie): String =
    cookie.value.map(_.encode.getOrElse("")).mkString("; ")
}
