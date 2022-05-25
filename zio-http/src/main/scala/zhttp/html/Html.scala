package zhttp.html

import scala.language.implicitConversions

/**
 * A view is a domain that used generate HTML.
 */
sealed trait Html { self =>
  def encode: CharSequence = {
    self match {
      case Html.Empty                        => ""
      case Html.Single(element)              => element.encode
      case Html.Multiple(elements: Seq[Dom]) => elements.map(_.encode).mkString("")
    }
  }
}

object Html {
  implicit def fromString(string: CharSequence): Html = Html.Single(Dom.text(string))

  implicit def fromSeq(elements: Seq[Dom]): Html = Html.Multiple(elements)

  implicit def fromDomElement(element: Dom): Html = Html.Single(element)

  implicit def fromUnit(unit: Unit): Html = Html.Empty

  private[zhttp] case class Single(element: Dom) extends Html

  private[zhttp] final case class Multiple(children: Seq[Dom]) extends Html

  private[zhttp] case object Empty extends Html
}
