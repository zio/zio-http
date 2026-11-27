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

package zio.http.h1

sealed trait H1Error extends Product with Serializable {
  def message: String
  def mustClose: Boolean = true
}

object H1Error {
  final case class InvalidMessage(message: String)                                                   extends H1Error
  final case class AmbiguousFraming(message: String = "ambiguous message framing")                   extends H1Error
  final case class LineTooLong(message: String = "start or chunk line exceeds the bound")            extends H1Error
  final case class HeaderSectionTooLarge(message: String = "header section exceeds the bound")       extends H1Error
  final case class TooManyHeaders(message: String = "too many header fields")                        extends H1Error
  final case class HeaderNameTooLong(message: String = "header name exceeds the bound")              extends H1Error
  final case class HeaderValueTooLong(message: String = "header value exceeds the bound")            extends H1Error
  final case class TrailerSectionTooLarge(message: String = "trailer section exceeds the bound")     extends H1Error
  final case class TooManyTrailers(message: String = "too many trailer fields")                      extends H1Error
  final case class RetentionOverflow(message: String = "undecoded bytes exceed the retention bound") extends H1Error
  final case class BodyTooLarge(message: String = "declared body exceeds the retention bound")       extends H1Error
  final case class DecoderPoisoned(message: String = "decoder is poisoned after a fatal error")      extends H1Error
}
