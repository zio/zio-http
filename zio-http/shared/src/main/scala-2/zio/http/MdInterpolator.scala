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

package zio.http

import zio.http.codec.Doc

/**
 * Provides the `md` string interpolator, which builds a `Doc` from CommonMark
 * text. It is mixed into the `zio.http` package object, so `md"..."` is
 * available with `import zio.http._`.
 */
trait MdInterpolator {

  /** Adds the `md` interpolator to `StringContext`. */
  implicit class MdInterpolatorHelper(val sc: StringContext) {

    /**
     * Creates a `Doc` from CommonMark text. The interpolated values are
     * inserted as `StringContext#s` does, then `stripMargin` is applied, and
     * the result is passed to `Doc.fromCommonMark`.
     *
     * {{{
     * import zio.http.codec.Doc
     *
     * val name = "Ada"
     * val doc: Doc = md"""|# Hello \$name
     *                     |Welcome"""
     * }}}
     */
    def md(args: Any*): Doc = Doc.fromCommonMark(sc.s(args: _*).stripMargin)
  }
}
