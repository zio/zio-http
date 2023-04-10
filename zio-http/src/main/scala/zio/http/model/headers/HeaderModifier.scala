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

import zio.http.Header.HeaderType
import zio.http._

/**
 * Maintains a list of operators that modify the current Headers. Once modified,
 * a new instance of the same type is returned. So or eg:
 * `request.addHeader("A", "B")` should return a new `Request` and similarly
 * `headers.add("A", "B")` should return a new `Headers` instance.
 *
 * NOTE: Add methods here that modify the current headers and returns an
 * instance of the same type.
 */
trait HeaderModifier[+A] { self =>

  final def addHeader(header: Header): A =
    addHeaders(Headers(header))

  final def addHeader(name: CharSequence, value: CharSequence): A =
    addHeaders(Headers.apply(name, value))

  final def addHeaders(headers: Headers): A = updateHeaders(_ ++ headers)

  final def removeHeader(headerType: HeaderType): A = removeHeader(headerType.name)

  final def removeHeader(name: String): A = removeHeaders(Set(name))

  final def removeHeaders(headers: Set[String]): A =
    updateHeaders(orig => Headers(orig.filterNot(h => headers.contains(h.headerName))))

  final def setHeaders(headers: Headers): A = self.updateHeaders(_ => headers)

  /**
   * Updates the current Headers with new one, using the provided update
   * function passed.
   */
  def updateHeaders(update: Headers => Headers): A

  def withHeader(header: Header): A =
    addHeaders(Headers(header))
}
