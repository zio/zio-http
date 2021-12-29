package zhttp.http.headers

import io.netty.util.AsciiString.contentEqualsIgnoreCase
import zhttp.http.Headers.Literals.Value

/**
 * Maintains a list of operators that checks if the Headers meet the give constraints.
 *
 * NOTE: Add methods here, if it tests the Headers for something, and returns a true or false based on if the conditions
 * are met or not.
 */
trait HeaderChecks[+A] { self: HeaderExtension[A] with A =>
  final def hasContentType(value: CharSequence): Boolean =
    getContentType.exists(contentEqualsIgnoreCase(value, _))

  final def hasFormUrlencodedContentType: Boolean =
    hasContentType(Value.ApplicationXWWWFormUrlencoded)

  final def hasHeader(name: CharSequence, value: CharSequence): Boolean =
    getHeaderValue(name) match {
      case Some(v1) => v1 == value
      case None     => false
    }

  final def hasHeader(name: CharSequence): Boolean =
    getHeaderValue(name).nonEmpty

  final def hasJsonContentType: Boolean =
    hasContentType(Value.ApplicationJson)

  final def hasTextPlainContentType: Boolean =
    hasContentType(Value.TextPlain)

  final def hasXhtmlXmlContentType: Boolean =
    hasContentType(Value.ApplicationXhtml)

  final def hasXmlContentType: Boolean =
    hasContentType(Value.ApplicationXml)
}
