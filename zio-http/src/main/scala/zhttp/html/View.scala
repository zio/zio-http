package zhttp.html

import scala.language.implicitConversions

/**
 * A view is a domain that used generate HTML.
 */
sealed trait View { self =>
  def encode: String = {
    self match {
      case View.Empty                        => ""
      case View.Single(element)              => element.encode
      case View.Multiple(elements: Seq[Dom]) => elements.map(_.encode).mkString("")
    }
  }
}

object View {
  implicit def fromString(string: String): View = View.Single(Dom.text(string))

  implicit def fromSeq(elements: Seq[Dom]): View = View.Multiple(elements)

  implicit def fromDomElement(element: Dom): View = View.Single(element)

  private[zhttp] case class Single(element: Dom) extends View

  private[zhttp] final case class Multiple(children: Seq[Dom]) extends View

  private[zhttp] case object Empty extends View
}
