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

package zio.http.internal

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
