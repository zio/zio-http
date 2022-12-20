package zio.http.security

import scala.collection.mutable

private[http] object OutputEncoder {
  private val `&` = "&amp"
  private val `<` = "&lt;"
  private val `>` = "&gt;"
  private val `"` = "&quot;"
  private val `'` = "&#x27;"
  private val `/` = "&#x2F;"

  /**
   * Encode HTML characters that can cause XSS, according to OWASP
   * specification:
   * https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html#output-encoding-rules-summary
   *
   * Specification: Convert & to &amp;, Convert < to &lt;, Convert > to &gt;,
   * Convert " to &quot;, Convert ' to &#x27;, Convert / to &#x2F;
   *
   * Only use this function to encode characters inside HTML context:
   * <html>output</html
   *
   * @param output
   *   string that needs HTML encoding
   * @return
   *   HTML encoded string
   */
  def encodeHtml(output: String): String = {
    val sb = new mutable.StringBuilder(output.length)

    var idx = 0

    while (idx < output.length) {
      sb.append(encodeHtmlChar(output.charAt(idx)))
      idx += 1
    }

    sb.mkString
  }

  private def encodeHtmlChar(char: Char): CharSequence = char match {
    case '&'     => `&`
    case '<'     => `<`
    case '>'     => `>`
    case '"'     => `"`
    case '\''    => `'`
    case '/'     => `/`
    case _ @char => char.toString
  }

}
