package zio.http.template2

import scala.language.implicitConversions

import zio.schema.Schema

trait CssSelectable {
  self =>
  val selector: CssSelector

  def >(child: CssSelector): CssSelector       = CssSelector.Child(selector, child.selector)
  def >>(descendant: CssSelector): CssSelector = CssSelector.Descendant(selector, descendant.selector)
  def +(adjacent: CssSelector): CssSelector    = CssSelector.AdjacentSibling(selector, adjacent.selector)
  def &(other: CssSelector): CssSelector       = CssSelector.And(selector, other.selector)
  def ~(sibling: CssSelector): CssSelector     = CssSelector.GeneralSibling(selector, sibling.selector)
  def |(other: CssSelector): CssSelector       = CssSelector.Or(selector, other.selector)

  def active: CssSelector = CssSelector.PseudoClass(selector, "active")

  def adjacentSibling(sel: CssSelector): CssSelector = selector + sel

  def after: CssSelector = CssSelector.PseudoElement(selector, "after")

  def and(sel: CssSelector): CssSelector = selector & sel

  def before: CssSelector = CssSelector.PseudoElement(selector, "before")

  def child(sel: CssSelector): CssSelector = selector > sel

  def descendant(sel: CssSelector): CssSelector = selector >> sel

  def firstChild: CssSelector = CssSelector.PseudoClass(selector, "first-child")

  def firstLetter: CssSelector = CssSelector.PseudoElement(selector, "first-letter")

  def firstLine: CssSelector = CssSelector.PseudoElement(selector, "first-line")

  def focus: CssSelector = CssSelector.PseudoClass(selector, "focus")

  def generalSibling(sel: CssSelector): CssSelector = selector ~ sel

  def host: CssSelector = CssSelector.PseudoClass(selector, "host")

  def host(sel: CssSelector): CssSelector = CssSelector.PseudoClass(selector, s"host(${sel.selector})")

  def hostContext(sel: CssSelector): CssSelector = CssSelector.PseudoClass(selector, s"host-context(${sel.selector})")

  def hover: CssSelector = CssSelector.PseudoClass(selector, "hover")

  def lastChild: CssSelector = CssSelector.PseudoClass(selector, "last-child")

  def not(sel: CssSelector): CssSelector = CssSelector.Not(selector, sel.selector)

  def nthChild(n: Int): CssSelector = CssSelector.PseudoClass(selector, s"nth-child($n)")

  def nthChild(formula: String): CssSelector = CssSelector.PseudoClass(selector, s"nth-child($formula)")

  def or(sel: CssSelector): CssSelector = selector | sel

  def part(name: String): CssSelector = CssSelector.PseudoElement(selector, s"part($name)")

  def slotted: CssSelector = CssSelector.PseudoElement(selector, "slotted(*)")

  def slotted(sel: CssSelector): CssSelector = CssSelector.PseudoElement(selector, s"slotted(${sel.selector})")

  def visited: CssSelector = CssSelector.PseudoClass(selector, "visited")

  def withAttribute(attr: String): CssSelector = CssSelector.Attribute(selector, attr, None)

  def withAttribute(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.Exact(value)))

  def withAttributeContaining(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.Contains(value)))

  def withAttributeEnding(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.EndsWith(value)))

  def withAttributePrefix(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.HyphenPrefix(value)))

  def withAttributeStarting(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.StartsWith(value)))

  def withAttributeWord(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.WhitespaceContains(value)))
}

object CssSelectable {
  implicit def toSelector(s: CssSelectable): CssSelector = s.selector
}

sealed trait CssSelector extends CssSelectable with Product with Serializable {

  val selector: CssSelector = this

  def render: String

  override def toString: String = render

}

object CssSelector {
  def raw(value: String): Raw = Raw(value)

  def `class`(name: String): Class = Class(name)

  def element(tag: String): Element = Element(tag)

  def id(name: String): Id = Id(name)

  def universal: Universal.type = Universal

  implicit val schema: Schema[CssSelector] =
    Schema[String].transform(
      str => CssSelector.raw(str),
      css => css.render,
    )

  sealed trait AttributeMatch extends Product with Serializable

  object AttributeMatch {
    final case class Contains(value: String)           extends AttributeMatch
    final case class EndsWith(value: String)           extends AttributeMatch
    final case class Exact(value: String)              extends AttributeMatch
    final case class HyphenPrefix(value: String)       extends AttributeMatch
    final case class StartsWith(value: String)         extends AttributeMatch
    final case class WhitespaceContains(value: String) extends AttributeMatch
  }

  final case class AdjacentSibling(previous: CssSelector, next: CssSelector) extends CssSelector {
    def render: String = s"$previous + $next"
  }

  final case class And(left: CssSelector, right: CssSelector) extends CssSelector {
    def render: String = s"$left$right"
  }

  final case class Attribute(inner: CssSelector, attribute: String, matcher: Option[AttributeMatch])
      extends CssSelector {
    def render: String = {
      val base     = if (inner.toString != "") inner.toString else ""
      val attrPart = matcher match {
        case None                                           => s"[$attribute]"
        case Some(AttributeMatch.Contains(value))           => s"""[$attribute*="$value"]"""
        case Some(AttributeMatch.EndsWith(value))           => s"""[$attribute$$="$value"]"""
        case Some(AttributeMatch.Exact(value))              => s"""[$attribute="$value"]"""
        case Some(AttributeMatch.HyphenPrefix(value))       => s"""[$attribute|="$value"]"""
        case Some(AttributeMatch.StartsWith(value))         => s"""[$attribute^="$value"]"""
        case Some(AttributeMatch.WhitespaceContains(value)) => s"""[$attribute~="$value"]"""
      }
      s"$base$attrPart"
    }
  }

  final case class Child(parent: CssSelector, child: CssSelector) extends CssSelector {
    def render: String = s"$parent > $child"
  }

  final case class Class(name: String) extends CssSelector {
    def render: String = s".$name"
  }

  final case class Descendant(parent: CssSelector, descendant: CssSelector) extends CssSelector {
    def render: String = s"$parent $descendant"
  }

  final case class Element(tag: String) extends CssSelector {
    def render: String = tag
  }

  final case class GeneralSibling(previous: CssSelector, sibling: CssSelector) extends CssSelector {
    def render: String = s"$previous ~ $sibling"
  }

  final case class Id(name: String) extends CssSelector {
    def render: String = s"#$name"
  }

  final case class Not(inner: CssSelector, negated: CssSelector) extends CssSelector {
    def render: String = s"$inner:not($negated)"
  }

  final case class Or(left: CssSelector, right: CssSelector) extends CssSelector {
    def render: String = s"$left, $right"
  }

  final case class PseudoClass(inner: CssSelector, pseudoClass: String) extends CssSelector {
    def render: String = s"$inner:$pseudoClass"
  }

  final case class PseudoElement(inner: CssSelector, pseudoElement: String) extends CssSelector {
    def render: String = s"$inner::$pseudoElement"
  }

  final case class Raw(value: String) extends CssSelector {
    def render: String = value
  }

  case object Universal extends CssSelector {
    def render: String = "*"
  }
}
