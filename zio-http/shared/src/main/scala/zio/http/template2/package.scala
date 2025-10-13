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

import scala.language.implicitConversions

import zio.http.template2.{Dom, Modifier}

/**
 * Package object for template2 providing all HTML elements and attributes.
 * Users can import everything with: import zio.http.template2._
 */
package object template2
    extends HtmlElements
    with HtmlAttributes
    with LowPriorityTemplateImplicits
    with CssInterpolator
    with JsInterpolator {
  implicit def itrToModifier(seq: Iterable[Dom]): Modifier    = Dom.Fragment(seq)
  implicit def optToModifier(opt: Option[Modifier]): Modifier = opt match {
    case Some(mod) => mod
    case None      => Dom.Empty
  }
  implicit def stringToModifier(s: String): Modifier          = Dom.Text(s)

}

private[http] trait LowPriorityTemplateImplicits {
  implicit def optStrToDom(opt: Option[String]): Dom = opt match {
    case Some(str) => Dom.Text(str)
    case None      => Dom.Empty
  }
  implicit def stringToDom(s: String): Dom           = Dom.Text(s)
}
