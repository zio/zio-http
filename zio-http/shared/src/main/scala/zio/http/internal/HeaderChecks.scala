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

import zio.http.Header.HeaderType
import zio.http._

/**
 * Operators that check whether the headers meet given constraints.
 */
trait HeaderChecks[+A] { self: HeaderOps[A] with A =>

  /**
   * Returns `true` when the `Content-Type` header parses and its type and
   * subtype, without parameters, equal `value` ignoring case.
   */
  final def hasContentType(value: CharSequence): Boolean =
    header(Header.ContentType).exists(ct =>
      CharSequenceExtensions.equals(ct.mediaType.fullType, value, CaseMode.Insensitive),
    )

  /**
   * Returns `true` when the `Content-Type` header parses and its media type
   * equals `mediaType`.
   */
  final def hasContentType(mediaType: MediaType): Boolean =
    header(Header.ContentType).exists(ct => ct.mediaType == mediaType)

  /**
   * Returns `true` when the content type is
   * `application/x-www-form-urlencoded`.
   */
  final def hasFormUrlencodedContentType: Boolean =
    hasContentType(MediaType.application.`x-www-form-urlencoded`.fullType)

  /** Returns `true` when the content type is `multipart/form-data`. */
  final def hasFormMultipartContentType: Boolean =
    hasContentType(MediaType.multipart.`form-data`.fullType)

  /** Returns `true` when a header with this name is present, ignoring case. */
  final def hasHeader(name: CharSequence): Boolean =
    rawHeader(name).nonEmpty

  /** Returns `true` when a header of this type is present and parses. */
  final def hasHeader(headerType: HeaderType): Boolean =
    header(headerType).nonEmpty

  /**
   * Returns `true` when the header of the same type parses to a value equal to
   * `header`.
   */
  final def hasHeader(header: Header): Boolean =
    self.header(header.headerType).contains(header)

  /** Returns `true` when the content type is `application/json`. */
  final def hasJsonContentType: Boolean =
    hasContentType(MediaType.application.json.fullType)

  /** Returns `true` when the content type is `text/plain`. */
  final def hasTextPlainContentType: Boolean =
    hasContentType(MediaType.text.plain.fullType)

  /** Returns `true` when the content type is `application/xhtml+xml`. */
  final def hasXhtmlXmlContentType: Boolean =
    hasContentType(MediaType.application.`xhtml+xml`.fullType)

  /** Returns `true` when the content type is `application/xml`. */
  final def hasXmlContentType: Boolean =
    hasContentType(MediaType.application.`xml`.fullType)
}
