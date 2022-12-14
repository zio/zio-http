package zio.http.security

private[http] object OutputEncoding {

  /**
   * Encode HTML characters that can cause XSS, according to OWASP
   * specification:
   * https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html#output-encoding-rules-summary
   *
   * Specification: Convert & to &amp;, Convert < to &lt;, Convert > to &gt;,
   * Convert " to &quot;, Convert ' to &#x27;, Convert / to &#x2F;
   *
   * @param output
   *   string that needs HTML encoding
   * @return
   *   HTML encoded string
   */
  def html(output: String): String = {
    output.map(char => encodeHtmlChar(char))
  }.mkString

  private def encodeHtmlChar(char: Char): String = char match {
    case '&'     => "&amp"
    case '<'     => "&lt;"
    case '>'     => "&gt;"
    case '"'     => "&quot;"
    case '\''    => "&#x27;"
    case '/'     => "&#x2F;"
    case _ @data => data.toString
  }

}
