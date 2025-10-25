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
 * Macro implementations for CSS interpolation (Scala 2.x)
 */
object CssInterpolatorMacros {

  def cssImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Css] = {
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
          val cssString = stringParts.head
          if (!isValidCss(cssString)) {
            c.error(c.enclosingPosition, s"Invalid CSS syntax: $cssString")
          }
        }
      case _               => // Skip validation for complex cases
    }

    // Always delegate to standard string interpolation
    c.Expr[Css](q"Css.apply($stringContext.s(..$args))")
  }

  def selectorImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[CssSelector] = {
    import c.universe._

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
          val selectorString = stringParts.head
          if (!isValidCssSelector(selectorString)) {
            c.error(c.enclosingPosition, s"Invalid CSS selector syntax: $selectorString")
          }
        }
      case _               => // Skip validation for complex cases
    }

    // Always delegate to standard string interpolation
    c.Expr[CssSelector](q"CssSelector.raw($stringContext.s(..$args))")
  }

  private def isValidCss(css: String): Boolean = {
    val trimmed = css.trim
    if (trimmed.isEmpty) return true

    // Basic CSS validation - allow most reasonable CSS
    val cssPropertyPattern    = """^[a-zA-Z\-]+\s*:\s*[^;]+;?\s*$""".r
    val cssRulePattern        = """^[^{]+\{[^}]*\}$""".r
    val cssDeclarationPattern = """^[a-zA-Z\-]+\s*:\s*[^;]+$""".r

    cssPropertyPattern.findFirstMatchIn(trimmed).isDefined ||
    cssRulePattern.findFirstMatchIn(trimmed).isDefined ||
    cssDeclarationPattern.findFirstMatchIn(trimmed).isDefined ||
    trimmed.contains(":") ||
    """^[a-zA-Z0-9\-\s\.\#\{\}\:;,'"()\[\]]+$""".r
      .findFirstMatchIn(trimmed)
      .isDefined
  }

  private def isValidCssSelector(selector: String): Boolean = {
    val trimmed = selector.trim
    if (trimmed.isEmpty) return true

    val cssSelectorPattern = """^[a-zA-Z0-9\-\_\.#\[\]\(\):>~\s\+\*\=\"\'\|\^\$\,n]+$""".r
    cssSelectorPattern.findFirstMatchIn(trimmed).isDefined
  }
}
