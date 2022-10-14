package zio.http.html

import scala.language.implicitConversions
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

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

  implicit def fromOption(maybeElement: Option[Dom]): Html =
    maybeElement.fold(Html.Empty: Html)(Html.Single.apply)

  implicit def fromUnit(unit: Unit): Html = Html.Empty

  private[zio] case class Single(element: Dom) extends Html

  private[zio] final case class Multiple(children: Seq[Dom]) extends Html

  private[zio] case object Empty extends Html
}
