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

  /**
   * Encode HTML characters that can cause XSS, according to OWASP
   * specification:
   * https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html#output-encoding-rules-summary
   *
   * Specification: Convert & to &amp;, Convert < to &lt;, Convert > to &gt;,
   * Convert " to &quot;, Convert ' to &#x27;
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

    // trim removes leading and trailing whitespaces, tabs and linebreaks
    output.trim.foreach { char =>
      if (char > 126) {
        sb.append(encodeExtendedChar(char))
      } else if (char > 32) {
        sb.append(encodePrintableChar(char))
      } else {
        sb.append(encodeControlChar(char))
      }
    }
    sb.toString
  }

  private def encodeControlChar(char: Char): CharSequence = char match {
    case 9  => "\t";
    case 10 => " ";
    case 32 => " ";
    case _  => "";
  }

  private def encodePrintableChar(char: Char): CharSequence = char match {
    case 34      => "&quot;"
    case 38      => "&amp;"
    case 39      => "&apos;"
    case 60      => "&lt;"
    case 62      => "&gt;"
    case _ @char => char.toString
  }

  private def encodeExtendedChar(char: Char): CharSequence = char match {
    case 128 => "&euro;"
    case 130 => "&sbquo;"
    case 131 => "&fnof;"
    case 132 => "&bdquo;"
    case 133 => "&hellip;"
    case 134 => "&dagger;"
    case 135 => "&Dagger;"
    case 136 => "&circ;"
    case 137 => "&permil;"
    case 138 => "&Scaron;"
    case 139 => "&lsaquo;"
    case 140 => "&OElig;"
    case 142 => "&Zcaron;"
    case 145 => "&lsquo;"
    case 146 => "&rsquo;"
    case 147 => "&ldquo;"
    case 148 => "&rdquo;"
    case 149 => "&bull;"
    case 150 => "&ndash;"
    case 151 => "&mdash;"
    case 152 => "&tilde;"
    case 153 => "&trade;"
    case 154 => "&scaron;"
    case 155 => "&rsaquo;"
    case 156 => "&oelig;"
    case 158 => "&zcaron;"
    case 159 => "&Yuml;"
    case 160 => "&nbsp;"
    case 161 => "&iexcl;"
    case 162 => "&cent;"
    case 163 => "&pound;"
    case 164 => "&curren;"
    case 165 => "&yen;"
    case 166 => "&brvbar;"
    case 167 => "&sect;"
    case 168 => "&uml;"
    case 169 => "&copy;"
    case 170 => "&ordf;"
    case 171 => "&laquo;"
    case 172 => "&not;"
    case 173 => "&shy;"
    case 174 => "&reg;"
    case 175 => "&macr;"
    case 176 => "&deg;"
    case 177 => "&plusmn;"
    case 178 => "&sup2;"
    case 179 => "&sup3;"
    case 180 => "&acute;"
    case 181 => "&micro;"
    case 182 => "&para;"
    case 183 => "&middot;"
    case 184 => "&cedil;"
    case 185 => "&sup1;"
    case 186 => "&ordm;"
    case 187 => "&raquo;"
    case 188 => "&frac14;"
    case 189 => "&frac12;"
    case 190 => "&frac34;"
    case 191 => "&iquest;"
    case 192 => "&Agrave;"
    case 193 => "&Aacute;"
    case 194 => "&Acirc;"
    case 195 => "&Atilde;"
    case 196 => "&Auml;"
    case 197 => "&Aring;"
    case 198 => "&AElig;"
    case 199 => "&Ccedil;"
    case 200 => "&Egrave;"
    case 201 => "&Eacute;"
    case 202 => "&Ecirc;"
    case 203 => "&Euml;"
    case 204 => "&Igrave;"
    case 205 => "&Iacute;"
    case 206 => "&Icirc;"
    case 207 => "&Iuml;"
    case 208 => "&ETH;"
    case 209 => "&Ntilde;"
    case 210 => "&Ograve;"
    case 211 => "&Oacute;"
    case 212 => "&Ocirc;"
    case 213 => "&Otilde;"
    case 214 => "&Ouml;"
    case 215 => "&times;"
    case 216 => "&Oslash;"
    case 217 => "&Ugrave;"
    case 218 => "&Uacute;"
    case 219 => "&Ucirc;"
    case 220 => "&Uuml;"
    case 221 => "&Yacute;"
    case 222 => "&THORN;"
    case 223 => "&szlig;"
    case 224 => "&agrave;"
    case 225 => "&aacute;"
    case 226 => "&acirc;"
    case 227 => "&atilde;"
    case 228 => "&auml;"
    case 229 => "&aring;"
    case 230 => "&aelig;"
    case 231 => "&ccedil;"
    case 232 => "&egrave;"
    case 233 => "&eacute;"
    case 234 => "&ecirc;"
    case 235 => "&euml;"
    case 236 => "&igrave;"
    case 237 => "&iacute;"
    case 238 => "&icirc;"
    case 239 => "&iuml;"
    case 240 => "&eth;"
    case 241 => "&ntilde;"
    case 242 => "&ograve;"
    case 243 => "&oacute;"
    case 244 => "&ocirc;"
    case 245 => "&otilde;"
    case 246 => "&ouml;"
    case 247 => "&divide;"
    case 248 => "&oslash;"
    case 249 => "&ugrave;"
    case 250 => "&uacute;"
    case 251 => "&ucirc;"
    case 252 => "&uuml;"
    case 253 => "&yacute;"
    case 254 => "&thorn;"
    case 255 => "&yuml;"

    case 35  => "&num;"
    case 36  => "&dollar;"
    case 37  => "&percnt;"
    case 40  => "&lparen;"
    case 41  => "&rparen;"
    case 42  => "&ast;"
    case 43  => "&plus;"
    case 44  => "&comma;"
    case 46  => "&period;"
    case 47  => "&sol;"
    case 58  => "&colon;"
    case 59  => "&semi;"
    case 61  => "&equals;"
    case 63  => "&quest;"
    case 64  => "&commat;"
    case 91  => "&lsqb;"
    case 92  => "&bsol;"
    case 93  => "&rsqb;"
    case 94  => "&Hat;"
    case 95  => "&lowbar;"
    case 96  => "&grave;"
    case 123 => "&lcub;"
    case 124 => "&verbar;"
    case 125 => "&rcub;"
    case 126 => "&tilde;"

    case _ => "";

  }

}
