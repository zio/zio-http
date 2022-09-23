package zio.http.model.headers

import io.netty.handler.codec.http.HttpHeaderValues
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * List of commonly use HeaderValues. They are provided to reduce bugs caused by
 * typos and also to improve performance. `HeaderValues` arent encoded everytime
 * one needs to send them over the wire.
 */
trait HeaderValues {
  final val applicationJson: CharSequence               = HttpHeaderValues.APPLICATION_JSON
  final val applicationXWWWFormUrlencoded: CharSequence = HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED
  final val applicationOctetStream: CharSequence        = HttpHeaderValues.APPLICATION_OCTET_STREAM
  final val applicationXhtml: CharSequence              = HttpHeaderValues.APPLICATION_XHTML
  final val applicationXml: CharSequence                = HttpHeaderValues.APPLICATION_XML
  final val applicationZstd: CharSequence               = HttpHeaderValues.APPLICATION_ZSTD
  final val attachment: CharSequence                    = HttpHeaderValues.ATTACHMENT
  final val base64: CharSequence                        = HttpHeaderValues.BASE64
  final val binary: CharSequence                        = HttpHeaderValues.BINARY
  final val boundary: CharSequence                      = HttpHeaderValues.BOUNDARY
  final val bytes: CharSequence                         = HttpHeaderValues.BYTES
  final val charset: CharSequence                       = HttpHeaderValues.CHARSET
  final val chunked: CharSequence                       = HttpHeaderValues.CHUNKED
  final val close: CharSequence                         = HttpHeaderValues.CLOSE
  final val compress: CharSequence                      = HttpHeaderValues.COMPRESS
  final val continue: CharSequence                      = HttpHeaderValues.CONTINUE
  final val deflate: CharSequence                       = HttpHeaderValues.DEFLATE
  final val xDeflate: CharSequence                      = HttpHeaderValues.X_DEFLATE
  final val file: CharSequence                          = HttpHeaderValues.FILE
  final val filename: CharSequence                      = HttpHeaderValues.FILENAME
  final val formData: CharSequence                      = HttpHeaderValues.FORM_DATA
  final val gzip: CharSequence                          = HttpHeaderValues.GZIP
  final val br: CharSequence                            = HttpHeaderValues.BR
  final val zstd: CharSequence                          = HttpHeaderValues.ZSTD
  final val gzipDeflate: CharSequence                   = HttpHeaderValues.GZIP_DEFLATE
  final val xGzip: CharSequence                         = HttpHeaderValues.X_GZIP
  final val identity: CharSequence                      = HttpHeaderValues.IDENTITY
  final val keepAlive: CharSequence                     = HttpHeaderValues.KEEP_ALIVE
  final val maxAge: CharSequence                        = HttpHeaderValues.MAX_AGE
  final val maxStale: CharSequence                      = HttpHeaderValues.MAX_STALE
  final val minFresh: CharSequence                      = HttpHeaderValues.MIN_FRESH
  final val multipartFormData: CharSequence             = HttpHeaderValues.MULTIPART_FORM_DATA
  final val multipartMixed: CharSequence                = HttpHeaderValues.MULTIPART_MIXED
  final val mustRevalidate: CharSequence                = HttpHeaderValues.MUST_REVALIDATE
  final val name: CharSequence                          = HttpHeaderValues.NAME
  final val noCache: CharSequence                       = HttpHeaderValues.NO_CACHE
  final val noStore: CharSequence                       = HttpHeaderValues.NO_STORE
  final val noTransform: CharSequence                   = HttpHeaderValues.NO_TRANSFORM
  final val none: CharSequence                          = HttpHeaderValues.NONE
  final val zero: CharSequence                          = HttpHeaderValues.ZERO
  final val onlyIfCached: CharSequence                  = HttpHeaderValues.ONLY_IF_CACHED
  final val `private`: CharSequence                     = HttpHeaderValues.PRIVATE
  final val proxyRevalidate: CharSequence               = HttpHeaderValues.PROXY_REVALIDATE
  final val public: CharSequence                        = HttpHeaderValues.PUBLIC
  final val quotedPrintable: CharSequence               = HttpHeaderValues.QUOTED_PRINTABLE
  final val sMaxAge: CharSequence                       = HttpHeaderValues.S_MAXAGE
  final val textCss: CharSequence                       = HttpHeaderValues.TEXT_CSS
  final val textHtml: CharSequence                      = HttpHeaderValues.TEXT_HTML
  final val textEventStream: CharSequence               = HttpHeaderValues.TEXT_EVENT_STREAM
  final val textPlain: CharSequence                     = HttpHeaderValues.TEXT_PLAIN
  final val trailers: CharSequence                      = HttpHeaderValues.TRAILERS
  final val upgrade: CharSequence                       = HttpHeaderValues.UPGRADE
  final val webSocket: CharSequence                     = HttpHeaderValues.WEBSOCKET
  final val xmlHttpRequest: CharSequence                = HttpHeaderValues.XML_HTTP_REQUEST
}

object HeaderValues {

  sealed trait AcceptCharset {
    val raw: String
  }

  /**
   * The Accept-Charset request HTTP header was a header that advertised a
   * client's supported character encodings. It is no longer widely used. UTF-8
   * is well-supported and the overwhelmingly preferred choice for character
   * encoding. To guarantee better privacy through less configuration-based
   * entropy, all browsers omit the Accept-Charset header. Chrome, Firefox,
   * Internet Explorer, Opera, and Safari abandoned this header.
   */
  object AcceptCharset {

    /**
     * Seven-bit ASCII, a.k.a. ISO646-US, a.k.a. the Basic Latin block of the
     * Unicode character set
     */
    object US_ASCII extends AcceptCharset {
      override val raw: String = "US-ASCII"
    }

    /**
     * ISO Latin Alphabet No. 1, a.k.a. ISO-LATIN-1
     */
    object ISO_8859_1 extends AcceptCharset {
      override val raw: String = "ISO-8859-1"
    }

    /**
     * Eight-bit UCS Transformation Format
     */
    object UTF_8 extends AcceptCharset {
      override val raw: String = "UTF-8"
    }

    /**
     * Sixteen-bit UCS Transformation Format, big-endian byte order
     */
    object UTF_16BE extends AcceptCharset {
      override val raw: String = "UTF-16BE"
    }

    /**
     * Sixteen-bit UCS Transformation Format, little-endian byte order
     */
    object UTF_16LE extends AcceptCharset {
      override val raw: String = "UTF-16LE"
    }

    /**
     * Sixteen-bit UCS Transformation Format, byte order identified by an
     * optional byte-order mark
     */
    object UTF_16 extends AcceptCharset {
      override val raw: String = "UTF-16"
    }

    def toCharset(value: String): AcceptCharset = value match {
      case UTF_8.raw      => UTF_8
      case US_ASCII.raw   => US_ASCII
      case ISO_8859_1.raw => ISO_8859_1
      case UTF_16BE.raw   => UTF_16BE
      case UTF_16LE.raw   => UTF_16LE
      case UTF_16.raw     => UTF_16
      case _              => UTF_8
    }

    def fromCharset(charset: AcceptCharset): String = charset.raw
  }

}
