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

import scala.reflect.macros.blackbox

/**
 * Macro implementations for JavaScript interpolation (Scala 2.x)
 */
object JSInterpolatorMacros {

  def jsImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Js] = {
    import c.universe._

    // Simple approach: delegate to standard string interpolation at runtime
    // and perform compile-time validation when possible
    val stringContext = c.prefix.tree match {
      case Apply(_, List(sc)) => sc
      case _                  => c.abort(c.enclosingPosition, "Invalid macro usage")
    }

    // Try to extract literal string parts for validation
    stringContext match {
      case Apply(_, parts) =>
        val stringParts = parts.collect { case Literal(Constant(s: String)) =>
          s
        }

        // If we have only one part and no interpolation, validate it
        if (stringParts.length == parts.length && args.isEmpty && stringParts.length == 1) {
          val jsString = stringParts.head
          if (!isValidJavaScript(jsString)) {
            c.error(c.enclosingPosition, s"Invalid JavaScript syntax: $jsString")
          }
        }
      case _               => // Skip validation for complex cases
    }

    // Always delegate to standard string interpolation
    c.Expr[Js](q"Js.apply($stringContext.s(..$args))")
  }

  private def isValidJavaScript(js: String): Boolean = {
    val trimmed = js.trim
    if (trimmed.isEmpty) return true

    // Basic JavaScript validation patterns
    val jsKeywords = Set(
      "var",
      "let",
      "const",
      "function",
      "if",
      "else",
      "for",
      "while",
      "do",
      "switch",
      "case",
      "break",
      "continue",
      "return",
      "try",
      "catch",
      "finally",
      "throw",
      "new",
      "this",
      "typeof",
      "instanceof",
      "in",
      "delete",
      "void",
      "true",
      "false",
      "null",
      "undefined",
    )

    // Allow basic JavaScript constructs - be more permissive
    val validJsPatterns = List(
      """^[a-zA-Z_$][a-zA-Z0-9_$]*\s*=\s*.+;?\s*$""".r,                             // Variable assignment
      """^[a-zA-Z_$][a-zA-Z0-9_$]*\s*\([^)]*\)\s*\{.*}$""".r,                       // Function declaration
      """^[a-zA-Z_$][a-zA-Z0-9_$]*\s*\([^)]*\)\s*;?\s*$""".r,                       // Function call
      """^if\s*\([^)]+\)\s*\{.*}(\s*else\s*\{.*})?$""".r,                           // If statement
      """^for\s*\([^)]*\)\s*\{.*}$""".r,                                            // For loop
      """^while\s*\([^)]+\)\s*\{.*}$""".r,                                          // While loop
      """^[a-zA-Z_$][a-zA-Z0-9_$]*(\.[a-zA-Z_$][a-zA-Z0-9_$]*)*\s*=\s*.+;?\s*$""".r, // Property assignment
    )

    validJsPatterns.exists(_.findFirstMatchIn(trimmed).isDefined) ||
    jsKeywords.exists(keyword => trimmed.contains(keyword)) ||
    """^[a-zA-Z0-9_$\s.(){}\[\];:,'"+-=<>!&|*/%]+$""".r.findFirstMatchIn(trimmed).isDefined
  }
}
