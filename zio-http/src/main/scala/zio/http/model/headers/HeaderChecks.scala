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

import io.netty.handler.codec.http.HttpUtil
import io.netty.util.AsciiString.contentEqualsIgnoreCase
import zio.http.model._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Maintains a list of operators that checks if the Headers meet the give
 * constraints.
 *
 * NOTE: Add methods here, if it tests the Headers for something, and returns a
 * true or false based on if the conditions are met or not.
 */
trait HeaderChecks[+A] { self: HeaderExtension[A] with A =>
  final def hasContentType(value: CharSequence): Boolean = {
    contentType
      .flatMap(ct => Option(HttpUtil.getMimeType(ct)))
      .fold(false)(
        contentEqualsIgnoreCase(_, value),
      )
  }

  final def hasFormUrlencodedContentType: Boolean =
    hasContentType(HeaderValues.applicationXWWWFormUrlencoded)

  final def hasHeader(name: CharSequence, value: CharSequence): Boolean =
    headerValue(name) match {
      case Some(v1) => v1.contentEquals(value)
      case None     => false
    }

  final def hasHeader(name: CharSequence): Boolean =
    headerValue(name).nonEmpty

  final def hasJsonContentType: Boolean =
    hasContentType(HeaderValues.applicationJson)

  final def hasMediaType(other: MediaType): Boolean =
    mediaType match {
      case None     => false
      case Some(mt) => mt == other
    }

  final def hasTextPlainContentType: Boolean =
    hasContentType(HeaderValues.textPlain)

  final def hasXhtmlXmlContentType: Boolean =
    hasContentType(HeaderValues.applicationXhtml)

  final def hasXmlContentType: Boolean =
    hasContentType(HeaderValues.applicationXml)
}
