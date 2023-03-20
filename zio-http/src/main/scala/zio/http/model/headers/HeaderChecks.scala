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

import zio.http.internal.{CaseMode, CharSequenceExtensions}
import zio.http.model.Header.HeaderType
import zio.http.model._

/**
 * Maintains a list of operators that checks if the Headers meet the give
 * constraints.
 */
trait HeaderChecks[+A] { self: HeaderOps[A] with A =>
  final def hasContentType(value: CharSequence): Boolean =
    header(Header.ContentType).exists(ct =>
      CharSequenceExtensions.equals(ct.value.fullType, value, CaseMode.Insensitive),
    )

  final def hasFormUrlencodedContentType: Boolean =
    hasContentType(HeaderValues.applicationXWWWFormUrlencoded)

  final def hasHeader(name: CharSequence): Boolean =
    rawHeader(name).nonEmpty

  final def hasHeader(headerType: HeaderType): Boolean =
    header(headerType).nonEmpty

  final def hasJsonContentType: Boolean =
    hasContentType(HeaderValues.applicationJson)

  final def hasMediaType(mediaType: MediaType): Boolean =
    header(Header.ContentType).exists(ct => ct.value == mediaType)

  final def hasTextPlainContentType: Boolean =
    hasContentType(HeaderValues.textPlain)

  final def hasXhtmlXmlContentType: Boolean =
    hasContentType(HeaderValues.applicationXhtml)

  final def hasXmlContentType: Boolean =
    hasContentType(HeaderValues.applicationXml)
}
