package zhttp.html

import zhttp.html.Dom.Element

import scala.language.implicitConversions

sealed trait Dom { self =>
  def encode: String = {
    self match {
      case Dom.Empty            => ""
      case element: Dom.Element =>
        element match {
          case Dom.Element.Text(text)             => text
          case Dom.Element.Tagged(name, children) =>
            val attributes        = children.collect { case a @ Dom.Attribute(_, _) => a }
            val elements          = children.collect {
              case a @ Dom.Element.Tagged(_, _) => a
              case a @ Dom.Element.Text(_)      => a
            }
            val attributesEncoded = if (attributes.isEmpty) "" else " " + attributes.map(_.encode).mkString(" ")

            if (elements.isEmpty) s"<${name}${attributesEncoded}/>"
            else s"""<$name${attributesEncoded}>${elements.map(_.encode).mkString("")}</$name>"""
        }

      case Dom.Attribute(name, value)          => s"""${name}="${value}""""
      case Dom.Phantom(elements: Seq[Element]) => elements.map(_.encode).mkString("")
    }
  }
}

object Dom {
  implicit def fromString(string: String): Dom = Dom.text(string)

  implicit def fromSeq(elements: Seq[Element]): Dom = Dom.Phantom(elements)

  def attribute(name: String, value: String): Dom = Attribute(name, value)

  def element(name: String, children: Dom*): Dom = elementSeq(name, children)

  def elementSeq(name: String, children: Seq[Dom]): Element = Element.Tagged(name, children)

  def empty: Dom = Empty

  def phantom(seq: Seq[Element]): Dom = Phantom(seq)

  def text(data: String): Dom = Element.Text(data)

  sealed trait Element extends Dom

  private final case class Phantom(children: Seq[Element]) extends Dom

  private final case class Attribute(name: String, value: String) extends Dom

  object Element {
    case class Tagged(name: String, children: Seq[Dom]) extends Element
    case class Text(data: String)                       extends Element
  }

  private final case object Empty extends Dom
}
