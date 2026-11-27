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

final case class H1Limits(
  maxRequestLineLength: Int,
  maxStatusLineLength: Int,
  maxHeaderSectionBytes: Int,
  maxHeaderCount: Int,
  maxHeaderNameLength: Int,
  maxHeaderValueLength: Int,
  maxChunkLineLength: Int,
  maxTrailerSectionBytes: Int,
  maxTrailerCount: Int,
  maxUndecodedBytes: Int,
)

object H1Limits {
  val Default: H1Limits = H1Limits(
    maxRequestLineLength = 8192,
    maxStatusLineLength = 8192,
    maxHeaderSectionBytes = 32768,
    maxHeaderCount = 100,
    maxHeaderNameLength = 256,
    maxHeaderValueLength = 8192,
    maxChunkLineLength = 256,
    maxTrailerSectionBytes = 16384,
    maxTrailerCount = 100,
    maxUndecodedBytes = 1048576,
  )
}
