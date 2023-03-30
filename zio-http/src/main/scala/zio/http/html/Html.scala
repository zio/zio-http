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

package zio.http.html

import scala.language.implicitConversions

/**
 * A view is a domain that used generate HTML.
 */
sealed trait Html { self =>
  def encode: CharSequence =
    encode(EncodingState.NoIndentation)

  def encode(spaces: Int): CharSequence =
    encode(EncodingState.Indentation(0, spaces))

  private[html] def encode(state: EncodingState): CharSequence = {
    self match {
      case Html.Empty                        => ""
      case Html.Single(element)              => element.encode(state)
      case Html.Multiple(elements: Seq[Dom]) => elements.map(_.encode(state)).mkString(state.nextElemSeparator)
    }
  }

  def ++(that: Html): Html =
    (self, that) match {
      case (l, Html.Empty)                      => l
      case (Html.Empty, r)                      => r
      case (Html.Single(l), Html.Single(r))     => Html.Multiple(Seq(l, r))
      case (Html.Multiple(l), Html.Single(r))   => Html.Multiple(l :+ r)
      case (Html.Single(l), Html.Multiple(r))   => Html.Multiple(l +: r)
      case (Html.Multiple(l), Html.Multiple(r)) => Html.Multiple(l ++ r)
    }
}

object Html {
  implicit def fromString(string: CharSequence): Html = Html.Single(Dom.text(string))

  implicit def fromSeq(elements: Seq[Dom]): Html = Html.Multiple(elements)

  implicit def fromDomElement(element: Dom): Html = Html.Single(element)

  implicit def fromOption(maybeElement: Option[Dom]): Html =
    maybeElement.fold(Html.Empty: Html)(Html.Single.apply)

  implicit def fromUnit(unit: Unit): Html = Html.Empty

  private[zio] case class Single(element: Dom) extends Html

  private[zio] final case class Multiple(children: Seq[Dom]) extends Html

  private[zio] case object Empty extends Html
}
