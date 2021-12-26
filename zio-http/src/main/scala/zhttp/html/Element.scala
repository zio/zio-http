package zhttp.html

sealed trait Element { self =>
  def encode: String = self match {
    case Element.HtmlElement(name, children) =>
      val attributes = children.collect { case self: Element.Attribute => self.encode }

      val elements = children.collect {
        case self: Element.HtmlElement => self.encode
        case self: Element.Text        => self.encode
      }

      if (elements.isEmpty && attributes.isEmpty) s"<$name/>"
      else if (elements.isEmpty) s"<$name ${attributes.mkString(" ")}/>"
      else if (attributes.isEmpty) s"<$name>${elements.mkString("")}</$name>"
      else s"<$name ${attributes.mkString(" ")}>${elements.mkString}</$name>"

    case Element.Text(data)             => data
    case Element.Attribute(name, value) => s"""$name="$value""""
  }
}

object Element {
  def element(name: String, children: Element*): Element = Element.HtmlElement(name, children)

  def elementSeq(name: String, children: Seq[View]): Element = Element.HtmlElement(
    name,
    children.collect {
      case View.Single(element)    => Seq(element)
      case View.Multiple(children) => children
    }.flatten,
  )

  def text(data: String): Element = Element.Text(data)

  def attr(name: String, value: String): Element = Element.Attribute(name, value)

  private[zhttp] final case class HtmlElement(name: String, children: Seq[Element]) extends Element

  private[zhttp] final case class Text(data: String) extends Element

  private[zhttp] final case class Attribute(name: String, value: String) extends Element
}
