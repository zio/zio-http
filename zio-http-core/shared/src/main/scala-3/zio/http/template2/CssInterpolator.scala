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

/**
 * Trait providing CSS and selector string interpolation with compile-time validation.
 */
trait CssInterpolator {

  /**
   * CSS string interpolator that validates CSS syntax at compile time.
   */
  extension(inline sc: StringContext) {
    inline def css(inline args: Any*): Css = ${ CssInterpolatorMacros.cssImpl('args, 'sc) }
    inline def selector(inline args: Any*): CssSelector = ${ CssInterpolatorMacros.selectorImpl('args, 'sc) }
  }
}
