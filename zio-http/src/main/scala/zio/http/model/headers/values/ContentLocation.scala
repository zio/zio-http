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

package zio.http.model.headers.values

import java.net.URI

sealed trait ContentLocation

object ContentLocation {
  final case class ContentLocationValue(value: URI) extends ContentLocation
  case object InvalidContentLocationValue           extends ContentLocation

  def toContentLocation(value: CharSequence): ContentLocation =
    try {
      ContentLocationValue(new URI(value.toString))
    } catch {
      case _: Throwable => InvalidContentLocationValue
    }

  def fromContentLocation(contentLocation: ContentLocation): String =
    contentLocation match {
      case ContentLocationValue(value) => value.toString
      case InvalidContentLocationValue => ""
    }
}
