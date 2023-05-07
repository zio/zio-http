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

package zio.http.codec

sealed trait HttpCodecType

object HttpCodecType {
  type RequestType <: Path with Content with Query with Header with Method with PathQuery
  type ResponseType <: Content with Header with Status

  type PathQuery <: HttpCodecType
  type Path <: HttpCodecType
  type Content <: HttpCodecType
  type Query <: HttpCodecType
  type Header <: HttpCodecType
  type Method <: HttpCodecType
  type Status <: HttpCodecType

}
