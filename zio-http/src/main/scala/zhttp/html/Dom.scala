package zhttp.html

/**
 * Light weight DOM implementation that can be rendered as a html string.
 *
 * @see
 *   <a href="https://html.spec.whatwg.org/multipage/syntax.html#void-elements">Void elements</a> only have a start tag;
 *   end tags must not be specified for void elements.
 */
sealed trait Dom { self =>
  def encode: String = self match {
    case Dom.Element(name, children) =>
      val attributes = children.collect { case self: Dom.Attribute => self.encode }

      val elements = children.collect {
        case self: Dom.Element => self.encode
        case self: Dom.Text    => self.encode
      }

      val noElements   = elements.isEmpty
      val noAttributes = attributes.isEmpty
      val isVoid       = Element.isVoid(name)

      if (noElements && noAttributes && isVoid) s"<$name/>"
      else if (noElements && isVoid) s"<$name ${attributes.mkString(" ")}/>"
      else if (noAttributes) s"<$name>${elements.mkString("")}</$name>"
      else s"<$name ${attributes.mkString(" ")}>${elements.mkString}</$name>"

    case Dom.Text(data)             => data
    case Dom.Attribute(name, value) => s"""$name="$value""""
    case Dom.Empty                  => ""
  }
}

object Dom {
  def attr(name: String, value: String): Dom = Dom.Attribute(name, value)

  def element(name: String, children: Dom*): Dom = Dom.Element(name, children)

  def empty: Dom = Empty

  def text(data: String): Dom = Dom.Text(data)

  private[zhttp] final case class Element(name: String, children: Seq[Dom]) extends Dom

  private[zhttp] final case class Text(data: String) extends Dom

  private[zhttp] final case class Attribute(name: String, value: String) extends Dom

  private[zhttp] object Empty extends Dom
}
