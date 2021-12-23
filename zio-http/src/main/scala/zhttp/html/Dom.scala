package zhttp.html

import zhttp.html.Dom.Attribute
import zio.Chunk

sealed trait Dom { self =>
  def encode: String = {
    self match {
      case Dom.Empty                   => ""
      case Dom.Element(name, children) =>
        val attributes        = children.collect { case a @ Dom.Attribute(_, _) => a }
        val elements          = children.collect {
          case a @ Dom.Element(_, _) => a
          case a @ Dom.Text(_)       => a
        }
        val attributesEncoded = if (attributes.isEmpty) "" else " " + attributes.map(_.encode).mkString(" ")

        if (elements.isEmpty) s"<${name}${attributesEncoded}/>"
        else s"""<$name${attributesEncoded}>${elements.map(_.encode).mkString("")}</$name>"""
      case Dom.Text(value)             => value
      case Dom.Attribute(name, value)  =>
        value match {
          case Attribute.Constant(value)   => s"""${name}="${value}""""
          case Attribute.Collection(chunk) =>
            val right = chunk.map { case (name, value) => s"""${name}:${value}""" }.mkString(";")
            s"""${name}="${right}""""
        }
    }
  }
}

object Dom {
  def element(name: String, children: Dom*): Dom = Element(name, Chunk.fromIterable(children))
  def empty: Dom                                 = Empty
  def text(data: String): Dom                    = Text(data)

  private[zhttp] final case class Element(name: String, children: Chunk[Dom]) extends Dom

  private[zhttp] final case class Text(value: String) extends Dom

  private[zhttp] case class Attribute(name: String, value: Attribute.Value) extends Dom

  object Attribute {
    def apply(name: String, value: String): Attribute =
      Attribute(name, Attribute.Constant(value))

    def apply(name: String, value: (String, String)*): Attribute =
      Attribute(name, Attribute.Collection(Chunk.fromIterable(value)))

    sealed trait Value

    private[zhttp] final case class Constant(value: String) extends Value

    private[zhttp] final case class Collection(chunk: Chunk[(String, String)]) extends Value
  }

  private[zhttp] case object Empty extends Dom
}
