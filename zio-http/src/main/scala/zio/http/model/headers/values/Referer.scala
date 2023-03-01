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

import zio.http.URL

/**
 * The Referer HTTP request header contains the absolute or partial address from
 * which a resource has been requested. The Referer header allows a server to
 * identify referring pages that people are visiting from or where requested
 * resources are being used. This data can be used for analytics, logging,
 * optimized caching, and more.
 *
 * When you click a link, the Referer contains the address of the page that
 * includes the link. When you make resource requests to another domain, the
 * Referer contains the address of the page that uses the requested resource.
 *
 * The Referer header can contain an origin, path, and querystring, and may not
 * contain URL fragments (i.e. #section) or username:password information. The
 * request's referrer policy defines the data that can be included. See
 * Referrer-Policy for more information and examples.
 */
sealed trait Referer

object Referer {

  /**
   * The Location header contains URL of the new Resource
   */
  final case class ValidReferer(url: URL) extends Referer

  /**
   * The URL header value is invalid.
   */
  case object InvalidReferer extends Referer

  def fromReferer(url: Referer): String = {
    url match {
      case ValidReferer(url) => url.toJavaURL.fold("")(_.toString())
      case InvalidReferer    => ""
    }

  }

  def toReferer(value: String): Referer = {
    URL.fromString(value) match {
      case Left(_)                                              => InvalidReferer
      case Right(url) if url.host.isEmpty || url.scheme.isEmpty => InvalidReferer
      case Right(url)                                           => ValidReferer(url)
    }
  }
}
