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
 * Macro implementations for CSS interpolation (Scala 3.x)
 */
object CssInterpolatorMacros {
  def cssImpl(args: Expr[Seq[Any]], sc: Expr[StringContext])(using Quotes): Expr[Css] = {
    import quotes.reflect.*

    // Extract StringContext value and perform validation when possible
    sc.value match {
      case Some(stringContext) =>
        val stringParts = stringContext.parts

        // If we have a simple case with no interpolation, validate it
        if (stringParts.length == 1) {
          // Check if args represents an empty vararg
          args.asTerm match {
            case Inlined(_, _, Typed(Repeated(Nil, _), _)) =>
              val cssString = stringParts.head
              if (!isValidCss(cssString)) {
                report.error(s"Invalid CSS syntax: $cssString")
              }
            case _ => // Has interpolation, skip validation
          }
        }
      case None => // Can't extract value at compile time, skip validation
    }

    // Always delegate to standard string interpolation
    '{ Css.apply($sc.s($args: _*)) }
  }

  def selectorImpl(args: Expr[Seq[Any]], sc: Expr[StringContext])(using Quotes): Expr[CssSelector] = {
    import quotes.reflect.*

    // Extract StringContext value and perform validation when possible
    sc.value match {
      case Some(stringContext) =>
        val stringParts = stringContext.parts

        // If we have a simple case with no interpolation, validate it
        if (stringParts.length == 1) {
          // Check if args represents an empty vararg
          args.asTerm match {
            case Inlined(_, _, Typed(Repeated(Nil, _), _)) =>
              val selectorString = stringParts.head
              if (!isValidCssSelector(selectorString)) {
                report.error(s"Invalid CSS selector syntax: $selectorString")
              }
            case _ => // Has interpolation, skip validation
          }
        }
      case None => // Can't extract value at compile time, skip validation
    }

    // Always delegate to standard string interpolation
    '{ CssSelector.apply($sc.s($args: _*)) }
  }

  private def isValidCss(css: String): Boolean = {
    val trimmed = css.trim
    if (trimmed.isEmpty) return true

    // Basic CSS validation - allow most reasonable CSS
    val cssPropertyPattern = """^[a-zA-Z\-]+\s*:\s*[^;]+;?\s*$""".r
    val cssRulePattern = """^[^{]+\{[^}]*}$""".r
    val cssDeclarationPattern = """^[a-zA-Z\-]+\s*:\s*[^;]+$""".r

    cssPropertyPattern.matches(trimmed) ||
    cssRulePattern.matches(trimmed) ||
    cssDeclarationPattern.matches(trimmed) ||
    trimmed.contains(":") || // Allow basic property declarations
    trimmed.matches("""^[a-zA-Z0-9\-\s.#{}:;,'"()\[\]]+$""") // Allow reasonable CSS characters
  }

  private def isValidCssSelector(selector: String): Boolean = {
    val trimmed = selector.trim
    if (trimmed.isEmpty) return true

    val validCssSelectorPattern = """^[a-zA-Z0-9\-_.#\[\]():>~\s+*="'|$,n]+$""".r
    validCssSelectorPattern.matches(trimmed)
  }
}
