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

package zio.http.h2

sealed trait H2Error extends Product with Serializable

object H2Error {
  final case class Code(value: Int) extends AnyVal

  object Code {
    val NO_ERROR: Code            = Code(0x0)
    val PROTOCOL_ERROR: Code      = Code(0x1)
    val INTERNAL_ERROR: Code      = Code(0x2)
    val FLOW_CONTROL_ERROR: Code  = Code(0x3)
    val SETTINGS_TIMEOUT: Code    = Code(0x4)
    val STREAM_CLOSED: Code       = Code(0x5)
    val FRAME_SIZE_ERROR: Code    = Code(0x6)
    val REFUSED_STREAM: Code      = Code(0x7)
    val CANCEL: Code              = Code(0x8)
    val COMPRESSION_ERROR: Code   = Code(0x9)
    val CONNECT_ERROR: Code       = Code(0xa)
    val ENHANCE_YOUR_CALM: Code   = Code(0xb)
    val INADEQUATE_SECURITY: Code = Code(0xc)
    val HTTP_1_1_REQUIRED: Code   = Code(0xd)
  }

  case object InsufficientData extends H2Error
  final case class InvalidFrameSize(msg: String) extends H2Error
  case object InvalidPadding extends H2Error
  final case class ProtocolViolation(msg: String) extends H2Error
}
