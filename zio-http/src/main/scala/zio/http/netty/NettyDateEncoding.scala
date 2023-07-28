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

package zio.http.netty

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.Date

import zio.http.internal.DateEncoding

import io.netty.handler.codec.DateFormatter

private[netty] object NettyDateEncoding extends DateEncoding {
  override def encodeDate(date: ZonedDateTime): String =
    DateFormatter.format(Date.from(date.toInstant))

  override def decodeDate(date: String): Option[ZonedDateTime] =
    Option(DateFormatter.parseHttpDate(date)).map(date => ZonedDateTime.ofInstant(date.toInstant, ZoneOffset.UTC))
}
