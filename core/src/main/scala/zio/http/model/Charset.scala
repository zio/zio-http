/*
 *
 *  Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package zio.http.model

import java.nio.charset.{ StandardCharsets, Charset => JCharset }

import scala.util.Try

final case class Charset private (value: String) extends AnyVal {
  override def toString: String = value
}

object Charset {

  def fromCharset(charSet: JCharset): Charset = Charset(charSet.name)

  def fromString(str: String): Option[Charset] =
    Try(JCharset.forName(str)).map(nioCharset => Charset(nioCharset.name)).toOption

  val ISO_8859_1 = Charset(StandardCharsets.ISO_8859_1.name)
  val UTF_8      = Charset(StandardCharsets.UTF_8.name)
  val UTF_16     = Charset(StandardCharsets.UTF_16.name)
  val UTF_16BE   = Charset(StandardCharsets.UTF_16BE.name)
  val UTF_16LE   = Charset(StandardCharsets.UTF_16LE.name)
  val US_ASCII   = Charset(StandardCharsets.US_ASCII.name)
}
