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
 * Modern DOM library for ZIO HTTP inspired by Laminar and ScalaTags. Provides
 * type-safe, composable, and transformable DOM trees.
 */
sealed trait Dom extends Modifier with Product with Serializable { self =>

  /**
   * Render the DOM to HTML string without indentation
   */
  def render: String = render(indentation = false)

  /**
   * Render the DOM to HTML string with optional indentation
   */
  def render(indentation: Boolean): String = {
    val state = if (indentation) RenderState.withIndentation else RenderState.noIndentation
    renderInternal(state).toString
  }

  /**
   * Transform this DOM node by applying a function
   */
  def transform(f: Dom => Dom): Dom = f(self)

  /**
   * Filter children recursively based on a predicate
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
   * Find the first element matching the predicate
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
   * Collect all elements matching the predicate
   */
  def collect(predicate: PartialFunction[Dom, Dom]): List[Dom] = {
    val current = if (predicate.isDefinedAt(self)) List(self) else Nil
    self match {
      case element: Dom.Element => current ++ element.children.flatMap(_.collect(predicate))
      case _                    => current
    }
  }

  private[template2] def renderInternal(state: RenderState): CharSequence

  /**
   * Render without HTML escaping (used for script/style content)
   */
  private[template2] def renderInternalRaw(state: RenderState): CharSequence = self match {
    case text: Dom.Text => text.content
    case _              => renderInternal(state)
  }
}

/**
 * Common trait for things that can be applied to elements (attributes and
 * children)
 */
sealed trait Modifier extends Product with Serializable {
  def modify(element: Dom.Element): Dom.Element = element.apply(this)
}

object Dom {

  val script: Element.Script                    = Element.Script()
  val style: Element.Style                      = Element.Style()
  implicit val schema: Schema[Dom]              =
    Schema[String].transform(raw, _.render)
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

  def element(tag: String): Element.Generic = Element.Generic(tag)

  def text(content: String): Text = Text(content)

  def raw(content: String): RawHtml = RawHtml(content)

  def empty: Dom = Empty

  def fragment(children: Dom*): Fragment = Fragment(children.toVector)

  def attr(name: String): PartialAttribute = PartialAttribute(name)

  def attr(name: String, value: AttributeValue): CompleteAttribute = CompleteAttribute(name, value)

  def boolAttr(name: String, enabled: Boolean = true): BooleanAttribute = BooleanAttribute(name, enabled)

  def multiAttr(name: String): PartialMultiAttribute =
    PartialMultiAttribute(name, AttributeSeparator.Space)

  def multiAttr(name: String, separator: AttributeSeparator): PartialMultiAttribute =
    PartialMultiAttribute(name, separator)

  def multiAttr(
    name: String,
    values: Iterable[String],
    separator: AttributeSeparator = AttributeSeparator.Space,
  ): CompleteAttribute =
    CompleteAttribute(name, AttributeValue.MultiValue(values.toVector, separator))

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

  sealed trait Element extends Dom with CssSelectable {

    override val selector: CssSelector =
      CssSelector.element(tag)

    /**
     * Apply modifiers (attributes and children) to this element
     */
    def apply(modifiers: Modifier*): Element

    /**
     * Add or update an attribute
     */
    def attr(name: String, value: AttributeValue): Element

    def attributes: Map[String, AttributeValue]

    def children: Vector[Dom]

    /**
     * Remove an attribute
     */
    def removeAttr(name: String): Element

    /**
     * Replace all children
     */
    def replaceChildren(children: Dom*): Element

    def tag: String

    /**
     * Add children conditionally
     */
    def when(condition: Boolean)(modifiers: Modifier*): Element

    /**
     * Add children if Option is defined
     */
    def whenSome[T](option: Option[T])(f: T => Seq[Modifier]): Element

    protected def shouldEscapeContent: Boolean

    protected def renderElementInternal(state: RenderState, escapeContent: Boolean): CharSequence = {
      val attributeString = if (attributes.nonEmpty) {
        val sb = ThreadLocals.stringBuilder
        attributes.foreach {
          case (name, AttributeValue.StringValue(v))                     =>
            sb.append(s""" $name="${htmlEscape(v)}"""")
          case (name, AttributeValue.MultiValue(values, separator))      =>
            if (values.nonEmpty) {
              val joinedValues = values.mkString(separator.render)
              sb.append(s""" $name="${htmlEscape(joinedValues)}"""")
            }
          case (name, value: AttributeValue.BooleanValue) if value.value =>
            sb.append(s""" $name""")
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

  sealed trait Attribute extends Modifier {
    def name: String
    def value: AttributeValue
  }

  sealed trait AttributeValue extends Product with Serializable

  final case class Text(content: String) extends Dom {
    private[template2] def renderInternal(state: RenderState): CharSequence =
      htmlEscape(content)
  }

  final case class RawHtml(content: String) extends Dom {
    private[template2] def renderInternal(state: RenderState): CharSequence = content
  }

  final case class Fragment(children: Iterable[Dom]) extends Dom {
    def apply(children: Dom*): Fragment =
      Fragment(this.children ++ children)

    private[template2] def renderInternal(state: RenderState): CharSequence =
      children.map(_.renderInternal(state)).mkString(state.separator)
  }

  /**
   * Complete attribute with name and value
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
   * Partial attribute that needs a value (like href := "...")
   */
  final case class PartialAttribute(name: String) {
    def :=(value: AttributeValue): CompleteAttribute = CompleteAttribute(name, value)
    def :=(value: String): CompleteAttribute         = CompleteAttribute(name, AttributeValue.StringValue(value))
    def :=(value: Boolean): CompleteAttribute        = CompleteAttribute(name, AttributeValue.BooleanValue(value))
    def :=(value: Int): CompleteAttribute    = CompleteAttribute(name, AttributeValue.StringValue(value.toString))
    def :=(value: Double): CompleteAttribute = CompleteAttribute(name, AttributeValue.StringValue(value.toString))

    def apply(value: AttributeValue): CompleteAttribute = CompleteAttribute(name, value)
    def apply(value: String): CompleteAttribute         = CompleteAttribute(name, AttributeValue.StringValue(value))
    def apply(value: Boolean): CompleteAttribute        = CompleteAttribute(name, AttributeValue.BooleanValue(value))
    def apply(value: Int): CompleteAttribute    = CompleteAttribute(name, AttributeValue.StringValue(value.toString))
    def apply(value: Double): CompleteAttribute = CompleteAttribute(name, AttributeValue.StringValue(value.toString))
  }

  /**
   * Boolean attribute (like disabled, checked, etc.)
   */
  final case class BooleanAttribute(name: String, enabled: Boolean = true) extends Attribute {
    def value: AttributeValue = AttributeValue.BooleanValue(enabled)
  }

  /**
   * Partial multi-value attribute that needs values (like class := ("foo",
   * "bar"))
   */
  final case class PartialMultiAttribute(name: String, separator: AttributeSeparator = AttributeSeparator.Space) {
    def :=(values: String*): CompleteAttribute             =
      CompleteAttribute(name, AttributeValue.MultiValue(values.toVector, separator))
    def :=(values: Iterable[String]): CompleteAttribute    =
      CompleteAttribute(name, AttributeValue.MultiValue(values.toVector, separator))
    def apply(values: String*): CompleteAttribute          =
      CompleteAttribute(name, AttributeValue.MultiValue(values.toVector, separator))
    def apply(values: Iterable[String]): CompleteAttribute =
      CompleteAttribute(name, AttributeValue.MultiValue(values.toVector, separator))
  }

  object Element {

    implicit def toCssSelector(partialElement: Element): CssSelector =
      partialElement.selector

    /**
     * Generic HTML element
     */
    final case class Generic(
      tag: String,
      attributes: Map[String, AttributeValue] = Map.empty,
      children: Vector[Dom] = Vector.empty,
    ) extends Element {

      def apply(modifiers: Modifier*): Generic = {
        val (attrs, kids) = modifiers.foldLeft((attributes, children)) {
          case ((currentAttrs, currentChildren), modifier) =>
            modifier match {
              case attr: Attribute => (currentAttrs + (attr.name -> attr.value), currentChildren)
              case child: Dom      => (currentAttrs, currentChildren :+ child)
            }
        }

        copy(attributes = attrs, children = kids)
      }

      def attr(name: String, value: AttributeValue): Generic =
        copy(attributes = attributes + (name -> value))

      def removeAttr(name: String): Generic =
        copy(attributes = attributes - name)

      def replaceChildren(children: Dom*): Generic =
        copy(children = children.toVector)

      def when(condition: Boolean)(modifiers: Modifier*): Generic =
        if (condition) apply(modifiers: _*) else this

      def whenSome[T](option: Option[T])(f: T => Seq[Modifier]): Generic =
        option.fold(this)(value => apply(f(value): _*))

      protected def shouldEscapeContent: Boolean = true

      private[template2] def renderInternal(state: RenderState): CharSequence = {
        renderElementInternal(state, shouldEscapeContent)
      }
    }

    /**
     * Script element with specialized methods for JavaScript
     */
    final case class Script(
      attributes: Map[String, AttributeValue] = Map.empty,
      children: Vector[Dom] = Vector.empty,
    ) extends Element {

      def tag: String = "script"

      def apply(modifiers: Modifier*): Script = {
        val (attrs, kids) = modifiers.foldLeft((attributes, children)) {
          case ((currentAttrs, currentChildren), modifier) =>
            modifier match {
              case attr: Attribute => (currentAttrs + (attr.name -> attr.value), currentChildren)
              case child: Dom      => (currentAttrs, currentChildren :+ child)
            }
        }
        copy(attributes = attrs, children = kids)
      }

      def apply(Js: Js, modifiers: Modifier*): Script = {
        val (attrs, kids) = modifiers.foldLeft((attributes, children)) {
          case ((currentAttrs, currentChildren), modifier) =>
            modifier match {
              case attr: Attribute => (currentAttrs + (attr.name -> attr.value), currentChildren)
              case child: Dom      => (currentAttrs, currentChildren :+ child)
            }
        }
        copy(attributes = attrs, children = kids :+ Dom.text(Js.value))
      }

      def async: Script = attr("async", true)

      def attr(name: String, value: AttributeValue): Script =
        copy(attributes = attributes + (name -> value))

      def crossOrigin(value: String): Script = attr("crossorigin", value)

      def defer: Script = attr("defer", true)

      def externalJs(url: String): Script = src(url).`type`("text/javascript")

      def externalModule(url: String): Script = src(url).`type`("module")

      def inlineJs(code: String): Script = `type`("text/javascript").javascript(code)

      def inlineJs(code: Js): Script = `type`("text/javascript").javascript(code)

      def integrity(value: String): Script = attr("integrity", value)

      def javascript(code: String): Script = apply(Dom.text(code))

      def javascript(code: Js): Script = apply(Dom.text(code.value))

      def module(code: String): Script = `type`("module").javascript(code)

      def module(code: Js): Script = `type`("module").javascript(code)

      def noModule: Script = attr("nomodule", true)

      def referrerPolicy(value: String): Script = attr("referrerpolicy", value)

      def removeAttr(name: String): Script =
        copy(attributes = attributes - name)

      def replaceChildren(children: Dom*): Script =
        copy(children = children.toVector)

      def src(url: String): Script = attr("src", url)

      def `type`(scriptType: String): Script = attr("type", scriptType)

      def when(condition: Boolean)(modifiers: Modifier*): Script =
        if (condition) apply(modifiers: _*) else this

      def whenSome[T](option: Option[T])(f: T => Seq[Modifier]): Script =
        option.fold(this)(value => apply(f(value): _*))

      protected def shouldEscapeContent: Boolean = false

      private[template2] def renderInternal(state: RenderState): CharSequence =
        renderElementInternal(state, shouldEscapeContent)
    }

    /**
     * Style element with specialized methods for CSS
     */
    final case class Style(
      attributes: Map[String, AttributeValue] = Map.empty,
      children: Vector[Dom] = Vector.empty,
    ) extends Element {

      def tag: String = "style"

      def apply(modifiers: Modifier*): Style = {
        val (attrs, kids) = modifiers.foldLeft((attributes, children)) {
          case ((currentAttrs, currentChildren), modifier) =>
            modifier match {
              case attr: Attribute => (currentAttrs + (attr.name -> attr.value), currentChildren)
              case child: Dom      => (currentAttrs, currentChildren :+ child)
            }
        }
        copy(attributes = attrs, children = kids)
      }

      def apply(Css: Css, modifiers: Modifier*): Style = {
        val (attrs, kids) = modifiers.foldLeft((attributes, children)) {
          case ((currentAttrs, currentChildren), modifier) =>
            modifier match {
              case attr: Attribute => (currentAttrs + (attr.name -> attr.value), currentChildren)
              case child: Dom      => (currentAttrs, currentChildren :+ child)
            }
        }
        copy(attributes = attrs, children = kids :+ Dom.text(Css.value))
      }

      def attr(name: String, value: AttributeValue): Style =
        copy(attributes = attributes + (name -> value))

      def css(code: String): Style = apply(Dom.text(code))

      def css(code: Css): Style = apply(Dom.text(code.value))

      def inlineCss(code: String): Style = `type`("text/css").css(code)

      def inlineCss(code: Css): Style = `type`("text/css").css(code.value)

      def media(value: String): Style = attr("media", value)

      def removeAttr(name: String): Style =
        copy(attributes = attributes - name)

      def replaceChildren(children: Dom*): Style =
        copy(children = children.toVector)

      def scoped: Style = attr("scoped", true)

      def `type`(value: String): Style = attr("type", value)

      def when(condition: Boolean)(modifiers: Modifier*): Style =
        if (condition) apply(modifiers: _*) else this

      def whenSome[T](option: Option[T])(f: T => Seq[Modifier]): Style =
        option.fold(this)(value => apply(f(value): _*))

      protected def shouldEscapeContent: Boolean = false

      private[template2] def renderInternal(state: RenderState): CharSequence =
        renderElementInternal(state, shouldEscapeContent)
    }
  }

  case object Empty extends Dom {
    private[template2] def renderInternal(state: RenderState): CharSequence = ""
  }

  object AttributeValue {
    implicit def fromString(value: String): AttributeValue = StringValue(value)

    implicit def fromBoolean(value: Boolean): AttributeValue = BooleanValue(value)

    implicit def fromInt(value: Int): AttributeValue = StringValue(String.valueOf(value))

    implicit def fromDouble(value: Double): AttributeValue = StringValue(String.valueOf(value))

    implicit def fromUrl(value: zio.http.URL): AttributeValue = StringValue(value.encode)

    implicit def fromUuid(value: java.util.UUID): AttributeValue = StringValue(value.toString)

    sealed trait BooleanValue extends AttributeValue with Serializable {
      def value: Boolean
    }

    final case class StringValue(value: String) extends AttributeValue {
      override def toString: String = value
    }

    /**
     * Multi-value attribute support for attributes like class, style, etc.
     */
    final case class MultiValue(values: Vector[String], separator: AttributeSeparator) extends AttributeValue {
      override def toString: String = values.mkString(separator.render)
    }

    object BooleanValue {
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
  }

}

/**
 * Attribute separator types for multi-value attributes
 */
sealed trait AttributeSeparator {
  def render: String
  override def toString: String = render
}

object AttributeSeparator {
  case object Space extends AttributeSeparator {
    def render: String = " "
  }

  case object Comma extends AttributeSeparator {
    def render: String = ","
  }

  case object Semicolon extends AttributeSeparator {
    def render: String = ";"
  }

  /**
   * Create a custom separator
   */
  final case class Custom(separator: String) extends AttributeSeparator {
    def render: String = separator
  }
}

private[template2] sealed trait RenderState {
  def separator: String
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
}
