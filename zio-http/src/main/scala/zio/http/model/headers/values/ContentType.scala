package zio.http.model.headers.values

sealed trait ContentType extends Product with Serializable { self =>
  def toStringValue: String = ContentType.fromContentType(self)
}

object ContentType {
  // based on Mozilla's list of common media types
  // https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types/Common_types

  case object `audio/aac`                                                                 extends ContentType
  case object `application/x-abiword`                                                     extends ContentType
  case object `application/x-freearc`                                                     extends ContentType
  case object `image/avif`                                                                extends ContentType
  case object `video/x-msvideo`                                                           extends ContentType
  case object `application/vnd.amazon.ebook`                                              extends ContentType
  case object `application/octet-stream`                                                  extends ContentType
  case object `image/bmp`                                                                 extends ContentType
  case object `application/x-bzip`                                                        extends ContentType
  case object `application/x-bzip2`                                                       extends ContentType
  case object `application/x-cdf`                                                         extends ContentType
  case object `application/x-csh`                                                         extends ContentType
  case object `text/css`                                                                  extends ContentType
  case object `text/csv`                                                                  extends ContentType
  case object `application/msword`                                                        extends ContentType
  case object `application/vnd.openxmlformats-officedocument.wordprocessingml.document`   extends ContentType
  case object `application/vnd.ms-fontobject`                                             extends ContentType
  case object `application/epub+zip`                                                      extends ContentType
  case object `application/gzip`                                                          extends ContentType
  case object `image/gif`                                                                 extends ContentType
  case object `text/html`                                                                 extends ContentType
  case object `image/vnd.microsoft.icon`                                                  extends ContentType
  case object `text/calendar`                                                             extends ContentType
  case object `application/java-archive`                                                  extends ContentType
  case object `image/jpeg`                                                                extends ContentType
  case object `text/javascript`                                                           extends ContentType
  case object `application/json`                                                          extends ContentType
  case object `application/ld+json`                                                       extends ContentType
  case object `audio/midi`                                                                extends ContentType
  case object `audio/mpeg`                                                                extends ContentType
  case object `video/mp4`                                                                 extends ContentType
  case object `video/mpeg`                                                                extends ContentType
  case object `application/vnd.apple.installer+xml`                                       extends ContentType
  case object `application/vnd.oasis.opendocument.presentation`                           extends ContentType
  case object `application/vnd.oasis.opendocument.spreadsheet`                            extends ContentType
  case object `application/vnd.oasis.opendocument.text`                                   extends ContentType
  case object `audio/ogg`                                                                 extends ContentType
  case object `video/ogg`                                                                 extends ContentType
  case object `application/ogg`                                                           extends ContentType
  case object `audio/opus`                                                                extends ContentType
  case object `font/otf`                                                                  extends ContentType
  case object `image/png`                                                                 extends ContentType
  case object `application/pdf`                                                           extends ContentType
  case object `application/x-httpd-php`                                                   extends ContentType
  case object `application/vnd.ms-powerpoint`                                             extends ContentType
  case object `application/vnd.openxmlformats-officedocument.presentationml.presentation` extends ContentType
  case object `application/vnd.rar`                                                       extends ContentType
  case object `application/rtf`                                                           extends ContentType
  case object `application/x-sh`                                                          extends ContentType
  case object `image/svg+xml`                                                             extends ContentType
  case object `application/x-tar`                                                         extends ContentType
  case object `image/tiff`                                                                extends ContentType
  case object `video/mp2t`                                                                extends ContentType
  case object `font/ttf`                                                                  extends ContentType
  case object `text/plain`                                                                extends ContentType
  case object `application/vnd.visio`                                                     extends ContentType
  case object `audio/wav`                                                                 extends ContentType
  case object `audio/webm`                                                                extends ContentType
  case object `video/webm`                                                                extends ContentType
  case object `image/webp`                                                                extends ContentType
  case object `font/woff`                                                                 extends ContentType
  case object `font/woff2`                                                                extends ContentType
  case object `application/xhtml+xml`                                                     extends ContentType
  case object `application/vnd.ms-excel`                                                  extends ContentType
  case object `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`         extends ContentType
  case object `application/xml`                                                           extends ContentType
  case object `application/vnd.mozilla.xul+xml`                                           extends ContentType
  case object `application/zip`                                                           extends ContentType
  case object `video/3gpp`                                                                extends ContentType
  case object `video/3gpp2`                                                               extends ContentType
  case object `application/x-7z-compressed`                                               extends ContentType
  final case class Other(value: String)                                                   extends ContentType

  def toContentType(s: String): ContentType =
    s match {
      case "audio/aac"                                                               => `audio/aac`
      case "application/x-abiword"                                                   => `application/x-abiword`
      case "application/x-freearc"                                                   => `application/x-freearc`
      case "image/avif"                                                              => `image/avif`
      case "video/x-msvideo"                                                         => `video/x-msvideo`
      case "application/vnd.amazon.ebook"                                            => `application/vnd.amazon.ebook`
      case "application/octet-stream"                                                => `application/octet-stream`
      case "image/bmp"                                                               => `image/bmp`
      case "application/x-bzip"                                                      => `application/x-bzip`
      case "application/x-bzip2"                                                     => `application/x-bzip2`
      case "application/x-cdf"                                                       => `application/x-cdf`
      case "application/x-csh"                                                       => `application/x-csh`
      case "text/css"                                                                => `text/css`
      case "text/csv"                                                                => `text/csv`
      case "application/msword"                                                      => `application/msword`
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" =>
        `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
      case "application/vnd.ms-fontobject"                                           => `application/vnd.ms-fontobject`
      case "application/epub+zip"                                                    => `application/epub+zip`
      case "application/gzip"                                                        => `application/gzip`
      case "image/gif"                                                               => `image/gif`
      case "text/html"                                                               => `text/html`
      case "image/vnd.microsoft.icon"                                                => `image/vnd.microsoft.icon`
      case "text/calendar"                                                           => `text/calendar`
      case "application/java-archive"                                                => `application/java-archive`
      case "image/jpeg"                                                              => `image/jpeg`
      case "text/javascript"                                                         => `text/javascript`
      case "application/json"                                                        => `application/json`
      case "application/ld+json"                                                     => `application/ld+json`
      case "audio/midi"                                                              => `audio/midi`
      case "audio/mpeg"                                                              => `audio/mpeg`
      case "video/mp4"                                                               => `video/mp4`
      case "video/mpeg"                                                              => `video/mpeg`
      case "application/vnd.apple.installer+xml"             => `application/vnd.apple.installer+xml`
      case "application/vnd.oasis.opendocument.presentation" => `application/vnd.oasis.opendocument.presentation`
      case "application/vnd.oasis.opendocument.spreadsheet"  => `application/vnd.oasis.opendocument.spreadsheet`
      case "application/vnd.oasis.opendocument.text"         => `application/vnd.oasis.opendocument.text`
      case "audio/ogg"                                       => `audio/ogg`
      case "video/ogg"                                       => `video/ogg`
      case "application/ogg"                                 => `application/ogg`
      case "audio/opus"                                      => `audio/opus`
      case "font/otf"                                        => `font/otf`
      case "image/png"                                       => `image/png`
      case "application/pdf"                                 => `application/pdf`
      case "application/x-httpd-php"                         => `application/x-httpd-php`
      case "application/vnd.ms-powerpoint"                   => `application/vnd.ms-powerpoint`
      case "application/vnd.openxmlformats-officedocument.presentationml.presentation" =>
        `application/vnd.openxmlformats-officedocument.presentationml.presentation`
      case "application/vnd.rar"                                                       => `application/vnd.rar`
      case "application/rtf"                                                           => `application/rtf`
      case "application/x-sh"                                                          => `application/x-sh`
      case "image/svg+xml"                                                             => `image/svg+xml`
      case "application/x-tar"                                                         => `application/x-tar`
      case "image/tiff"                                                                => `image/tiff`
      case "video/mp2t"                                                                => `video/mp2t`
      case "font/ttf"                                                                  => `font/ttf`
      case "text/plain"                                                                => `text/plain`
      case "application/vnd.visio"                                                     => `application/vnd.visio`
      case "audio/wav"                                                                 => `audio/wav`
      case "audio/webm"                                                                => `audio/webm`
      case "video/webm"                                                                => `video/webm`
      case "image/webp"                                                                => `image/webp`
      case "font/woff"                                                                 => `font/woff`
      case "font/woff2"                                                                => `font/woff2`
      case "application/xhtml+xml"                                                     => `application/xhtml+xml`
      case "application/vnd.ms-excel"                                                  => `application/vnd.ms-excel`
      case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"         =>
        `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
      case "application/xml"                                                           => `application/xml`
      case "application/vnd.mozilla.xul+xml" => `application/vnd.mozilla.xul+xml`
      case "application/zip"                 => `application/zip`
      case "video/3gpp"                      => `video/3gpp`
      case "video/3gpp2"                     => `video/3gpp2`
      case "application/x-7z-compressed"     => `application/x-7z-compressed`
      case other                             => Other(other)
    }

  def fromContentType(contentType: ContentType): String =
    contentType match {
      case Other(value) => value
      case _            => contentType.productPrefix
    }

}
