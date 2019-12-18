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

  final val EDIX12               = MediaType("application/EDI-X12")
  final val EDIFACT              = MediaType("application/EDIFACT")
  final val Javascript           = MediaType("application/javascript")
  final val OCTET                = MediaType("application/octet-stream")
  final val OGG                  = MediaType("application/ogg")
  final val PDF                  = MediaType("application/pdf")
  final val XHTML                = MediaType("application/xhtml+xml")
  final val SHOCKWAVEFLASH       = MediaType("application/x-shockwave-flash")
  final val JSON                 = MediaType("application/json")
  final val LDJSON               = MediaType("application/ld+json ")
  final val XML                  = MediaType("application/xml")
  final val ZIP                  = MediaType("application/zip")
  final val FORM                 = MediaType("application/x-www-form-urlencoded")
  final val MPEG                 = MediaType("audio/mpeg")
  final val WMA                  = MediaType("audio/")
  final val REALAUDIO            = MediaType("audio/vnd.rn-realaudio")
  final val WAV                  = MediaType("audio/x-wav")
  final val GIF                  = MediaType("image/gif")
  final val JPEG                 = MediaType("image/jpeg")
  final val PNG                  = MediaType("image/png")
  final val TIFF                 = MediaType("image/tiff")
  final val MSICON               = MediaType("image/vnd.microsoft.icon")
  final val XICON                = MediaType("image/x-icon")
  final val DJVU                 = MediaType("image/vnd.djvu")
  final val SVGXML               = MediaType("image/svg+xml ")
  final val MULTIPARTMIXED       = MediaType("multipart/mixed")
  final val MULTIPARTALTERNATIVE = MediaType("multipart/alternative")
  final val MULTIPARTRELATED     = MediaType("multipart/related")
  final val MULTIPARTFORMDATA    = MediaType("multipart/form-data")
  final val TEXTCSS              = MediaType("text/css")
  final val TEXTCSV              = MediaType("text/csv")
  final val TEXTHTML             = MediaType("text/html")
  final val TEXTJAVASCRIPT       = MediaType("text/javascript")
  final val TEXTPLAIN            = MediaType("text/plain")
  final val TEXTXML              = MediaType("text/xml")
  final val VIDEOMPEG            = MediaType("video/mpeg")
  final val VIDEOMP4             = MediaType("video/mp4")
  final val QUICKTIME            = MediaType("video/quicktime")
  final val MSWMV                = MediaType("video/x-ms-wmv")
  final val MSVIDEO              = MediaType("video/x-msvideo")
  final val FLY                  = MediaType("video/x-flv")
  final val WEBM                 = MediaType("video/webm")
  final val OPENTEXT             = MediaType("application/vnd.oasis.opendocument.text")
  final val OPENSHEET            = MediaType("application/vnd.oasis.opendocument.spreadsheet")
  final val OPENPPT              = MediaType("application/vnd.oasis.opendocument.presentation")
  final val OPENGRAPHICS         = MediaType("application/vnd.oasis.opendocument.graphics")
  final val MSEXCEL              = MediaType("application/vnd.ms-excel")
  final val OFFICESHEET          = MediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
  final val MSPPT                = MediaType("application/vnd.ms-powerpoint")
  final val OFFICEPPT            = MediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation")
  final val MSWORD               = MediaType("application/msword")
  final val OFFICEDOC            = MediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
  final val MOZXML               = MediaType("application/vnd.mozilla.xul+xml")
}
