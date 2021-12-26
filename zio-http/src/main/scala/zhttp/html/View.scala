package zhttp.html

import zhttp.html.View.Element

import scala.language.implicitConversions

sealed trait View { self =>
  def encode: String = {
    self match {
      case View.Empty                           => ""
      case View.ViewElement(element)             => element.encode
      case View.Attribute(name, value)          => s"""${name}="${value}""""
      case View.Phantom(elements: Seq[Element]) => elements.map(_.encode).mkString("")
    }
  }
}

object View {
  implicit def fromString(string: String): View = View.ViewElement(Element.text(string))

  implicit def fromSeq(elements: Seq[Element]): View  = View.Phantom(elements)

  implicit def fromDomElement(element: Element): View = View.ViewElement(element)

  private[zhttp] def attribute(name: String, value: String): View = Attribute(name, value)

  private[zhttp] def element(name: String, children: View*): View = ViewElement(Element.elementSeq(name, children))

  private[zhttp] def empty: View = Empty

  private[zhttp] def phantom(seq: Seq[Element]): View = Phantom(seq)

  private[zhttp] def text(text: String): View = ViewElement(Element.text(text))

  sealed trait Element { self =>
    def encode: String = self match {
      case Element.Text(text)                  => text
      case Element.HtmlElement(name, children) =>
        val attributes        = children.collect { case a @ Attribute(_, _) => a }
        val elements          = children.collect { case ViewElement(element) => element }
        val attributesEncoded = if (attributes.isEmpty) "" else " " + attributes.map(_.encode).mkString(" ")

        if (elements.isEmpty) s"<${name}${attributesEncoded}/>"
        else s"""<$name${attributesEncoded}>${elements.map(_.encode).mkString("")}</$name>"""
    }
  }

  private case class ViewElement(element: Element) extends View

  private final case class Phantom(children: Seq[Element]) extends View

  private final case class Attribute(name: String, value: String) extends View

  object Element {
    def element(name: String, children: View*): Element = elementSeq(name, children)

    def elementSeq(name: String, children: Seq[View]): Element = Element.HtmlElement(name, children)

    def text(data: String): Element = Element.Text(data)

    case class HtmlElement(name: String, children: Seq[View]) extends Element

    case class Text(data: String) extends Element
  }

  private final case object Empty extends View
}
