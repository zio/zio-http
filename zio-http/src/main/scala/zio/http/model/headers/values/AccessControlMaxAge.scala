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

import java.time.Duration

import scala.util.Try

/**
 * The Access-Control-Max-Age response header indicates how long the results of
 * a preflight request (that is the information contained in the
 * Access-Control-Allow-Methods and Access-Control-Allow-Headers headers) can be
 * cached.
 *
 * Maximum number of seconds the results can be cached, as an unsigned
 * non-negative integer. Firefox caps this at 24 hours (86400 seconds). Chromium
 * (prior to v76) caps at 10 minutes (600 seconds). Chromium (starting in v76)
 * caps at 2 hours (7200 seconds). The default value is 5 seconds.
 */
final case class AccessControlMaxAge(duration: Duration)

object AccessControlMaxAge {

  def parse(seconds: String): Either[String, AccessControlMaxAge] = {
    Try(seconds.toLong).fold(
      _ => Left(s"Invalid Access-Control-Max-Age header value"),
      long =>
        if (long > -1) Right(AccessControlMaxAge(Duration.ofSeconds(long)))
        else Left(s"Invalid Access-Control-Max-Age header value"),
    )
  }

  def render(accessControlMaxAge: AccessControlMaxAge): String = {
    accessControlMaxAge.duration.toSeconds.toString
  }
}
