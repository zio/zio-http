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

final case class MediaType private (value: String) extends AnyVal {
  override def toString: String = value
}

final object MediaType {

  val EDIX12               = MediaType("application/EDI-X12")
  val EDIFACT              = MediaType("application/EDIFACT")
  val Javascript           = MediaType("application/javascript")
  val OCTET                = MediaType("application/octet-stream")
  val OGG                  = MediaType("application/ogg")
  val PDF                  = MediaType("application/pdf")
  val XHTML                = MediaType("application/xhtml+xml")
  val SHOCKWAVEFLASH       = MediaType("application/x-shockwave-flash")
  val JSON                 = MediaType("application/json")
  val LDJSON               = MediaType("application/ld+json ")
  val XML                  = MediaType("application/xml")
  val ZIP                  = MediaType("application/zip")
  val FORM                 = MediaType("application/x-www-form-urlencoded")
  val MPEG                 = MediaType("audio/mpeg")
  val WMA                  = MediaType("audio/")
  val REALAUDIO            = MediaType("audio/vnd.rn-realaudio")
  val WAV                  = MediaType("audio/x-wav")
  val GIF                  = MediaType("image/gif")
  val JPEG                 = MediaType("image/jpeg")
  val PNG                  = MediaType("image/png")
  val TIFF                 = MediaType("image/tiff")
  val MSICON               = MediaType("image/vnd.microsoft.icon")
  val XICON                = MediaType("image/x-icon")
  val DJVU                 = MediaType("image/vnd.djvu")
  val SVGXML               = MediaType("image/svg+xml ")
  val MULTIPARTMIXED       = MediaType("multipart/mixed")
  val MULTIPARTALTERNATIVE = MediaType("multipart/alternative")
  val MULTIPARTRELATED     = MediaType("multipart/related")
  val MULTIPARTFORMDATA    = MediaType("multipart/form-data")
  val TEXTCSS              = MediaType("text/css")
  val TEXTCSV              = MediaType("text/csv")
  val TEXTHTML             = MediaType("text/html")
  val TEXTJAVASCRIPT       = MediaType("text/javascript")
  val TEXTPLAIN            = MediaType("text/plain")
  val TEXTXML              = MediaType("text/xml")
  val VIDEOMPEG            = MediaType("video/mpeg")
  val VIDEOMP4             = MediaType("video/mp4")
  val QUICKTIME            = MediaType("video/quicktime")
  val MSWMV                = MediaType("video/x-ms-wmv")
  val MSVIDEO              = MediaType("video/x-msvideo")
  val FLY                  = MediaType("video/x-flv")
  val WEBM                 = MediaType("video/webm")
  val OPENTEXT             = MediaType("application/vnd.oasis.opendocument.text")
  val OPENSHEET            = MediaType("application/vnd.oasis.opendocument.spreadsheet")
  val OPENPPT              = MediaType("application/vnd.oasis.opendocument.presentation")
  val OPENGRAPHICS         = MediaType("application/vnd.oasis.opendocument.graphics")
  val MSEXCEL              = MediaType("application/vnd.ms-excel")
  val OFFICESHEET          = MediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
  val MSPPT                = MediaType("application/vnd.ms-powerpoint")
  val OFFICEPPT            = MediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation")
  val MSWORD               = MediaType("application/msword")
  val OFFICEDOC            = MediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
  val MOZXML               = MediaType("application/vnd.mozilla.xul+xml")
}
