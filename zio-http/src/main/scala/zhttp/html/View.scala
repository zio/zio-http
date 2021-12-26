package zhttp.html

import scala.language.implicitConversions

sealed trait View { self =>
  def encode: String = {
    self match {
      case View.Empty                            => ""
      case View.Single(element)                  => element.encode
      case View.Multiple(elements: Seq[Element]) => elements.map(_.encode).mkString("")
    }
  }
}

object View {
  implicit def fromString(string: String): View = View.Single(Element.text(string))

  implicit def fromSeq(elements: Seq[Element]): View = View.Multiple(elements)

  implicit def fromDomElement(element: Element): View = View.Single(element)

  private[zhttp] def attribute(name: String, value: String): View = View.Single(Element.attr(name, value))

  private[zhttp] def element(name: String, children: View*): View = Element.elementSeq(name, children)

  private[zhttp] def empty: View = Empty

  private[zhttp] def phantom(seq: Seq[Element]): View = Multiple(seq)

  private[zhttp] def text(text: String): View = Single(Element.text(text))

  private[zhttp] case class Single(element: Element) extends View

  private[zhttp] final case class Multiple(children: Seq[Element]) extends View

  private[zhttp] final case object Empty extends View
}
