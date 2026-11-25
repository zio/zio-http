package zio.http.template2

import scala.language.implicitConversions

import zio.schema.Schema

/**
 * A value that has a CSS selector, with methods that build larger selectors from it. Elements, complete attributes (`Dom.CompleteAttribute`) and every [[CssSelector]] are `CssSelectable`, so `div > p` is a selector.
 */
trait CssSelectable {
  self =>

  /**
   * The CSS selector of this value.
   */
  val selector: CssSelector

  /** Returns the child selector `this > child`. */
  def >(child: CssSelector): CssSelector       = CssSelector.Child(selector, child.selector)

  /** Returns the descendant selector `this descendant`, with a space between the two. */
  def >>(descendant: CssSelector): CssSelector = CssSelector.Descendant(selector, descendant.selector)

  /** Returns the next-sibling selector `this + adjacent`. */
  def +(adjacent: CssSelector): CssSelector    = CssSelector.AdjacentSibling(selector, adjacent.selector)

  /** Returns a selector that matches both selectors, written together with nothing between them, such as `div.card`. */
  def &(other: CssSelector): CssSelector       = CssSelector.And(selector, other.selector)

  /** Returns the subsequent-sibling selector `this ~ sibling`. */
  def ~(sibling: CssSelector): CssSelector     = CssSelector.GeneralSibling(selector, sibling.selector)

  /** Returns the selector list `this, other`, which matches either selector. */
  def |(other: CssSelector): CssSelector       = CssSelector.Or(selector, other.selector)

  /** Returns `this:active`. */
  def active: CssSelector = CssSelector.PseudoClass(selector, "active")

  /** Returns the next-sibling selector `this + sel`, the same as `+`. */
  def adjacentSibling(sel: CssSelector): CssSelector = selector + sel

  /** Returns `this::after`. */
  def after: CssSelector = CssSelector.PseudoElement(selector, "after")

  /** Returns a selector that matches both selectors, the same as `&`. */
  def and(sel: CssSelector): CssSelector = selector & sel

  /** Returns `this::before`. */
  def before: CssSelector = CssSelector.PseudoElement(selector, "before")

  /** Returns the child selector `this > sel`, the same as `>`. */
  def child(sel: CssSelector): CssSelector = selector > sel

  /** Returns the descendant selector `this sel`, the same as `>>`. */
  def descendant(sel: CssSelector): CssSelector = selector >> sel

  /** Returns `this:first-child`. */
  def firstChild: CssSelector = CssSelector.PseudoClass(selector, "first-child")

  /** Returns `this::first-letter`. */
  def firstLetter: CssSelector = CssSelector.PseudoElement(selector, "first-letter")

  /** Returns `this::first-line`. */
  def firstLine: CssSelector = CssSelector.PseudoElement(selector, "first-line")

  /** Returns `this:focus`. */
  def focus: CssSelector = CssSelector.PseudoClass(selector, "focus")

  /** Returns the subsequent-sibling selector `this ~ sel`, the same as `~`. */
  def generalSibling(sel: CssSelector): CssSelector = selector ~ sel

  /** Returns `this:host`. */
  def host: CssSelector = CssSelector.PseudoClass(selector, "host")

  /** Returns `this:host(sel)`. */
  def host(sel: CssSelector): CssSelector = CssSelector.PseudoClass(selector, s"host(${sel.selector})")

  /** Returns `this:host-context(sel)`. */
  def hostContext(sel: CssSelector): CssSelector = CssSelector.PseudoClass(selector, s"host-context(${sel.selector})")

  /** Returns `this:hover`. */
  def hover: CssSelector = CssSelector.PseudoClass(selector, "hover")

  /** Returns `this:last-child`. */
  def lastChild: CssSelector = CssSelector.PseudoClass(selector, "last-child")

  /** Returns `this:not(sel)`. */
  def not(sel: CssSelector): CssSelector = CssSelector.Not(selector, sel.selector)

  /** Returns `this:nth-child(n)`. */
  def nthChild(n: Int): CssSelector = CssSelector.PseudoClass(selector, s"nth-child($n)")

  /** Returns `this:nth-child(formula)`, for a formula such as `2n+1`. */
  def nthChild(formula: String): CssSelector = CssSelector.PseudoClass(selector, s"nth-child($formula)")

  /** Returns the selector list `this, sel`, the same as `|`. */
  def or(sel: CssSelector): CssSelector = selector | sel

  /** Returns `this::part(name)`. */
  def part(name: String): CssSelector = CssSelector.PseudoElement(selector, s"part($name)")

  /** Returns `this::slotted(*)`. */
  def slotted: CssSelector = CssSelector.PseudoElement(selector, "slotted(*)")

  /** Returns `this::slotted(sel)`. */
  def slotted(sel: CssSelector): CssSelector = CssSelector.PseudoElement(selector, s"slotted(${sel.selector})")

  /** Returns `this:visited`. */
  def visited: CssSelector = CssSelector.PseudoClass(selector, "visited")

  /** Returns `this[attr]`, which matches when the attribute `attr` is present. */
  def withAttribute(attr: String): CssSelector = CssSelector.Attribute(selector, attr, None)

  /** Returns `this[attr="value"]`, which matches when the attribute `attr` equals `value`. */
  def withAttribute(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.Exact(value)))

  /** Returns `this[attr*="value"]`, which matches when the attribute `attr` contains `value`. */
  def withAttributeContaining(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.Contains(value)))

  /** Returns `this[attr\$="value"]`, which matches when the attribute `attr` ends with `value`. */
  def withAttributeEnding(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.EndsWith(value)))

  /**
   * Returns `this[attr|="value"]`, which matches when the attribute `attr` equals `value` or starts with `value` followed by `-`, as language codes such as `en-US` do.
   */
  def withAttributePrefix(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.HyphenPrefix(value)))

  /** Returns an attribute selector with the CSS starts-with operator, which matches when the attribute `attr` starts with `value`. */
  def withAttributeStarting(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.StartsWith(value)))

  /** Returns `this[attr~="value"]`, which matches when `value` is one of the space-separated words of the attribute `attr`. */
  def withAttributeWord(attr: String, value: String): CssSelector =
    CssSelector.Attribute(selector, attr, Some(CssSelector.AttributeMatch.WhitespaceContains(value)))
}

/**
 * The implicit conversion of a [[CssSelectable]] to its selector.
 */
object CssSelectable {

  /** Returns `s.selector`. */
  implicit def toSelector(s: CssSelectable): CssSelector = s.selector
}

/**
 * A CSS selector. Create one with the constructors of the companion object, with the `selector` string interpolator, or from an element, and combine selectors with the methods of [[CssSelectable]]. `render` and `toString` return the selector as CSS text.
 */
sealed trait CssSelector extends CssSelectable with Product with Serializable {

  /**
   * Returns this selector.
   */
  val selector: CssSelector = this

  /**
   * Returns this selector as CSS text, such as `nav > a.active`.
   */
  def render: String

  override def toString: String = render

}

/**
 * Constructors of [[CssSelector]], its node types, and its `Schema`.
 */
object CssSelector {

  /** Creates a selector from CSS text, which is rendered as given. */
  def raw(value: String): Raw = Raw(value)

  /** Creates the class selector `.name`. */
  def `class`(name: String): Class = Class(name)

  /** Creates the type selector for the tag name `tag`, such as `div`. */
  def element(tag: String): Element = Element(tag)

  /** Creates the id selector `#name`. */
  def id(name: String): Id = Id(name)

  /** Returns the universal selector `*`. */
  def universal: Universal.type = Universal

  /**
   * A `Schema` that encodes a selector as its rendered text and decodes a string as a `CssSelector.Raw`, without parsing it.
   */
  implicit val schema: Schema[CssSelector] =
    Schema[String].transform(
      str => CssSelector.raw(str),
      css => css.render,
    )

  /**
   * How an attribute selector compares the value of the attribute.
   */
  sealed trait AttributeMatch extends Product with Serializable

  /**
   * The comparisons of an attribute selector.
   */
  object AttributeMatch {

    /** Matches when the attribute value contains `value` (the `*=` operator). */
    final case class Contains(value: String)           extends AttributeMatch

    /** Matches when the attribute value ends with `value` (the `\$=` operator). */
    final case class EndsWith(value: String)           extends AttributeMatch

    /** Matches when the attribute value equals `value` (the `=` operator). */
    final case class Exact(value: String)              extends AttributeMatch

    /** Matches when the attribute value equals `value` or starts with `value` followed by `-` (the `|=` operator). */
    final case class HyphenPrefix(value: String)       extends AttributeMatch

    /** Matches when the attribute value starts with `value` (the CSS starts-with operator). */
    final case class StartsWith(value: String)         extends AttributeMatch

    /** Matches when `value` is one of the space-separated words of the attribute value (the `~=` operator). */
    final case class WhitespaceContains(value: String) extends AttributeMatch
  }

  /**
   * The next-sibling selector `previous + next`.
   */
  final case class AdjacentSibling(previous: CssSelector, next: CssSelector) extends CssSelector {

    /** Returns `previous + next`. */
    def render: String = s"$previous + $next"
  }

  /**
   * A selector that matches both `left` and `right`, written together with nothing between them.
   */
  final case class And(left: CssSelector, right: CssSelector) extends CssSelector {

    /** Returns `left` followed directly by `right`, such as `div.card`. */
    def render: String = s"$left$right"
  }

  /**
   * An attribute selector: `inner` followed by a test of the attribute `attribute`. With no `matcher`, the test is that the attribute is present.
   */
  final case class Attribute(inner: CssSelector, attribute: String, matcher: Option[AttributeMatch])
      extends CssSelector {

    /**
     * Returns `inner` followed by the attribute test in brackets, such as `a[href*="zio"]`, or `[attribute]` when there is no `matcher`.
     */
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

  /**
   * The child selector `parent > child`.
   */
  final case class Child(parent: CssSelector, child: CssSelector) extends CssSelector {

    /** Returns `parent > child`. */
    def render: String = s"$parent > $child"
  }

  /**
   * The class selector `.name`.
   */
  final case class Class(name: String) extends CssSelector {

    /** Returns `.name`. */
    def render: String = s".$name"
  }

  /**
   * The descendant selector `parent descendant`.
   */
  final case class Descendant(parent: CssSelector, descendant: CssSelector) extends CssSelector {

    /** Returns `parent` and `descendant` with a space between them. */
    def render: String = s"$parent $descendant"
  }

  /**
   * The type selector for the tag name `tag`.
   */
  final case class Element(tag: String) extends CssSelector {

    /** Returns `tag`. */
    def render: String = tag
  }

  /**
   * The subsequent-sibling selector `previous ~ sibling`.
   */
  final case class GeneralSibling(previous: CssSelector, sibling: CssSelector) extends CssSelector {

    /** Returns `previous ~ sibling`. */
    def render: String = s"$previous ~ $sibling"
  }

  /**
   * The id selector `#name`.
   */
  final case class Id(name: String) extends CssSelector {

    /** Returns `#name`. */
    def render: String = s"#$name"
  }

  /**
   * The selector `inner:not(negated)`.
   */
  final case class Not(inner: CssSelector, negated: CssSelector) extends CssSelector {

    /** Returns `inner:not(negated)`. */
    def render: String = s"$inner:not($negated)"
  }

  /**
   * The selector list `left, right`, which matches either selector. It is rendered without grouping, so a combinator applied to it is written after the whole list: `(a | b) > c` renders as `a, b > c`.
   */
  final case class Or(left: CssSelector, right: CssSelector) extends CssSelector {

    /** Returns `left, right`. */
    def render: String = s"$left, $right"
  }

  /**
   * The pseudo-class selector `inner:pseudoClass`.
   */
  final case class PseudoClass(inner: CssSelector, pseudoClass: String) extends CssSelector {

    /** Returns `inner:pseudoClass`. */
    def render: String = s"$inner:$pseudoClass"
  }

  /**
   * The pseudo-element selector `inner::pseudoElement`.
   */
  final case class PseudoElement(inner: CssSelector, pseudoElement: String) extends CssSelector {

    /** Returns `inner::pseudoElement`. */
    def render: String = s"$inner::$pseudoElement"
  }

  /**
   * A selector given as CSS text.
   */
  final case class Raw(value: String) extends CssSelector {

    /** Returns `value`. */
    def render: String = value
  }

  /**
   * The universal selector `*`.
   */
  case object Universal extends CssSelector {

    /** Returns `*`. */
    def render: String = "*"
  }
}
