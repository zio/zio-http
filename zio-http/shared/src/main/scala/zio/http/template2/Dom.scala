/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.template2

import scala.collection.immutable.ListMap
import scala.language.implicitConversions

import zio.schema.Schema

import zio.http.MediaType
import zio.http.codec.{BinaryCodecWithSchema, HttpContentCodec, TextBinaryCodec}
import zio.http.internal.ThreadLocals

/**
 * A node of an HTML document tree in the template2 DSL: an element, a text node, raw HTML, a fragment of several nodes, or nothing. Build nodes with the element values of `zio.http.template2` (`div`, `p`, ...) or with `Dom.element`, `Dom.text` and `Dom.raw`, and turn a tree into an HTML string with `render`. A `Dom` is also a [[Modifier]], so a node can be passed to an element as a child. Text nodes are rendered with `&`, `<`, `>`, `"` and `'` replaced by character references, except inside `script` and `style` elements.
 *
 * {{{
 * val card: Dom = div(b("Orders"), p("3 items & 1 coupon"))
 * card.render
 * // <div><b>Orders</b><p>3 items &amp; 1 coupon</p></div>
 * }}}
 *
 * @see [[https://ziohttp.com/reference/template/ Template DSL]]
 */
sealed trait Dom extends Modifier with Product with Serializable { self =>

  /**
   * Returns this node and its descendants for which `predicate` is defined, in depth-first order with this node first. It returns the matching nodes themselves: the result of `predicate` is not used. It looks inside the children of elements, but not inside a `Dom.Fragment`.
   */
  def collect(predicate: PartialFunction[Dom, Dom]): List[Dom] = {
    val current = if (predicate.isDefinedAt(self)) List(self) else Nil
    self match {
      case element: Dom.Element => current ++ element.children.flatMap(_.collect(predicate))
      case _                    => current
    }
  }

  /**
   * Returns a copy of this tree without the descendants for which `predicate` is false. On an element, it keeps the element itself, removes each child for which `predicate` is false, and filters the kept children the same way. On any other node, it returns the node when `predicate` is true and `Dom.Empty` otherwise. It does not look inside a `Dom.Fragment`: a kept fragment keeps all its children.
   */
  def filter(predicate: Dom => Boolean): Dom = self match {
    case element: Dom.Element =>
      element match {
        case gen: Dom.Element.Generic   =>
          gen.copy(children = gen.children.filter(predicate).map(_.filter(predicate)))
        case script: Dom.Element.Script =>
          script.copy(children = script.children.filter(predicate).map(_.filter(predicate)))
        case style: Dom.Element.Style   =>
          style.copy(children = style.children.filter(predicate).map(_.filter(predicate)))
      }
    case other                => if (predicate(other)) other else Dom.Empty
  }

  /**
   * Returns the first node for which `predicate` is true, in depth-first order with this node first, or `None`. It looks inside the children of elements, but not inside a `Dom.Fragment`.
   */
  def find(predicate: Dom => Boolean): Option[Dom] = {
    if (predicate(self)) Some(self)
    else
      self match {
        case element: Dom.Element => element.children.view.flatMap(_.find(predicate)).headOption
        case _                    => None
      }
  }

  /**
   * Returns true for `Dom.Empty` and for a `Dom.Fragment` whose children are all empty, including a fragment with no children. Returns false for the other nodes, including an element with no children and a text node with empty content.
   */
  def isEmpty: Boolean = self match {
    case Dom.Empty          => true
    case Dom.Fragment(kids) => kids.forall(_.isEmpty)
    case _                  => false
  }

  /**
   * Renders this tree to an HTML string, without adding line breaks or indentation. It is the same as `render(false)`.
   */
  def render: String = render(indentation = false)

  /**
   * Renders this tree to an HTML string. With `indentation = true`, the children of an element go on their own lines, indented by two spaces per level, unless the element's only child is a text node: then the element stays on one line. With `indentation = false`, no line breaks or indentation are added.
   */
  def render(indentation: Boolean): String = {
    val state = if (indentation) RenderState.withIndentation else RenderState.noIndentation
    renderInternal(state).toString
  }

  private[template2] def renderInternal(state: RenderState): CharSequence

  /**
   * Render without HTML escaping (used for script/style content)
   */
  private[template2] def renderInternalRaw(state: RenderState): CharSequence = self match {
    case text: Dom.Text => text.content
    case _              => renderInternal(state)
  }

  /**
   * Renders this tree to an HTML string, without adding line breaks or indentation. The output is the same as `render`.
   */
  def renderMinified: String = {
    val state = RenderState.Minified
    renderInternal(state).toString
  }

  /**
   * Returns the result of applying `f` to this node.
   */
  def transform(f: Dom => Dom): Dom = f(self)

}

/**
 * A value that can be applied to an element: an attribute (a [[Dom.Attribute]]), a child node (a [[Dom]]), or a group of modifiers. The `apply` methods of an element take modifiers, so attributes and children can be passed in one call. The implicit conversions of `zio.http.template2` turn a `String` into a text node, so a string can be passed where a modifier is expected.
 *
 * {{{
 * val mods: List[Modifier] = List(id := "cart", "2 items")
 * span(mods).render
 * // <span id="cart">2 items</span>
 * }}}
 *
 * @see [[https://ziohttp.com/reference/template/ Template DSL]]
 */
sealed trait Modifier extends Product with Serializable {

  /**
   * Applies this modifier to `element` and returns the new element. For an attribute or a node, it is the same as `element(this)`.
   */
  def modify(element: Dom.Element): Dom.Element = element.apply(this)
}

/**
 * The implicit conversion of a collection of modifiers to one [[Modifier]].
 */
object Modifier {
  private[template2] final case class IterableModifier(modifiers: Iterable[Modifier]) extends Modifier {
    override def modify(element: Dom.Element): Dom.Element = {
      if (modifiers.isEmpty) element
      else if (modifiers.size == 1) modifiers.head.modify(element)
      else if (modifiers.forall(_.isInstanceOf[Dom]))
        element.addChildren(modifiers.asInstanceOf[Iterable[Dom]])
      else {
        modifiers.partition(_.isInstanceOf[Dom]) match {
          case (doms, others) if others.forall(_.isInstanceOf[Dom.Attribute]) =>
            element
              .addAttributes(others.asInstanceOf[Iterable[Dom.Attribute]])
              .addChildren(doms.asInstanceOf[Iterable[Dom]])
          case (doms, others)                                                 =>
            others
              .foldLeft(element) { case (el, mod) => mod.modify(el) }
              .addChildren(doms.asInstanceOf[Iterable[Dom]])
        }
      }
    }
  }

  /**
   * Converts a collection of modifiers to one modifier. When it is passed to the `apply` method of an element, the attributes and nodes in `modifiers` are added to the element, and then every child that is equal to an earlier child of the element is removed.
   */
  implicit def iterableToModifier(modifiers: Iterable[Modifier]): Modifier =
    IterableModifier(modifiers)
}

/**
 * Constructors of [[Dom]] nodes and attributes, the node and attribute types, and the `Schema` and HTTP codec of `Dom`.
 */
object Dom {

  /**
   * An empty `script` element. Add content and attributes with its methods, such as `inlineJs` or `externalJs`.
   */
  val script: Element.Script                    = Element.Script()

  /**
   * An empty `style` element. Add CSS and attributes with its methods, such as `inlineCss`.
   */
  val style: Element.Style                      = Element.Style()

  /**
   * A `Schema` that encodes a `Dom` as its rendered HTML (`render`) and decodes a string as a `Dom.RawHtml` node, without parsing it.
   */
  implicit val schema: Schema[Dom]              =
    Schema[String].transform(raw, _.render)

  /**
   * An `HttpContentCodec` for the `text/html` media type, built from `schema`: it encodes a `Dom` as its rendered HTML and decodes a body as a `Dom.RawHtml` node.
   */
  implicit val htmlCodec: HttpContentCodec[Dom] = {
    HttpContentCodec(
      ListMap(
        MediaType.text.`html` ->
          BinaryCodecWithSchema.fromBinaryCodec(TextBinaryCodec.fromSchema(Schema[Dom])),
      ),
    )
  }
  private val VoidElements                      = Set(
    "area",
    "base",
    "br",
    "col",
    "embed",
    "hr",
    "img",
    "input",
    "link",
    "meta",
    "param",
    "source",
    "track",
    "wbr",
  )

  /**
   * Creates an element with the tag name `tag` and no attributes or children. Use it for tags that `zio.http.template2` has no value for, such as custom elements. The tag name is rendered as given. The void tags `area`, `base`, `br`, `col`, `embed`, `hr`, `img`, `input`, `link`, `meta`, `param`, `source`, `track` and `wbr`, in any letter case, render as a single self-closing tag that ends with `/>`, without their children.
   *
   * {{{
   * val badge = Dom.element("x-badge")(Dom.attr("level", "gold"), "VIP")
   * badge.render
   * // <x-badge level="gold">VIP</x-badge>
   * }}}
   *
   * @see [[https://ziohttp.com/reference/template/ Template DSL]]
   */
  def element(tag: String): Element.Generic = Element.Generic(tag)

  /**
   * Creates a text node. When it is rendered, the characters `&`, `<`, `>`, `"` and `'` are replaced by HTML character references. Inside a `script` or `style` element the text is rendered as is. The implicit conversions of `zio.http.template2` create a text node from a `String` passed where a [[Dom]] or a [[Modifier]] is expected.
   *
   * {{{
   * Dom.text("Tom & Jerry <3").render
   * // Tom &amp; Jerry &lt;3
   * }}}
   *
   * @see [[https://ziohttp.com/reference/template/ Template DSL]]
   */
  def text(content: String): Text = Text(content)

  /**
   * Creates a node whose `content` is rendered as is, without escaping. Use it for HTML that is already rendered, for example by another template engine.
   *
   * {{{
   * Dom.raw("<em>pre-rendered</em>").render
   * // <em>pre-rendered</em>
   * }}}
   *
   * @see [[https://ziohttp.com/reference/template/ Template DSL]]
   */
  def raw(content: String): RawHtml = RawHtml(content)

  /**
   * Returns `Dom.Empty`, a node that renders as an empty string. Use it where a branch of a template has nothing to show.
   *
   * {{{
   * def banner(message: Option[String]): Dom = message.fold(Dom.empty)(m => p(m))
   * div(banner(None)).render
   * // <div></div>
   * }}}
   *
   * @see [[https://ziohttp.com/reference/template/ Template DSL]]
   */
  def empty: Dom = Empty

  /**
   * Creates a `Dom.Fragment` that renders `children` one after another, with no element around them.
   */
  def fragment(children: Dom*): Fragment = Fragment(children.toVector)

  /**
   * Creates an attribute named `name` that still needs a value. Give it one with `:=` or `apply`, for example `Dom.attr("data-id") := "42"`.
   */
  def attr(name: String): PartialAttribute = PartialAttribute(name)

  /**
   * Creates an attribute named `name` with the value `value`.
   */
  def attr(name: String, value: AttributeValue): CompleteAttribute = CompleteAttribute(name, value)

  /**
   * Creates an attribute named `name` with the string value `value`. The value is rendered with `&`, `<`, `>`, `"` and `'` replaced by character references.
   */
  def attr(name: String, value: String): CompleteAttribute = CompleteAttribute(name, AttributeValue.StringValue(value))

  /**
   * Creates a boolean attribute such as `disabled`. When `enabled` is true it renders as its name alone, with no value; when it is false the attribute is not rendered.
   */
  def boolAttr(name: String, enabled: Boolean = true): BooleanAttribute = BooleanAttribute(name, enabled)

  /**
   * Creates an attribute named `name` that takes several values, joined by a space when it is rendered, for example `Dom.multiAttr("class") := ("card", "wide")`.
   */
  def multiAttr(name: String): PartialMultiAttribute =
    PartialMultiAttribute(name, AttributeSeparator.Space)

  /**
   * Creates an attribute named `name` that takes several values, joined by `separator` when it is rendered.
   */
  def multiAttr(name: String, separator: AttributeSeparator): PartialMultiAttribute =
    PartialMultiAttribute(name, separator)

  /**
   * Creates an attribute named `name` whose value is `values` joined by `separator`, a space by default. With no values, the attribute is not rendered.
   */
  def multiAttr(
    name: String,
    values: Iterable[String],
    separator: AttributeSeparator = AttributeSeparator.Space,
  ): CompleteAttribute =
    CompleteAttribute(name, AttributeValue.MultiValue(values.toVector, separator))

  /**
   * Creates an attribute named `name` whose value is `values` joined by `separator`. With no values, the attribute is not rendered.
   */
  def multiAttr(name: String, separator: AttributeSeparator, values: String*): CompleteAttribute =
    CompleteAttribute(name, AttributeValue.MultiValue(values.toVector, separator))

  private def htmlEscape(text: String): String = {
    text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#x27;")
  }

  /**
   * An HTML element: a tag name, attributes and child nodes. The methods that add or change attributes and children return a new element. Its `selector` is the CSS type selector of its tag, and an element converts implicitly to a [[CssSelector]].
   */
  sealed trait Element extends Dom with CssSelectable {

    override val selector: CssSelector =
      CssSelector.element(tag)

    /**
     * Returns this element with `attributes` added. An attribute replaces an existing attribute with the same name.
     */
    def addAttributes(attributes: Iterable[Attribute]): Element

    /**
     * Returns this element with the given attributes added. An attribute replaces an existing attribute with the same name.
     */
    def addAttributes(attribute: Attribute, attributes: Attribute*): Element =
      addAttributes(attribute +: attributes)

    /**
     * Returns this element with `children` added after its existing children.
     */
    def addChildren(children: Iterable[Dom]): Element =
      replaceChildren(this.children ++ children: _*)

    /**
     * Returns this element with the given nodes added after its existing children.
     */
    def addChildren(child: Dom, children: Dom*): Element =
      addChildren(child +: children)

    /**
     * Returns this element with the modifiers applied in order: an attribute is added, replacing an attribute with the same name, and a node is added as the last child.
     */
    def apply(modifier: Modifier, modifiers: Modifier*): Element =
      apply(modifier +: modifiers)

    /**
     * Returns this element with `modifiers` applied in order: an attribute is added, replacing an attribute with the same name, and a node is added as the last child.
     */
    def apply(modifiers: Iterable[Modifier]): Element

    /**
     * Returns this element with the attribute `name` set to `value`, replacing an existing value.
     */
    def attr(name: String, value: AttributeValue): Element

    /**
     * Returns this element with the attribute `name` set to the string `value`, replacing an existing value.
     */
    def attr(name: String, value: String): Element

    /**
     * The attributes of this element, by name.
     */
    def attributes: Map[String, AttributeValue]

    /**
     * The child nodes of this element, in order.
     */
    def children: Vector[Dom]

    /**
     * Returns this element without the attribute `name`.
     */
    def removeAttr(name: String): Element

    /**
     * Returns this element with its children replaced by `children`.
     */
    def replaceChildren(children: Dom*): Element

    /**
     * The tag name of this element, such as `div`.
     */
    def tag: String

    /**
     * Returns this element with `modifiers` applied when `condition` is true, and this element unchanged otherwise.
     */
    def when(condition: Boolean)(modifiers: Modifier*): Element

    /**
     * Returns this element with the modifiers `f(value)` applied when `option` is `Some(value)`, and this element unchanged when it is `None`.
     */
    def whenSome[T](option: Option[T])(f: T => Seq[Modifier]): Element

    protected def shouldEscapeContent: Boolean

    protected def renderElementInternal(state: RenderState, escapeContent: Boolean): CharSequence = {
      val attributeString = if (attributes.nonEmpty) {
        val sb = ThreadLocals.stringBuilder
        attributes.foreach {
          case (name, s: AttributeValue.StringValue)                     =>
            sb.append(s""" $name="${htmlEscape(s.value)}"""")
          case (name, m: AttributeValue.MultiValue)                      =>
            if (m.values.nonEmpty) {
              val joinedValues = m.values.mkString(m.separator.render)
              sb.append(s""" $name="${htmlEscape(joinedValues)}"""")
            }
          case (name, value: AttributeValue.BooleanValue) if value.value =>
            sb.append(s""" $name""")
          case (name, value: AttributeValue.JsValue)                     =>
            sb.append(s""" $name="$value"""")
          case _                                                         =>
        }
        sb.toString
      } else ""

      if (VoidElements.contains(tag.toLowerCase)) {
        s"<$tag$attributeString/>"
      } else if (children.isEmpty) {
        s"<$tag$attributeString></$tag>"
      } else {

        children match {
          case Vector(singleText: Text) =>
            val textContent = if (escapeContent) singleText.renderInternal(state) else singleText.content
            s"<$tag$attributeString>$textContent</$tag>"
          case _                        =>
            val childState   = state.indent
            val childrenHtml = children
              .map(child =>
                if (escapeContent) child.renderInternal(childState)
                else child.renderInternalRaw(childState),
              )
              .mkString(childState.separator)
            s"<$tag$attributeString>${childState.separator}$childrenHtml${state.separator}</$tag>"
        }
      }
    }
  }

  /**
   * An HTML attribute: a name and a value. An attribute is a [[Modifier]], so it is passed to an element together with the element's children, and it replaces an earlier attribute of that element with the same name. The two kinds are `Dom.CompleteAttribute` and `Dom.BooleanAttribute`; the attribute values of `zio.http.template2`, such as `id` and `href`, create them with `:=`.
   *
   * {{{
   * val attrs: List[Dom.Attribute] = List(Dom.attr("lang", "fa"), Dom.boolAttr("hidden"))
   * div(attrs).render
   * // <div lang="fa" hidden></div>
   * }}}
   *
   * @see [[https://ziohttp.com/reference/template/ Template DSL]]
   */
  sealed trait Attribute extends Modifier {

    /**
     * The attribute name, such as `href`.
     */
    def name: String

    /**
     * The attribute value.
     */
    def value: AttributeValue
  }

  /**
   * A text node. Its `content` is rendered with `&`, `<`, `>`, `"` and `'` replaced by character references, except inside a `script` or `style` element, where it is rendered as is.
   */
  final case class Text(content: String) extends Dom {
    private[template2] def renderInternal(state: RenderState): CharSequence =
      htmlEscape(content)
  }

  /**
   * A node whose `content` is rendered as is, without escaping.
   */
  final case class RawHtml(content: String) extends Dom {
    private[template2] def renderInternal(state: RenderState): CharSequence = content
  }

  /**
   * A group of nodes that are rendered one after another, with no element around them.
   */
  final case class Fragment(children: Iterable[Dom]) extends Dom {

    /**
     * Returns a fragment with `children` added after the existing children.
     */
    def apply(children: Dom*): Fragment =
      Fragment(this.children ++ children)

    private[template2] def renderInternal(state: RenderState): CharSequence =
      children.map(_.renderInternal(state)).mkString(state.separator)
  }

  /**
   * An attribute with a name and a value. Its `selector` is `.value` for the `class` attribute, `#value` for `id`, and `*[name="value"]` for other names, with the value as it renders.
   */
  final case class CompleteAttribute(name: String, value: AttributeValue) extends Attribute with CssSelectable {
    override val selector: CssSelector =
      name match {
        case "class" => CssSelector.`class`(value.toString)
        case "id"    => CssSelector.id(value.toString)
        case _       => CssSelector.Universal.withAttribute(name, value.toString)
      }
  }

  /**
   * An attribute name that still needs a value, as in `href := "/docs"`. The `:=` and `apply` methods return the complete attribute.
   */
  final case class PartialAttribute(name: String) {

    /** Returns this attribute with the value `value`. */
    def :=(value: AttributeValue): CompleteAttribute = CompleteAttribute(name, value)

    /**
     * Returns this attribute with a boolean value: when `value` is true it renders as its name alone, and when it is false it is not rendered.
     */
    def :=(value: Boolean): CompleteAttribute        = CompleteAttribute(name, AttributeValue.BooleanValue(value))

    /** Returns this attribute with `value.toString` as its string value. */
    def :=(value: Double): CompleteAttribute = CompleteAttribute(name, AttributeValue.StringValue(value.toString))

    /** Returns this attribute with `value.toString` as its string value. */
    def :=(value: Int): CompleteAttribute    = CompleteAttribute(name, AttributeValue.StringValue(value.toString))

    /** Returns this attribute with the JavaScript code `value`, for event handlers such as `onclick`. */
    def :=(value: Js): CompleteAttribute     = CompleteAttribute(name, AttributeValue.JsValue(value))

    /** Returns this attribute with the string value `value`. */
    def :=(value: String): CompleteAttribute = CompleteAttribute(name, AttributeValue.StringValue(value))

    /** Returns this attribute with the value `value`. */
    def apply(value: AttributeValue): CompleteAttribute = CompleteAttribute(name, value)

    /** Returns this attribute with the string value `value`. */
    def apply(value: String): CompleteAttribute         = CompleteAttribute(name, AttributeValue.StringValue(value))

    /**
     * Returns this attribute with a boolean value: when `value` is true it renders as its name alone, and when it is false it is not rendered.
     */
    def apply(value: Boolean): CompleteAttribute        = CompleteAttribute(name, AttributeValue.BooleanValue(value))

    /** Returns this attribute with `value.toString` as its string value. */
    def apply(value: Int): CompleteAttribute    = CompleteAttribute(name, AttributeValue.StringValue(value.toString))

    /** Returns this attribute with `value.toString` as its string value. */
    def apply(value: Double): CompleteAttribute = CompleteAttribute(name, AttributeValue.StringValue(value.toString))
  }

  /**
   * A boolean attribute, such as `disabled` or `checked`. When `enabled` is true it renders as its name alone; when it is false it is not rendered.
   */
  final case class BooleanAttribute(name: String, enabled: Boolean = true) extends Attribute {

    /**
     * Returns `enabled` as an `AttributeValue.BooleanValue`.
     */
    def value: AttributeValue = AttributeValue.BooleanValue(enabled)
  }

  /**
   * An attribute name that still needs one or more values, as in `className := ("foo", "bar")`. The values are joined by `separator` when the attribute is rendered.
   */
  final case class PartialMultiAttribute(name: String, separator: AttributeSeparator = AttributeSeparator.Space) {

    /** Returns this attribute with the values `values`. */
    def :=(values: String*): CompleteAttribute             =
      CompleteAttribute(name, AttributeValue.MultiValue(values.toVector, separator))

    /** Returns this attribute with the values `values`. */
    def :=(values: Iterable[String]): CompleteAttribute    =
      CompleteAttribute(name, AttributeValue.MultiValue(values.toVector, separator))

    /** Returns this attribute with the values `values`. */
    def apply(values: String*): CompleteAttribute          =
      CompleteAttribute(name, AttributeValue.MultiValue(values.toVector, separator))

    /** Returns this attribute with the values `values`. */
    def apply(values: Iterable[String]): CompleteAttribute =
      CompleteAttribute(name, AttributeValue.MultiValue(values.toVector, separator))
  }

  /**
   * The element types, and the implicit conversion of an element to its CSS selector.
   */
  object Element {

    /**
     * Converts an element to its CSS type selector, so an element such as `div` can be used where a [[CssSelector]] is expected.
     */
    implicit def toCssSelector(partialElement: Element): CssSelector =
      partialElement.selector

    /**
     * An element with any tag name. Its text children are rendered with `&`, `<`, `>`, `"` and `'` replaced by character references.
     */
    final case class Generic(
      tag: String,
      attributes: Map[String, AttributeValue] = Map.empty,
      children: Vector[Dom] = Vector.empty,
    ) extends Element {

      /**
       * Returns this element with `attributes` added. An attribute replaces an existing attribute with the same name.
       */
      def addAttributes(attributes: Iterable[Attribute]): Generic =
        copy(
          attributes = this.attributes ++ attributes.map(attr => attr.name -> attr.value),
        )

      override def apply(modifier: Modifier, modifiers: Modifier*): Generic =
        apply(modifier +: modifiers)

      /**
       * Returns this element with `modifiers` applied in order: an attribute is added, replacing an attribute with the same name, and a node is added as the last child.
       */
      def apply(modifiers: Iterable[Modifier]): Generic = {
        val (attrs, kids) = modifiers.foldLeft((attributes, children)) {
          case ((currentAttrs, currentChildren), modifier) =>
            modifier match {
              case attr: Attribute               => (currentAttrs + (attr.name -> attr.value), currentChildren)
              case child: Dom                    => (currentAttrs, currentChildren :+ child)
              case it: Modifier.IterableModifier =>
                val applied = it.modify(copy(attributes = currentAttrs, children = currentChildren))
                (applied.attributes, applied.children.distinct)
            }
        }

        copy(attributes = attrs, children = kids)
      }

      /**
       * Returns this element with the attribute `name` set to `value`, replacing an existing value.
       */
      def attr(name: String, value: AttributeValue): Generic =
        copy(attributes = attributes + (name -> value))

      /**
       * Returns this element with the attribute `name` set to the string `value`, replacing an existing value.
       */
      def attr(name: String, value: String): Generic =
        copy(attributes = attributes + (name -> AttributeValue.StringValue(value)))

      /**
       * Returns this element without the attribute `name`.
       */
      def removeAttr(name: String): Generic =
        copy(attributes = attributes - name)

      /**
       * Returns this element with its children replaced by `children`.
       */
      def replaceChildren(children: Dom*): Generic =
        copy(children = children.toVector)

      /**
       * Returns this element with `modifiers` applied when `condition` is true, and this element unchanged otherwise.
       */
      def when(condition: Boolean)(modifiers: Modifier*): Generic =
        if (condition) apply(modifiers) else this

      /**
       * Returns this element with the modifiers `f(value)` applied when `option` is `Some(value)`, and this element unchanged when it is `None`.
       */
      def whenSome[T](option: Option[T])(f: T => Seq[Modifier]): Generic =
        option.fold(this)(value => apply(f(value)))

      protected def shouldEscapeContent: Boolean = true

      private[template2] def renderInternal(state: RenderState): CharSequence = {
        renderElementInternal(state, shouldEscapeContent)
      }
    }

    /**
     * A `script` element, with methods that set its JavaScript content and its attributes. Its text children are rendered as is, without escaping.
     */
    final case class Script(
      attributes: Map[String, AttributeValue] = Map.empty,
      children: Vector[Dom] = Vector.empty,
    ) extends Element {

      /**
       * Returns `script`.
       */
      def tag: String = "script"

      /**
       * Returns this element with `attributes` added. An attribute replaces an existing attribute with the same name.
       */
      def addAttributes(attributes: Iterable[Attribute]): Script =
        copy(
          attributes = this.attributes ++ attributes.map(attr => attr.name -> attr.value),
        )

      override def apply(modifier: Modifier, modifiers: Modifier*): Script =
        apply(modifier +: modifiers)

      override def apply(modifiers: Iterable[Modifier]): Script = {
        val (attrs, kids) = modifiers.foldLeft((attributes, children)) {
          case ((currentAttrs, currentChildren), modifier) =>
            modifier match {
              case attr: Attribute               => (currentAttrs + (attr.name -> attr.value), currentChildren)
              case child: Dom                    => (currentAttrs, currentChildren :+ child)
              case it: Modifier.IterableModifier =>
                val modified = it.modify(this.copy(attributes = currentAttrs, children = currentChildren))
                (modified.attributes, modified.children.distinct)
            }
        }
        copy(attributes = attrs, children = kids)
      }

      /**
       * Returns this element with the code `Js` added as the last text child, and then `modifiers` applied.
       */
      def apply(Js: Js, modifiers: Modifier*): Script = {
        copy(children = children :+ Dom.text(Js.value)).apply(modifiers)
      }

      /**
       * Returns this element with the boolean attribute `async`.
       */
      def async: Script = attr("async", true)

      /**
       * Returns this element with the attribute `name` set to `value`, replacing an existing value.
       */
      def attr(name: String, value: AttributeValue): Script =
        copy(attributes = attributes + (name -> value))

      /**
       * Returns this element with the attribute `name` set to the string `value`, replacing an existing value.
       */
      def attr(name: String, value: String): Script =
        copy(attributes = attributes + (name -> AttributeValue.StringValue(value)))

      /**
       * Returns this element with the `crossorigin` attribute set to `value`, such as `anonymous`.
       */
      def crossOrigin(value: String): Script = attr("crossorigin", AttributeValue.StringValue(value))

      /**
       * Returns this element with the boolean attribute `defer`.
       */
      def defer: Script = attr("defer", true)

      /**
       * Returns this element with `src` set to `url` and `type` set to `text/javascript`.
       */
      def externalJs(url: String): Script = src(url).`type`("text/javascript")

      /**
       * Returns this element with `src` set to `url` and `type` set to `module`.
       */
      def externalModule(url: String): Script = src(url).`type`("module")

      /**
       * Returns this element with `type` set to `text/javascript` and `code` added as the last text child.
       */
      def inlineJs(code: String): Script = `type`("text/javascript").javascript(code)

      /**
       * Returns this element with `type` set to `text/javascript` and `code` added as the last text child.
       */
      def inlineJs(code: Js): Script = `type`("text/javascript").javascript(code)

      /**
       * Returns a `script` element with `type` set to `text/javascript` and the text of the classpath resource `file`, read with `classLoader`, as its content. The result starts from an empty `script` element: the attributes and children of this element are not kept. The resource is read when the method is called; when it does not exist, the method throws a `java.io.FileNotFoundException` on Scala 2.13 and 3, and a `java.lang.NullPointerException` on Scala 2.12.
       */
      def inlineResource(
        file: String,
        classLoader: ClassLoader = Thread.currentThread().getContextClassLoader,
      ): Dom.Element.Script = {
        val source = scala.io.Source.fromResource(file, classLoader)
        try script.inlineJs(source.mkString)
        finally source.close()
      }

      /**
       * Returns this element with the `integrity` attribute set to `value`, a hash such as `sha384-...`.
       */
      def integrity(value: String): Script = attr("integrity", AttributeValue.StringValue(value))

      /**
       * Returns this element with `code` added as the last text child.
       */
      def javascript(code: String): Script = apply(Dom.text(code))

      /**
       * Returns this element with `code` added as the last text child.
       */
      def javascript(code: Js): Script = apply(Dom.text(code.value))

      /**
       * Returns this element with `type` set to `module` and `code` added as the last text child.
       */
      def module(code: String): Script = `type`("module").javascript(code)

      /**
       * Returns this element with `type` set to `module` and `code` added as the last text child.
       */
      def module(code: Js): Script = `type`("module").javascript(code)

      /**
       * Returns this element with the boolean attribute `nomodule`.
       */
      def noModule: Script = attr("nomodule", true)

      /**
       * Returns this element with the `referrerpolicy` attribute set to `value`, such as `no-referrer`.
       */
      def referrerPolicy(value: String): Script = attr("referrerpolicy", AttributeValue.StringValue(value))

      /**
       * Returns this element without the attribute `name`.
       */
      def removeAttr(name: String): Script =
        copy(attributes = attributes - name)

      /**
       * Returns this element with its children replaced by `children`.
       */
      def replaceChildren(children: Dom*): Script =
        copy(children = children.toVector)

      /**
       * Returns this element with the `src` attribute set to `url`.
       */
      def src(url: String): Script = attr("src", AttributeValue.StringValue(url))

      /**
       * Returns this element with the `type` attribute set to `scriptType`, such as `module`.
       */
      def `type`(scriptType: String): Script = attr("type", AttributeValue.StringValue(scriptType))

      /**
       * Returns this element with `modifiers` applied when `condition` is true, and this element unchanged otherwise.
       */
      def when(condition: Boolean)(modifiers: Modifier*): Script =
        if (condition) apply(modifiers) else this

      /**
       * Returns this element with the modifiers `f(value)` applied when `option` is `Some(value)`, and this element unchanged when it is `None`.
       */
      def whenSome[T](option: Option[T])(f: T => Seq[Modifier]): Script =
        option.fold(this)(value => apply(f(value)))

      protected def shouldEscapeContent: Boolean = false

      private[template2] def renderInternal(state: RenderState): CharSequence =
        renderElementInternal(state, shouldEscapeContent)
    }

    /**
     * A `style` element, with methods that set its CSS content and its attributes. Its text children are rendered as is, without escaping.
     */
    final case class Style(
      attributes: Map[String, AttributeValue] = Map.empty,
      children: Vector[Dom] = Vector.empty,
    ) extends Element {

      /**
       * Returns `style`.
       */
      def tag: String = "style"

      /**
       * Returns this element with `attributes` added. An attribute replaces an existing attribute with the same name.
       */
      def addAttributes(attributes: Iterable[Attribute]): Style =
        copy(
          attributes = this.attributes ++ attributes.map(attr => attr.name -> attr.value),
        )

      override def apply(modifier: Modifier, modifiers: Modifier*): Style =
        apply(modifier +: modifiers)

      /**
       * Returns this element with `modifiers` applied in order: an attribute is added, replacing an attribute with the same name, and a node is added as the last child.
       */
      def apply(modifiers: Iterable[Modifier]): Style = {
        val (attrs, kids) = modifiers.foldLeft((attributes, children)) {
          case ((currentAttrs, currentChildren), modifier) =>
            modifier match {
              case attr: Attribute               => (currentAttrs + (attr.name -> attr.value), currentChildren)
              case child: Dom                    => (currentAttrs, currentChildren :+ child)
              case it: Modifier.IterableModifier =>
                val applied = it.modify(copy(attributes = currentAttrs, children = currentChildren))
                (applied.attributes, applied.children.distinct)
            }
        }
        copy(attributes = attrs, children = kids)
      }

      /**
       * Returns this element with `modifiers` applied, and then the code `Css` added as the last text child.
       */
      def apply(Css: Css, modifiers: Modifier*): Style = {
        val (attrs, kids) = modifiers.foldLeft((attributes, children)) {
          case ((currentAttrs, currentChildren), modifier) =>
            modifier match {
              case attr: Attribute               => (currentAttrs + (attr.name -> attr.value), currentChildren)
              case child: Dom                    => (currentAttrs, currentChildren :+ child)
              case it: Modifier.IterableModifier =>
                val applied = it.modify(copy(attributes = currentAttrs, children = currentChildren))
                (applied.attributes, applied.children.distinct)
            }
        }
        copy(attributes = attrs, children = kids :+ Dom.text(Css.value))
      }

      /**
       * Returns this element with the attribute `name` set to `value`, replacing an existing value.
       */
      def attr(name: String, value: AttributeValue): Style =
        copy(attributes = attributes + (name -> value))

      /**
       * Returns this element with the attribute `name` set to the string `value`, replacing an existing value.
       */
      def attr(name: String, value: String): Style =
        copy(attributes = attributes + (name -> AttributeValue.StringValue(value)))

      /**
       * Returns this element with `code` added as the last text child.
       */
      def css(code: String): Style = apply(Dom.text(code))

      /**
       * Returns this element with `code` added as the last text child.
       */
      def css(code: Css): Style = apply(Dom.text(code.value))

      /**
       * Returns this element with `type` set to `text/css` and `code` added as the last text child.
       */
      def inlineCss(code: String): Style = `type`("text/css").css(code)

      /**
       * Returns this element with `type` set to `text/css` and `code` added as the last text child.
       */
      def inlineCss(code: Css): Style = `type`("text/css").css(code.value)

      /**
       * Returns a `style` element with `type` set to `text/css` and the text of the classpath resource `file`, read with `classLoader`, as its content. The result starts from an empty `style` element: the attributes and children of this element are not kept. The resource is read when the method is called; when it does not exist, the method throws a `java.io.FileNotFoundException` on Scala 2.13 and 3, and a `java.lang.NullPointerException` on Scala 2.12.
       */
      def inlineResource(
        file: String,
        classLoader: ClassLoader = Thread.currentThread().getContextClassLoader,
      ): Dom.Element.Style = {
        val source = scala.io.Source.fromResource(file, classLoader)
        try style.inlineCss(source.mkString)
        finally source.close()
      }

      /**
       * Returns this element with the `media` attribute set to `value`, a media query such as `print`.
       */
      def media(value: String): Style = attr("media", AttributeValue.StringValue(value))

      /**
       * Returns this element without the attribute `name`.
       */
      def removeAttr(name: String): Style =
        copy(attributes = attributes - name)

      /**
       * Returns this element with its children replaced by `children`.
       */
      def replaceChildren(children: Dom*): Style =
        copy(children = children.toVector)

      /**
       * Returns this element with the boolean attribute `scoped`.
       */
      def scoped: Style = attr("scoped", true)

      /**
       * Returns this element with the `type` attribute set to `value`, such as `text/css`.
       */
      def `type`(value: String): Style = attr("type", AttributeValue.StringValue(value))

      /**
       * Returns this element with `modifiers` applied when `condition` is true, and this element unchanged otherwise.
       */
      def when(condition: Boolean)(modifiers: Modifier*): Style =
        if (condition) apply(modifiers) else this

      /**
       * Returns this element with the modifiers `f(value)` applied when `option` is `Some(value)`, and this element unchanged when it is `None`.
       */
      def whenSome[T](option: Option[T])(f: T => Seq[Modifier]): Style =
        option.fold(this)(value => apply(f(value)))

      protected def shouldEscapeContent: Boolean = false

      private[template2] def renderInternal(state: RenderState): CharSequence =
        renderElementInternal(state, shouldEscapeContent)
    }
  }

  /**
   * A node that renders as an empty string.
   */
  case object Empty extends Dom {
    private[template2] def renderInternal(state: RenderState): CharSequence = ""
  }

  /**
   * The value of an attribute: a string, several strings joined by a separator, a boolean, or JavaScript code.
   */
  sealed trait AttributeValue extends Product with Serializable

  /**
   * The attribute value types, and implicit conversions to `Dom.AttributeValue` from `Boolean`, `Int`, `Double`, `zio.http.URL` and `java.util.UUID`.
   */
  object AttributeValue {

    /**
     * Converts a `Boolean` to a `BooleanValue`.
     */
    implicit def fromBoolean(value: Boolean): AttributeValue = BooleanValue(value)

    /**
     * Converts an `Int` to a `StringValue` of its decimal digits.
     */
    implicit def fromInt(value: Int): AttributeValue = StringValue(String.valueOf(value))

    /**
     * Converts a `Double` to a `StringValue` of `String.valueOf(value)`, such as `2.5`.
     */
    implicit def fromDouble(value: Double): AttributeValue = StringValue(String.valueOf(value))

    /**
     * Converts a `URL` to a `StringValue` of its encoded form (`value.encode`), such as `/docs/a%20b?q=x+y`.
     */
    implicit def fromUrl(value: zio.http.URL): AttributeValue = StringValue(value.encode)

    /**
     * Converts a `UUID` to a `StringValue` of `value.toString`.
     */
    implicit def fromUuid(value: java.util.UUID): AttributeValue = StringValue(value.toString)

    /**
     * A boolean attribute value. When it is true, the attribute renders as its name alone; when it is false, the attribute is not rendered.
     */
    sealed trait BooleanValue extends AttributeValue with Serializable {

      /**
       * The boolean.
       */
      def value: Boolean
    }

    /**
     * The constructor of `BooleanValue`.
     */
    object BooleanValue {

      /**
       * Returns the `BooleanValue` of `value`.
       */
      def apply(value: Boolean): BooleanValue = if (value) True else False
      private[template2] case object True  extends BooleanValue {
        override def toString: String = "true"
        def value: Boolean            = true
      }
      private[template2] case object False extends BooleanValue {
        override def toString: String = "false"
        def value: Boolean            = false
      }
    }

    /**
     * An attribute value that holds JavaScript code, for an event handler such as `onclick`. It renders as `js.value`.
     */
    final case class JsValue(js: Js) extends AttributeValue {
      override def toString: String = js.value
    }

    /**
     * An attribute value made of several strings, for attributes such as `class` and `rel`. When it is rendered, `values` are joined by `separator`; with no values, the attribute is not rendered.
     */
    final case class MultiValue(values: Vector[String], separator: AttributeSeparator) extends AttributeValue {
      override def toString: String = values.mkString(separator.render)
    }

    /**
     * A string attribute value. It is rendered with `&`, `<`, `>`, `"` and `'` replaced by character references.
     */
    final case class StringValue(value: String) extends AttributeValue {
      override def toString: String = value
    }
  }

}

/**
 * Attribute separator types for multi-value attributes
 */
sealed trait AttributeSeparator {

  /**
   * The text placed between two values.
   */
  def render: String
  override def toString: String = render
}

/**
 * The separators `Space`, `Comma` and `Semicolon`, and `Custom` for any other text.
 */
object AttributeSeparator {

  /**
   * Joins values with a space.
   */
  case object Space extends AttributeSeparator {

    /** Returns a space. */
    def render: String = " "
  }

  /**
   * Joins values with a comma, without spaces.
   */
  case object Comma extends AttributeSeparator {

    /** Returns `,`. */
    def render: String = ","
  }

  /**
   * Joins values with a semicolon, without spaces.
   */
  case object Semicolon extends AttributeSeparator {

    /** Returns `;`. */
    def render: String = ";"
  }

  /**
   * A separator with any text, such as `" | "`.
   */
  final case class Custom(separator: String) extends AttributeSeparator {

    /** Returns `separator`. */
    def render: String = separator
  }
}

private[template2] sealed trait RenderState {

  /** The text placed between two child nodes at this level. */
  def separator: String

  /** Returns the state for the children one level deeper. */
  def indent: RenderState
}

private[template2] object RenderState {
  val withIndentation: RenderState = WithIndentation(0, 2)

  def noIndentation: RenderState = NoIndentation

  final case class WithIndentation(level: Int, spaces: Int) extends RenderState {
    def separator: String   = "\n" + (" " * (level * spaces))
    def indent: RenderState = WithIndentation(level + 1, spaces)
  }

  case object NoIndentation extends RenderState {
    def separator: String   = ""
    def indent: RenderState = this
  }

  case object Minified extends RenderState {
    def separator: String   = ""
    def indent: RenderState = this
  }
}
