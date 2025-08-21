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

package zio.http.template2

import scala.quoted.*

/**
 * Macro implementations for JavaScript interpolation (Scala 3.x)
 */
object JsInterpolatorMacros {

  def jsImpl(args: Expr[Seq[Any]], sc: Expr[StringContext])(using Quotes): Expr[Js] = {
    import quotes.reflect.*
    // Extract StringContext value and perform validation when possible
    sc.value match {
      case Some(stringContext) =>
        val stringParts = stringContext.parts
        // If we have a simple case with no interpolation, validate it
        if (stringParts.length == 1) {
          args match {
            case '{ Seq() } | '{ Nil } =>
              val jsString = stringParts.head
              if (!isValidJavaScript(jsString)) {
                report.error(s"Invalid JavaScript syntax: $jsString")
              }
            case _ => // Has interpolation, skip validation
          }
        }
      case None => // Can't extract value at compile time, skip validation
    }

    // Return Js wrapper around the interpolated string
    '{ Js($sc.s($args: _*)) }
  }

  private def isValidJavaScript(js: String): Boolean = {
    val trimmed = js.trim
    if (trimmed.isEmpty) return true

    // Basic JavaScript validation patterns
    val jsKeywords = Set(
      "var", "let", "const", "function", "if", "else", "for", "while", "do", "switch", "case",
      "break", "continue", "return", "try", "catch", "finally", "throw", "new", "this", "typeof",
      "instanceof", "in", "delete", "void", "true", "false", "null", "undefined"
    )

    val validJsPatterns = List(
      """^[a-zA-Z_$][a-zA-Z0-9_$]*\s*=\s*.+;?\s*$""".r,  // Variable assignment
      """^[a-zA-Z_$][a-zA-Z0-9_$]*\s*\([^)]*\)\s*\{.*}$""".r,  // Function declaration
      """^[a-zA-Z_$][a-zA-Z0-9_$]*\s*\([^)]*\)\s*;?\s*$""".r,  // Function call
      """^if\s*\([^)]+\)\s*\{.*}(\s*else\s*\{.*})?$""".r,  // If statement
      """^for\s*\([^)]*\)\s*\{.*}$""".r,  // For loop
      """^while\s*\([^)]+\)\s*\{.*}$""".r,  // While loop
      """^[a-zA-Z_$][a-zA-Z0-9_$]*(\.[a-zA-Z_$][a-zA-Z0-9_$]*)*\s*=\s*.+;?\s*$""".r  // Property assignment
    )

    validJsPatterns.exists(_.matches(trimmed)) ||
    jsKeywords.exists(keyword => trimmed.contains(keyword)) ||
    trimmed.matches("""^[a-zA-Z0-9_$\s.(){}\[\];:,'"+-=<>!&|*/%]+$""")
  }
}
