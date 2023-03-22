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

package zio.http.model.headers

import zio.http.model.Header.HeaderType
import zio.http.model._

/**
 * Maintains a list of operators that parse and extract data from the headers.
 *
 * NOTE: Add methods here if it performs some kind of processing on the header
 * and returns the result.
 */
trait HeaderGetters { self =>

  /**
   * Gets a header or returns None if the header was not present or it could not
   * be parsed
   */
  final def header(headerType: HeaderType): Option[headerType.HeaderValue] =
    headers.get(headerType.name).flatMap { raw =>
      val parsed = headerType.parse(raw)
      parsed.toOption
    }

  /**
   * Gets a header. If the header is not present, returns None. If the header
   * could not be parsed it returns the parsing error
   */
  final def headerOrFail(headerType: HeaderType): Option[Either[String, headerType.HeaderValue]] =
    headers.get(headerType.name).map(headerType.parse(_))

  /**
   * Returns the headers
   */
  def headers: Headers

  /** Gets the raw unparsed header value */
  final def rawHeader(name: CharSequence): Option[String] = headers.get(name)

  /** Gets the raw unparsed header value */
  final def rawHeader(headerType: HeaderType): Option[String] =
    rawHeader(headerType.name)
}
