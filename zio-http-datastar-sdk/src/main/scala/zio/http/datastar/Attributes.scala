package zio.http.datastar

import java.time.Duration

import scala.annotation.nowarn
import scala.language.implicitConversions

import zio.schema._

import zio.http.template2.Dom.AttributeValue
import zio.http.template2._

/**
 * Builders for the Datastar `data-*` attributes. Each method returns a value that renders as an HTML attribute whose name starts with the package prefix, which is `data` in the `zio.http.datastar` package object.
 */
trait Attributes {
  self: DatastarPackageBase =>

  private[datastar] val prefix: String

  import Attributes._

  /**
   * data-attr:* – Set arbitrary attributes. Doc:
   * [[https://data-star.dev/reference/attributes#data-attr]]
   */
  final def dataAttr(attr: Dom.PartialAttribute): DatastarAttribute =
    DatastarAttribute(s"$prefix-attr:${attr.name}")

  /**
   * data-attr:* – Set arbitrary attributes. Doc:
   * [[https://data-star.dev/reference/attributes#data-attr]]
   */
  final def dataAttr(attr: Dom.PartialMultiAttribute): DatastarAttribute =
    DatastarAttribute(s"$prefix-attr:${attr.name}")

  /**
   * data-attr:* – Set arbitrary attributes. Doc:
   * [[https://data-star.dev/reference/attributes#data-attr]]
   */
  final def dataAttr(attr: String): DatastarAttribute =
    DatastarAttribute(s"$prefix-attr:$attr")

  /**
   * Returns an attribute named `data-attr` that takes a JavaScript expression, Datastar's form for setting several attributes from one expression.
   */
  final def dataAttr: DatastarAttribute = DatastarAttribute(s"$prefix-attr")

  /**
   * data-bind:* – Binds the given signal name to the value of input, select,
   * textarea or web components. Doc:
   * [[https://data-star.dev/reference/attributes#data-bind]]
   */
  final def dataBind: PartialDataBind = PartialDataBind(prefix)

  /**
   * data-bind:* – Binds the given signal name to the value of input, select,
   * textarea or web components. Doc:
   * [[https://data-star.dev/reference/attributes#data-bind]]
   */
  final def dataBind(caseModifier: CaseModifier): PartialDataBind =
    PartialDataBind(prefix, caseModifier)

  /**
   * data-class – Dynamically toggle classes. Doc:
   * [[https://data-star.dev/reference/attributes#data-class]]
   */
  final def dataClass(className: String): DataClass.Single =
    DataClass.Single(prefix, className)

  /**
   * data-class – Dynamically toggle classes. Doc:
   * [[https://data-star.dev/reference/attributes#data-class]]
   */
  final def dataClass: DataClass = DataClass.Multi(prefix)

  /**
   * data-class – Dynamically toggle classes. Doc:
   * [[https://data-star.dev/reference/attributes#data-class]]
   */
  final def dataClass(caseModifier: CaseModifier)(className: String): DataClass.Single =
    DataClass.Single(prefix, className, caseModifier)

  /**
   * data-computed – Computed values from expressions. Doc:
   * [[https://data-star.dev/reference/attributes#data-computed]]
   */
  final def dataComputed: PartialSignalAttr = PartialSignalAttr(s"$prefix-computed")

  /**
   * data-computed – Computed values from expressions. Doc:
   * [[https://data-star.dev/reference/attributes#data-computed]]
   */
  final def dataComputed(caseModifier: CaseModifier): PartialSignalAttr =
    PartialSignalAttr(s"$prefix-computed", caseModifier)

  /**
   * data-effect – Side effects from expressions. Doc:
   * [[https://data-star.dev/reference/attributes#data-effect]]
   */
  final def dataEffect: DatastarAttribute = DatastarAttribute(s"$prefix-effect")

  /**
   * data-ignore – Ignore this element and its children. Doc:
   * [[https://data-star.dev/reference/attributes#data-ignore]]
   */
  final def dataIgnore: Dom.Attribute = Dom.boolAttr(s"$prefix-ignore")

  /**
   * like data-ignore, but only ignores this element and not its children. Doc:
   * [[https://data-star.dev/reference/attributes#data-ignore]]
   */
  final def dataIgnoreSelf: Dom.Attribute =
    Dom.boolAttr(s"$prefix-ignore__self")

  /**
   * data-ignore-morph – Ignore morphing for this element. Doc:
   * [[https://data-star.dev/reference/attributes#data-ignore-morph]]
   */
  final def dataIgnoreMorph: Dom.Attribute = Dom.boolAttr(s"$prefix-ignore-morph")

  /**
   * data-indicator – Loading indicator. Doc:
   * [[https://data-star.dev/reference/attributes#data-indicator]]
   */
  final def dataIndicator: PartialDataIndicator =
    PartialDataIndicator(prefix)

  /**
   * data-json-signals – JSON signal declarations. Doc:
   * [[https://data-star.dev/reference/attributes#data-json-signals]]
   */
  final def dataJsonSignals: DataJsonSignals = DataJsonSignals(prefix)

  /**
   * data-on:* – Event listener. Doc:
   * [[https://data-star.dev/reference/attributes#data-on]]
   */
  final def dataOn: PartialDataOn = PartialDataOn(prefix, EventModifier.None)

  /**
   * data-on-intersect – Execute when element intersects viewport. Doc:
   * [[https://data-star.dev/reference/attributes#data-on-intersect]]
   */
  final def dataOnIntersect: DataOnIntersect = DataOnIntersect(prefix, IntersectModifier.None)

  /**
   * data-on-interval – Execute on interval. Doc:
   * [[https://data-star.dev/reference/attributes#data-on-interval]]
   */
  final def dataOnInterval: DataOnInterval = DataOnInterval(prefix, OnIntervalModifier.None)

  /**
   * data-init – Execute when element loads. Doc:
   * [[https://data-star.dev/reference/attributes#data-init]]
   */
  final def dataInit: DataInit = DataInit(prefix, InitModifier.None)

  /**
   * data-on-load – Execute when element loads (deprecated, use dataInit). Doc:
   * [[https://data-star.dev/reference/attributes#data-init]]
   *
   * Note: This is kept for binary compatibility. It generates the same
   * data-init attribute.
   */
  @deprecated("Use dataInit instead of dataOnLoad", "3.6.0")
  final def dataOnLoad: DataOnLoad = DataOnLoad(prefix, LoadModifier.None)

  /**
   * data-on-signal-patch – Execute when signal patches. Doc:
   * [[https://data-star.dev/reference/attributes#data-on-signal-patch]]
   */
  final def dataOnSignalPatch: DataOnSignalPatch = DataOnSignalPatch(prefix, OnSignalPatchModifier.None)

  /**
   * data-on-signal-patch-filter – Filter signal patch events. Doc:
   * [[https://data-star.dev/reference/attributes#data-on-signal-patch-filter]]
   */
  final def dataOnSignalPatchFilter: DatastarAttribute = DatastarAttribute(s"$prefix-on-signal-patch-filter")

  /**
   * data-preserve-attr – Preserve attributes during morphing. Doc:
   * [[https://data-star.dev/reference/attributes#data-preserve-attr]]
   */
  final def dataPreserveAttr(attribute: Dom.Attribute, attributes: Dom.Attribute*): Dom.Attribute =
    Dom.attr(s"$prefix-preserve-attr", AttributeValue.StringValue((attribute +: attributes).map(_.name).mkString(" ")))

  /**
   * data-preserve-attr – Preserve attributes during morphing. Doc:
   * [[https://data-star.dev/reference/attributes#data-preserve-attr]]
   */
  final def dataPreserveAttr(attribute: String, attributes: String*): Dom.Attribute =
    Dom.attr(s"$prefix-preserve-attr", AttributeValue.StringValue((attribute +: attributes).mkString(" ")))

  /**
   * data-ref – Assign a local reference. Doc:
   * [[https://data-star.dev/reference/attributes#data-ref]]
   */
  final def dataRef: PartialDataRef = PartialDataRef(prefix)

  /**
   * data-ref – Assign a local reference. Doc:
   * [[https://data-star.dev/reference/attributes#data-ref]]
   */
  final def dataRef(caseModifier: CaseModifier): PartialDataRef = PartialDataRef(prefix, caseModifier)

  /**
   * data-show – Show element when expression truthy. Doc:
   * [[https://data-star.dev/reference/attributes#data-show]]
   */
  final def dataShow: DatastarAttribute = DatastarAttribute(s"$prefix-show")

  /**
   * data-signals – Declare / expose signals. Doc:
   * [[https://data-star.dev/reference/attributes#data-signals]]
   */
  final def dataSignals: PartialSignalsAttr = PartialSignalsAttr(prefix)

  /**
   * data-style – Dynamically set inline style(s). Doc:
   * [[https://data-star.dev/reference/attributes#data-style]]
   */
  final def dataStyle: DatastarAttribute = DatastarAttribute(s"$prefix-style")

  /**
   * data-style:* – Dynamically set inline style(s). Doc:
   * [[https://data-star.dev/reference/attributes#data-style]]
   */
  final def dataStyle(styleName: String): DatastarAttribute = DatastarAttribute(s"$prefix-style:$styleName")

  /**
   * data-text – Sets element text content from an expression / signal. Doc:
   * [[https://data-star.dev/reference/attributes#data-text]]
   */
  final def dataText: DatastarAttribute = DatastarAttribute(s"$prefix-text")

}

/**
 * Attribute and modifier types that the builders in the `Attributes` trait return.
 */
object Attributes {
  import Dom._

  /**
   * A `data-class` attribute under construction. `:=` takes a JavaScript expression and returns the attribute.
   */
  sealed trait DataClass {
    private[datastar] def full: String

    /**
     * Returns the attribute whose value is the JavaScript expression `value`.
     */
    def :=(value: Js): CompleteAttribute = Dom.attr(full) := value.value
  }

  /**
   * The two forms of the `data-class` attribute.
   */
  object DataClass {

    /**
     * The `data-class` attribute without a class name; its expression is an object that maps class names to conditions.
     */
    final case class Multi(prefix: String) extends DataClass {
      private[datastar] val full = s"$prefix-class"
    }

    private val defaultCaseModifier = CaseModifier.Kebab

    /**
     * The `data-class:<className>` attribute, which toggles one class. Throws a `java.lang.AssertionError` when `className` is blank. The default kebab case adds nothing to the attribute name; the other cases add `__case.<case>`.
     */
    final case class Single(prefix: String, className: String, caseModifier: CaseModifier = defaultCaseModifier)
        extends DataClass {
      assert(!className.isBlank, "Class name cannot be empty")
      private[datastar] val full = s"$prefix-class:$className${caseModifier.suffix(defaultCaseModifier)}"

      /**
       * Returns the attribute with the reference to `signal` (its `\$name` form) as the expression. Throws a `java.lang.RuntimeException` when the signal's schema is not a primitive.
       */
      def :=(signal: Signal[_]): CompleteAttribute = Dom.attr(full) := signal.ref

      /**
       * Adds the `__case.camel` modifier to the rendered attribute name.
       */
      def camel: Single  = copy(caseModifier = CaseModifier.Camel)
      /**
       * Adds a kebab case modifier. Kebab case is the default, so it adds nothing to the rendered attribute name.
       */
      def kebab: Single  = copy(caseModifier = CaseModifier.Kebab)
      /**
       * Adds the `__case.snake` modifier to the rendered attribute name.
       */
      def snake: Single  = copy(caseModifier = CaseModifier.Snake)
      /**
       * Adds the `__case.pascal` modifier to the rendered attribute name.
       */
      def pascal: Single = copy(caseModifier = CaseModifier.Pascal)
    }
  }

  /**
   * A signal-taking attribute such as `data-computed` that still needs its signal. `attrName` is the full attribute name and `caseModifier` converts signal names given as strings.
   */
  final case class PartialSignalAttr(attrName: String, caseModifier: CaseModifier = CaseModifier.Camel) {
    /**
     * Returns the attribute for the signal with the given name, converted with `caseModifier`. The signal gets the type `A` through its schema.
     */
    def apply[A: Schema](signal: String): SignalAttr[A]     =
      SignalAttr(attrName, SignalName(caseModifier)(signal).toSignal, caseModifier)
    /**
     * Returns the attribute for the signal with the given `SignalName`.
     */
    def apply[A: Schema](signal: SignalName): SignalAttr[A] =
      SignalAttr(attrName, signal.toSignal, caseModifier)
    /**
     * Returns the attribute for the given signal.
     */
    def apply[A](signal: Signal[A]): SignalAttr[A]          =
      SignalAttr(attrName, signal, caseModifier)
  }

  /**
   * A signal-taking attribute such as `data-computed:<signal>`. `:=` with a JavaScript expression renders the keyed form; `:=` with a `SignalUpdate` renders the plain attribute name with an object expression.
   */
  final case class SignalAttr[A](attrName: String, signal: Signal[A], caseModifier: CaseModifier = CaseModifier.Camel) {
    private val full  = s"$attrName:${signal.name.name}${signal.name.caseModifier.suffix(CaseModifier.Camel)}"
    private val plain = s"$attrName${signal.name.caseModifier.suffix(CaseModifier.Camel)}"

    /**
     * Returns the attribute named `<attrName>:<signal>` with the JavaScript `expression` as its value.
     */
    def :=(expression: Js): Attribute          = Dom.attr(full)  := expression.value
    /**
     * Returns the attribute named `attrName` (without the signal name) whose value is the object expression produced by `update`.
     */
    def :=(update: SignalUpdate[A]): Attribute = Dom.attr(plain) := update.toExpression.value
  }

  /**
   * Conversions for `SignalAttr`.
   */
  object SignalAttr {
    /**
     * Converts to the keyed attribute whose value is the reference to the signal (its `\$name` form). Throws a `java.lang.RuntimeException` when the signal's schema is not a primitive.
     */
    implicit def toAttribute[A](attr: SignalAttr[A]): Attribute =
      Dom.attr(attr.full) := attr.signal.ref
  }

  /**
   * The `data-signals` attribute before a signal is chosen. `:=` with an expression renders `data-signals` and `caseModifier` converts signal names given as strings.
   */
  final case class PartialSignalsAttr(
    prefix: String,
    caseModifier: CaseModifier = CaseModifier.Camel,
    ifMissing: Boolean = false,
  ) {
    /**
     * Returns the `data-signals:<name>` attribute for the signal with the given name, converted with `caseModifier`.
     */
    def apply[A: Schema](signal: String): SignalsAttr[A]     =
      SignalsAttr(prefix, SignalName(caseModifier)(signal).toSignal[A], caseModifier)
    /**
     * Returns the `data-signals:<name>` attribute for the given `SignalName`.
     */
    def apply[A: Schema](signal: SignalName): SignalsAttr[A] =
      SignalsAttr(prefix, signal.toSignal[A], caseModifier)
    /**
     * Returns the `data-signals:<name>` attribute for the given signal.
     */
    def apply[A: Schema](signal: Signal[A]): SignalsAttr[A]  =
      SignalsAttr(prefix, signal, caseModifier)

    /**
     * Returns the `data-signals` attribute with the JavaScript `expression` as its value. The name ends in `__case.<case>` when `caseModifier` is not camel case.
     */
    def :=(expression: Js): Attribute =
      Dom.attr(s"$prefix-signals${caseModifier.suffix(CaseModifier.Camel)}") := expression.value
  }

  /**
   * The `data-signals:<name>` attribute for one signal. `:=` with an expression renders the keyed form; `:=` with a `SignalUpdate` or a value renders `data-signals` with an object expression. The case methods change how the signal name is rendered.
   */
  final case class SignalsAttr[A: Schema](
    prefix: String,
    signal: Signal[A],
    caseModifier: CaseModifier,
    ifMissing: Boolean = false,
  ) {
    private val full = {
      val ifMissing0: String = if (ifMissing) "__if-missing" else ""
      s"$prefix-signals:${signal.name.name}${signal.name.caseModifier.suffix(CaseModifier.Camel)}$ifMissing0"
    }

    private val plain = {
      val ifMissing0: String = if (ifMissing) "__if-missing" else ""
      s"$prefix-signals${signal.name.caseModifier.suffix(CaseModifier.Camel)}$ifMissing0"
    }

    /**
     * Returns the `data-signals:<name>` attribute with the JavaScript `expression` as its value.
     */
    def :=(expression: Js): Attribute = Dom.attr(full) := expression.value

    /**
     * Returns the `data-signals` attribute (without the signal name) whose value is the object expression produced by `update`.
     */
    def :=(update: SignalUpdate[A]): Attribute =
      Dom.attr(plain) := update.toExpression.value

    /**
     * Returns the `data-signals` attribute (without the signal name) whose value is `in` encoded as an object expression nested under the signal's path.
     */
    def :=(in: A): Attribute =
      Dom.attr(plain) := signal
        .update(in)
        .toExpression
        .value

    /**
     * Returns a copy that uses camel case for the signal name. Camel case is the default, so the rendered attribute has no `__case` suffix.
     */
    def camel: SignalsAttr[A]  =
      copy(caseModifier = CaseModifier.Camel, signal = signal.caseModifier(CaseModifier.Camel))
    /**
     * Returns a copy that uses kebab case for the signal name. The rendered attribute ends in `__case.kebab`.
     */
    def kebab: SignalsAttr[A]  =
      copy(caseModifier = CaseModifier.Kebab, signal = signal.caseModifier(CaseModifier.Kebab))
    /**
     * Returns a copy that uses snake case for the signal name. The rendered attribute ends in `__case.snake`.
     */
    def snake: SignalsAttr[A]  =
      copy(caseModifier = CaseModifier.Snake, signal = signal.caseModifier(CaseModifier.Snake))
    /**
     * Returns a copy that uses pascal case for the signal name. The rendered attribute ends in `__case.pascal`.
     */
    def pascal: SignalsAttr[A] =
      copy(caseModifier = CaseModifier.Pascal, signal = signal.caseModifier(CaseModifier.Pascal))
  }

  /**
   * The `data-indicator` attribute before a signal is chosen. The signal is a `Boolean` signal that Datastar sets while a request is in flight.
   */
  final case class PartialDataIndicator(prefix: String, caseModifier: CaseModifier = CaseModifier.Camel) {
    /**
     * Returns the `data-indicator:<name>` attribute for the signal with the given name, converted with `caseModifier`.
     */
    def apply(signal: String): DataIndicatorAttr =
      DataIndicatorAttr(prefix, SignalName(caseModifier)(signal).toSignal[Boolean], caseModifier)

    /**
     * Returns the `data-indicator:<name>` attribute for the given `SignalName`.
     */
    def apply(signal: SignalName): DataIndicatorAttr =
      DataIndicatorAttr(prefix, signal.toSignal[Boolean], caseModifier)

    /**
     * Returns the `data-indicator:<name>` attribute for the given `Boolean` signal.
     */
    def apply(signal: Signal[Boolean]): DataIndicatorAttr =
      DataIndicatorAttr(prefix, signal, caseModifier)

    /**
     * Returns a copy that converts signal names given as strings to camel case. Camel case is the default, so the rendered attribute has no `__case` suffix.
     */
    def camel: PartialDataIndicator  = copy(caseModifier = CaseModifier.Camel)
    /**
     * Returns a copy that converts signal names given as strings to kebab case. The rendered attribute ends in `__case.kebab`.
     */
    def kebab: PartialDataIndicator  = copy(caseModifier = CaseModifier.Kebab)
    /**
     * Returns a copy that converts signal names given as strings to snake case. The rendered attribute ends in `__case.snake`.
     */
    def snake: PartialDataIndicator  = copy(caseModifier = CaseModifier.Snake)
    /**
     * Returns a copy that converts signal names given as strings to pascal case. The rendered attribute ends in `__case.pascal`.
     */
    def pascal: PartialDataIndicator = copy(caseModifier = CaseModifier.Pascal)
  }

  /**
   * The `data-indicator:<signal>` attribute for one `Boolean` signal. It converts implicitly to a boolean attribute.
   */
  final case class DataIndicatorAttr(
    prefix: String,
    signal: Signal[Boolean],
    caseModifier: CaseModifier = CaseModifier.Camel,
  ) {
    private val full = s"$prefix-indicator:${signal.name.name}${signal.name.caseModifier.suffix(CaseModifier.Camel)}"

    /**
     * Returns a copy that uses camel case for the signal name. Camel case is the default, so the rendered attribute has no `__case` suffix.
     */
    def camel: DataIndicatorAttr  =
      copy(caseModifier = CaseModifier.Camel, signal = signal.caseModifier(CaseModifier.Camel))
    /**
     * Returns a copy that uses kebab case for the signal name. The rendered attribute ends in `__case.kebab`.
     */
    def kebab: DataIndicatorAttr  =
      copy(caseModifier = CaseModifier.Kebab, signal = signal.caseModifier(CaseModifier.Kebab))
    /**
     * Returns a copy that uses snake case for the signal name. The rendered attribute ends in `__case.snake`.
     */
    def snake: DataIndicatorAttr  =
      copy(caseModifier = CaseModifier.Snake, signal = signal.caseModifier(CaseModifier.Snake))
    /**
     * Returns a copy that uses pascal case for the signal name. The rendered attribute ends in `__case.pascal`.
     */
    def pascal: DataIndicatorAttr =
      copy(caseModifier = CaseModifier.Pascal, signal = signal.caseModifier(CaseModifier.Pascal))
  }

  /**
   * Conversions for `DataIndicatorAttr`.
   */
  object DataIndicatorAttr {
    /**
     * Converts to a boolean attribute named `data-indicator:<signal>`.
     */
    implicit def toAttribute(attr: DataIndicatorAttr): Attribute =
      Dom.boolAttr(attr.full)
  }

  /**
   * The `data-json-signals` attribute. `:=` takes a JavaScript expression; `terse` selects the `__terse` form.
   */
  final case class DataJsonSignals(prefix: String, terseOn: Boolean = false) {
    /**
     * Returns the attribute (`data-json-signals`, or `data-json-signals__terse` after `terse`) with the JavaScript `expression` as its value.
     */
    def :=(expression: Js): Attribute =
      if (terseOn) Dom.attr(s"$prefix-json-signals__terse") := expression.value
      else Dom.attr(s"$prefix-json-signals")                := expression.value

    /**
     * Returns a copy that renders as `data-json-signals__terse`.
     */
    def terse: DataJsonSignals = copy(terseOn = true)

  }

  /**
   * A Datastar attribute with a fixed `name` that takes a JavaScript expression.
   */
  final case class DatastarAttribute(name: String) {

    /**
     * Returns the attribute named `name` with the JavaScript `expression` as its value.
     */
    def :=(expression: Js): Attribute = Dom.attr(name) := expression.value
  }

  /**
   * The `data-on` attribute before an event is chosen. The modifier methods collect modifiers and the event methods (`click`, `input`, and so on) or `apply` pick the DOM event.
   */
  final case class PartialDataOn(prefix: String, modifier: EventModifier) {
    /**
     * Returns a `DataOn` for the DOM event `name`, rendered as `data-on:<name>` followed by the modifiers collected so far.
     */
    def apply(name: String): DataOn = DataOn(prefix, name, modifier)

    /**
     * Returns a copy with `mod` combined onto the modifiers collected so far.
     */
    def modify(mod: EventModifier): PartialDataOn = copy(modifier = modifier && mod)
    /**
     * Adds the `__capture` modifier.
     */
    def capture: PartialDataOn                    = modify(EventModifier.Capture)
    /**
     * Adds the `__case.camel` modifier to the rendered attribute name.
     */
    def camel: PartialDataOn                      = modify(EventModifier.Case(CaseModifier.Camel))
    /**
     * Adds a kebab case modifier. Kebab case is the default, so it adds nothing to the rendered attribute name.
     */
    def kebab: PartialDataOn                      = modify(EventModifier.Case(CaseModifier.Kebab))
    /**
     * Adds the `__case.snake` modifier to the rendered attribute name.
     */
    def snake: PartialDataOn                      = modify(EventModifier.Case(CaseModifier.Snake))
    /**
     * Adds the `__case.pascal` modifier to the rendered attribute name.
     */
    def pascal: PartialDataOn                     = modify(EventModifier.Case(CaseModifier.Pascal))
    /**
     * Adds a `__debounce.<ms>ms` modifier. `leading` appends `.leading` and `notrailing` appends `.notrailing`. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def debounce(duration: Duration, leading: Boolean = false, notrailing: Boolean = false): PartialDataOn =
      modify(EventModifier.Debounce(duration, leading, notrailing))
    /**
     * Adds a `__delay.<ms>ms` modifier. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def delay(duration: Duration): PartialDataOn = modify(EventModifier.Delay(duration))
    /**
     * Adds the `__once` modifier.
     */
    def once: PartialDataOn                      = modify(EventModifier.Once)
    /**
     * Adds the `__outside` modifier.
     */
    def outside: PartialDataOn                   = modify(EventModifier.Outside)
    /**
     * Adds the `__passive` modifier.
     */
    def passive: PartialDataOn                   = modify(EventModifier.Passive)
    /**
     * Adds the `__prevent` modifier.
     */
    def prevent: PartialDataOn                   = modify(EventModifier.Prevent)
    /**
     * Alias for `prevent`: adds the `__prevent` modifier.
     */
    def preventDefault: PartialDataOn            = modify(EventModifier.Prevent)
    /**
     * Adds the `__stop` modifier.
     */
    def stop: PartialDataOn                      = modify(EventModifier.Stop)
    /**
     * Alias for `stop`: adds the `__stop` modifier.
     */
    def stopPropagation: PartialDataOn           = modify(EventModifier.Stop)
    /**
     * Adds a `__throttle.<ms>ms` modifier. `noleading` appends `.noleading` and `trailing` appends `.trailing`. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def throttle(duration: Duration, noleading: Boolean = false, trailing: Boolean = false): PartialDataOn =
      modify(EventModifier.Throttle(duration, noleading, trailing))
    /**
     * Adds the `__viewtransition` modifier.
     */
    def viewTransition: PartialDataOn = modify(EventModifier.ViewTransition)
    /**
     * Adds the `__window` modifier.
     */
    def window: PartialDataOn         = modify(EventModifier.Window)
    /**
     * Adds the `__document` modifier.
     */
    def document: PartialDataOn       = modify(EventModifier.Document)

    // ---------------------------------------------------------------------------
    // Mouse Events
    // ---------------------------------------------------------------------------
    /**
     * Returns a `DataOn` for the `click` DOM event, rendered as `data-on:click` followed by the modifiers collected so far.
     */
    def click: DataOn       = apply("click")
    /**
     * Returns a `DataOn` for the `dblclick` DOM event, rendered as `data-on:dblclick` followed by the modifiers collected so far.
     */
    def dblclick: DataOn    = apply("dblclick")
    /**
     * Returns a `DataOn` for the `mousedown` DOM event, rendered as `data-on:mousedown` followed by the modifiers collected so far.
     */
    def mousedown: DataOn   = apply("mousedown")
    /**
     * Returns a `DataOn` for the `mouseup` DOM event, rendered as `data-on:mouseup` followed by the modifiers collected so far.
     */
    def mouseup: DataOn     = apply("mouseup")
    /**
     * Returns a `DataOn` for the `mouseover` DOM event, rendered as `data-on:mouseover` followed by the modifiers collected so far.
     */
    def mouseover: DataOn   = apply("mouseover")
    /**
     * Returns a `DataOn` for the `mouseout` DOM event, rendered as `data-on:mouseout` followed by the modifiers collected so far.
     */
    def mouseout: DataOn    = apply("mouseout")
    /**
     * Returns a `DataOn` for the `mouseenter` DOM event, rendered as `data-on:mouseenter` followed by the modifiers collected so far.
     */
    def mouseenter: DataOn  = apply("mouseenter")
    /**
     * Returns a `DataOn` for the `mouseleave` DOM event, rendered as `data-on:mouseleave` followed by the modifiers collected so far.
     */
    def mouseleave: DataOn  = apply("mouseleave")
    /**
     * Returns a `DataOn` for the `mousemove` DOM event, rendered as `data-on:mousemove` followed by the modifiers collected so far.
     */
    def mousemove: DataOn   = apply("mousemove")
    /**
     * Returns a `DataOn` for the `contextmenu` DOM event, rendered as `data-on:contextmenu` followed by the modifiers collected so far.
     */
    def contextmenu: DataOn = apply("contextmenu")
    /**
     * Returns a `DataOn` for the `wheel` DOM event, rendered as `data-on:wheel` followed by the modifiers collected so far.
     */
    def wheel: DataOn       = apply("wheel")

    // ---------------------------------------------------------------------------
    // Keyboard Events
    // ---------------------------------------------------------------------------
    /**
     * Returns a `DataOn` for the `keydown` DOM event, rendered as `data-on:keydown` followed by the modifiers collected so far.
     */
    def keydown: DataOn  = apply("keydown")
    /**
     * Returns a `DataOn` for the `keyup` DOM event, rendered as `data-on:keyup` followed by the modifiers collected so far.
     */
    def keyup: DataOn    = apply("keyup")
    /**
     * Returns a `DataOn` for the `keypress` DOM event, rendered as `data-on:keypress` followed by the modifiers collected so far.
     */
    def keypress: DataOn = apply("keypress")

    // ---------------------------------------------------------------------------
    // Form Events
    // ---------------------------------------------------------------------------
    /**
     * Returns a `DataOn` for the `input` DOM event, rendered as `data-on:input` followed by the modifiers collected so far.
     */
    def input: DataOn   = apply("input")
    /**
     * Returns a `DataOn` for the `change` DOM event, rendered as `data-on:change` followed by the modifiers collected so far.
     */
    def change: DataOn  = apply("change")
    /**
     * Returns a `DataOn` for the `submit` DOM event, rendered as `data-on:submit` followed by the modifiers collected so far.
     */
    def submit: DataOn  = apply("submit")
    /**
     * Returns a `DataOn` for the `reset` DOM event, rendered as `data-on:reset` followed by the modifiers collected so far.
     */
    def reset: DataOn   = apply("reset")
    /**
     * Returns a `DataOn` for the `select` DOM event, rendered as `data-on:select` followed by the modifiers collected so far.
     */
    def select: DataOn  = apply("select")
    /**
     * Returns a `DataOn` for the `invalid` DOM event, rendered as `data-on:invalid` followed by the modifiers collected so far.
     */
    def invalid: DataOn = apply("invalid")

    // ---------------------------------------------------------------------------
    // Focus Events
    // ---------------------------------------------------------------------------
    /**
     * Returns a `DataOn` for the `focus` DOM event, rendered as `data-on:focus` followed by the modifiers collected so far.
     */
    def focus: DataOn    = apply("focus")
    /**
     * Returns a `DataOn` for the `blur` DOM event, rendered as `data-on:blur` followed by the modifiers collected so far.
     */
    def blur: DataOn     = apply("blur")
    /**
     * Returns a `DataOn` for the `focusin` DOM event, rendered as `data-on:focusin` followed by the modifiers collected so far.
     */
    def focusin: DataOn  = apply("focusin")
    /**
     * Returns a `DataOn` for the `focusout` DOM event, rendered as `data-on:focusout` followed by the modifiers collected so far.
     */
    def focusout: DataOn = apply("focusout")

    // ---------------------------------------------------------------------------
    // Touch Events
    // ---------------------------------------------------------------------------
    /**
     * Returns a `DataOn` for the `touchstart` DOM event, rendered as `data-on:touchstart` followed by the modifiers collected so far.
     */
    def touchstart: DataOn  = apply("touchstart")
    /**
     * Returns a `DataOn` for the `touchend` DOM event, rendered as `data-on:touchend` followed by the modifiers collected so far.
     */
    def touchend: DataOn    = apply("touchend")
    /**
     * Returns a `DataOn` for the `touchmove` DOM event, rendered as `data-on:touchmove` followed by the modifiers collected so far.
     */
    def touchmove: DataOn   = apply("touchmove")
    /**
     * Returns a `DataOn` for the `touchcancel` DOM event, rendered as `data-on:touchcancel` followed by the modifiers collected so far.
     */
    def touchcancel: DataOn = apply("touchcancel")

    // ---------------------------------------------------------------------------
    // Window/Document Events
    // ---------------------------------------------------------------------------
    /**
     * Returns a `DataOn` for the `load` DOM event, rendered as `data-on:load` followed by the modifiers collected so far.
     */
    def load: DataOn         = apply("load")
    /**
     * Returns a `DataOn` for the `unload` DOM event, rendered as `data-on:unload` followed by the modifiers collected so far.
     */
    def unload: DataOn       = apply("unload")
    /**
     * Returns a `DataOn` for the `beforeunload` DOM event, rendered as `data-on:beforeunload` followed by the modifiers collected so far.
     */
    def beforeunload: DataOn = apply("beforeunload")
    /**
     * Returns a `DataOn` for the `resize` DOM event, rendered as `data-on:resize` followed by the modifiers collected so far.
     */
    def resize: DataOn       = apply("resize")
    /**
     * Returns a `DataOn` for the `scroll` DOM event, rendered as `data-on:scroll` followed by the modifiers collected so far.
     */
    def scroll: DataOn       = apply("scroll")
    /**
     * Returns a `DataOn` for the `hashchange` DOM event, rendered as `data-on:hashchange` followed by the modifiers collected so far.
     */
    def hashchange: DataOn   = apply("hashchange")
    /**
     * Returns a `DataOn` for the `popstate` DOM event, rendered as `data-on:popstate` followed by the modifiers collected so far.
     */
    def popstate: DataOn     = apply("popstate")

  }

  /**
   * A `data-on:<event>` attribute with its modifiers. `:=` takes the JavaScript expression to run when the event fires.
   */
  final case class DataOn(prefix: String, event: String, modifier: EventModifier) {
    private val full = s"$prefix-on:$event${modifier.render}"

    /**
     * Returns the `data-on:<event>` attribute (with the rendered modifiers) whose value is the JavaScript `expression`.
     */
    def :=(expression: Js): Attribute = Dom.attr(full) := expression.value

    /**
     * Returns a copy with `mod` combined onto the modifiers collected so far.
     */
    def modify(mod: EventModifier): DataOn = copy(modifier = modifier && mod)
    /**
     * Adds the `__capture` modifier.
     */
    def capture: DataOn                    = modify(EventModifier.Capture)
    /**
     * Adds the `__case.camel` modifier to the rendered attribute name.
     */
    def camel: DataOn                      = modify(EventModifier.Case(CaseModifier.Camel))
    /**
     * Adds a kebab case modifier. Kebab case is the default, so it adds nothing to the rendered attribute name.
     */
    def kebab: DataOn                      = modify(EventModifier.Case(CaseModifier.Kebab))
    /**
     * Adds the `__case.snake` modifier to the rendered attribute name.
     */
    def snake: DataOn                      = modify(EventModifier.Case(CaseModifier.Snake))
    /**
     * Adds the `__case.pascal` modifier to the rendered attribute name.
     */
    def pascal: DataOn                     = modify(EventModifier.Case(CaseModifier.Pascal))
    /**
     * Adds a `__debounce.<ms>ms` modifier. `leading` appends `.leading` and `notrailing` appends `.notrailing`. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def debounce(duration: Duration, leading: Boolean = false, notrailing: Boolean = false): DataOn =
      modify(EventModifier.Debounce(duration, leading, notrailing))
    /**
     * Adds a `__delay.<ms>ms` modifier. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def delay(duration: Duration): DataOn = modify(EventModifier.Delay(duration))
    /**
     * Adds the `__once` modifier.
     */
    def once: DataOn                      = modify(EventModifier.Once)
    /**
     * Adds the `__outside` modifier.
     */
    def outside: DataOn                   = modify(EventModifier.Outside)
    /**
     * Adds the `__passive` modifier.
     */
    def passive: DataOn                   = modify(EventModifier.Passive)
    /**
     * Adds the `__prevent` modifier.
     */
    def prevent: DataOn                   = modify(EventModifier.Prevent)
    /**
     * Alias for `prevent`: adds the `__prevent` modifier.
     */
    def preventDefault: DataOn            = modify(EventModifier.Prevent)
    /**
     * Adds the `__stop` modifier.
     */
    def stop: DataOn                      = modify(EventModifier.Stop)
    /**
     * Alias for `stop`: adds the `__stop` modifier.
     */
    def stopPropagation: DataOn           = modify(EventModifier.Stop)
    /**
     * Adds a `__throttle.<ms>ms` modifier. `noleading` appends `.noleading` and `trailing` appends `.trailing`. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def throttle(duration: Duration, noleading: Boolean = false, trailing: Boolean = false): DataOn =
      modify(EventModifier.Throttle(duration, noleading, trailing))
    /**
     * Adds the `__viewtransition` modifier.
     */
    def viewTransition: DataOn = modify(EventModifier.ViewTransition)
    /**
     * Adds the `__window` modifier.
     */
    def window: DataOn         = modify(EventModifier.Window)
    /**
     * Adds the `__document` modifier.
     */
    def document: DataOn       = modify(EventModifier.Document)
  }

  /**
   * The `data-on-intersect` attribute, which runs an expression when the element enters the viewport. The methods add modifiers to the attribute name.
   */
  final case class DataOnIntersect(prefix: String, modifier: IntersectModifier) {
    /**
     * Returns the `data-on-intersect` attribute (with the rendered modifiers) whose value is the JavaScript `expression`.
     */
    def :=(expression: Js): Attribute = Dom.attr(s"$prefix-on-intersect${modifier.render}") := expression.value

    /**
     * Returns a copy with `mod` combined onto the modifiers collected so far.
     */
    def modify(mod: IntersectModifier): DataOnIntersect = copy(modifier = modifier && mod)
    /**
     * Adds the `__once` modifier.
     */
    def once: DataOnIntersect                           = modify(IntersectModifier.Once)
    /**
     * Adds the `__half` modifier.
     */
    def half: DataOnIntersect                           = modify(IntersectModifier.Half)
    /**
     * Adds the `__full` modifier.
     */
    def full: DataOnIntersect                           = modify(IntersectModifier.Full)
    /**
     * Adds a `__delay.<ms>ms` modifier. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def delay(duration: Duration): DataOnIntersect      = modify(IntersectModifier.Delay(duration))
    /**
     * Adds a `__debounce.<ms>ms` modifier. `leading` appends `.leading` and `notrailing` appends `.notrailing`. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def debounce(duration: Duration, leading: Boolean = false, notrailing: Boolean = false): DataOnIntersect =
      modify(IntersectModifier.Debounce(duration, leading, notrailing))
    /**
     * Adds a `__throttle.<ms>ms` modifier. `noleading` appends `.noleading` and `trailing` appends `.trailing`. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def throttle(duration: Duration, noleading: Boolean = false, trailing: Boolean = false): DataOnIntersect =
      modify(IntersectModifier.Throttle(duration, noleading, trailing))
    /**
     * Adds the `__viewtransition` modifier.
     */
    def viewTransition: DataOnIntersect          = modify(IntersectModifier.ViewTransition)
    /**
     * Adds the `__exit` modifier.
     */
    def exit: DataOnIntersect                    = modify(IntersectModifier.Exit)
    /**
     * Adds a `__threshold.<percent>` modifier. Throws a `java.lang.IllegalArgumentException` when `percent` is outside 0 to 100.
     */
    def threshold(percent: Int): DataOnIntersect = modify(IntersectModifier.Threshold(percent))
  }

  /**
   * A modifier for `data-on-intersect`. It renders as a suffix of the attribute name, and `and` joins two modifiers.
   */
  sealed trait IntersectModifier extends Product with Serializable {
    /**
     * The suffix this modifier adds to the attribute name, such as `__once`. It is an empty string when the modifier adds nothing.
     */
    val render: String
    /**
     * Alias for `and`.
     */
    def &&(other: IntersectModifier): IntersectModifier  = and(other)
    /**
     * Returns a modifier that renders this modifier's suffix followed by the suffix of `other`.
     */
    def and(other: IntersectModifier): IntersectModifier = IntersectModifier.And(this, other)
  }

  /**
   * A `data-on-intersect` modifier without a value. It renders as `__` plus the lower-cased case object name, or an empty string for `None`.
   */
  sealed trait OptionLessIntersect extends IntersectModifier { self =>
    /**
     * An empty string for `None`, otherwise `__` followed by the lower-cased case object name.
     */
    final val render: String =
      if (self == IntersectModifier.None) ""
      else s"__${productPrefix.toLowerCase}"
  }

  /**
   * The modifiers available for `data-on-intersect`.
   */
  object IntersectModifier                                                      {
    /**
     * Two `data-on-intersect` modifiers joined together. A `None` side is dropped from the rendered suffix.
     */
    final case class And(left: IntersectModifier, right: IntersectModifier) extends IntersectModifier {
      /**
       * The rendered suffix of the left modifier followed by that of the right one. A `None` side contributes nothing.
       */
      val render: String =
        (left, right) match {
          case (None, r) => r.render
          case (l, None) => l.render
          case _         => s"${left.render}${right.render}"
        }
    }
    /**
     * A modifier that adds nothing to the attribute name of `data-on-intersect`.
     */
    case object None                                                        extends OptionLessIntersect
    /**
     * The `__once` modifier of `data-on-intersect`.
     */
    case object Once                                                        extends OptionLessIntersect
    /**
     * The `__half` modifier of `data-on-intersect`.
     */
    case object Half                                                        extends OptionLessIntersect
    /**
     * The `__full` modifier of `data-on-intersect`.
     */
    case object Full                                                        extends OptionLessIntersect
    /**
     * The `__delay.<ms>ms` modifier of `data-on-intersect`. It delays the expression by `duration`.
     */
    final case class Delay(duration: Duration)                              extends IntersectModifier {
      /**
       * `__delay.<ms>ms`, with the duration in whole milliseconds.
       */
      val render: String = s"__delay.${duration.toMillis}ms"
    }
    /**
     * The `__debounce.<ms>ms` modifier of `data-on-intersect`, with `.leading` and `.notrailing` appended when the flags are set.
     */
    final case class Debounce(duration: Duration, leading: Boolean = false, notrailing: Boolean = false)
        extends IntersectModifier {
      /**
       * `__debounce.<ms>ms`, followed by `.leading` and/or `.notrailing` when those flags are set.
       */
      val render: String     = {
        val base = s"__debounce.${duration.toMillis}ms"
        (leading, notrailing) match {
          case (false, false) => base
          case (true, false)  => s"$base.leading"
          case (false, true)  => s"$base.notrailing"
          case (true, true)   => s"$base.leading.notrailing"
        }
      }
      /**
       * Returns a copy with `leading` set to `true`.
       */
      def lead: Debounce     = copy(leading = true)
      /**
       * Returns a copy with `notrailing` set to `false`, so the suffix has no `.notrailing`.
       */
      def trailing: Debounce = copy(notrailing = false)
    }
    /**
     * The `__throttle.<ms>ms` modifier of `data-on-intersect`, with `.noleading` and `.trailing` appended when the flags are set.
     */
    final case class Throttle(duration: Duration, noleading0: Boolean = false, trailing0: Boolean = false)
        extends IntersectModifier {
      /**
       * Returns a copy with `noleading0` set to `true`, so the suffix ends in `.noleading`.
       */
      def noleading: Throttle = copy(noleading0 = true)
      /**
       * `__throttle.<ms>ms`, followed by `.noleading` and/or `.trailing` when those flags are set.
       */
      val render: String      = {
        val base = s"__throttle.${duration.toMillis}ms"
        (noleading0, trailing0) match {
          case (false, false) => base
          case (true, false)  => s"$base.noleading"
          case (false, true)  => s"$base.trailing"
          case (true, true)   => s"$base.noleading.trailing"
        }
      }
      /**
       * Returns a copy with `trailing0` set to `false`. The rendered suffix does not gain `.trailing`.
       */
      def trailing: Throttle  = copy(trailing0 = false)
    }
    /**
     * The `__viewtransition` modifier of `data-on-intersect`.
     */
    case object ViewTransition                                              extends OptionLessIntersect
    /**
     * The `__exit` modifier of `data-on-intersect`.
     */
    case object Exit                                                        extends OptionLessIntersect
    /**
     * The `__threshold.<percent>` modifier of `data-on-intersect`. Throws a `java.lang.IllegalArgumentException` when `percent` is outside 0 to 100.
     */
    final case class Threshold(percent: Int)                                extends IntersectModifier {
      require(percent >= 0 && percent <= 100, s"Threshold percent must be between 0 and 100, got: $percent")
      /**
       * `__threshold.<percent>`.
       */
      val render: String = s"__threshold.$percent"
    }
  }
  /**
   * The `data-on-interval` attribute, which runs an expression repeatedly. The methods add modifiers to the attribute name.
   */
  final case class DataOnInterval(prefix: String, modifier: OnIntervalModifier) {
    private val full = s"$prefix-on-interval${modifier.render}"

    /**
     * Returns the `data-on-interval` attribute (with the rendered modifiers) whose value is the JavaScript `expression`.
     */
    def :=(expression: Js): Attribute = Dom.attr(full) := expression.value

    /**
     * Adds a `__duration.<ms>ms` modifier, followed by `.leading` when `leading` is set. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def duration(duration: Duration, leading: Boolean = false): DataOnInterval = copy(
      modifier = this.modifier && OnIntervalModifier.Duration(duration, leading),
    )
    /**
     * Adds the `__viewtransition` modifier.
     */
    def viewTransition: DataOnInterval = copy(modifier = this.modifier && OnIntervalModifier.ViewTransition)
  }

  /**
   * A modifier for `data-on-interval`. It renders as a suffix of the attribute name, and `and` joins two modifiers.
   */
  sealed trait OnIntervalModifier extends Product with Serializable {
    /**
     * The suffix this modifier adds to the attribute name, such as `__once`. It is an empty string when the modifier adds nothing.
     */
    val render: String
    /**
     * Alias for `and`.
     */
    def &&(other: OnIntervalModifier): OnIntervalModifier  = and(other)
    /**
     * Returns a modifier that renders this modifier's suffix followed by the suffix of `other`.
     */
    def and(other: OnIntervalModifier): OnIntervalModifier = OnIntervalModifier.And(this, other)
  }

  /**
   * The modifiers available for `data-on-interval`.
   */
  object OnIntervalModifier {
    /**
     * Two `data-on-interval` modifiers joined together. A `None` side is dropped from the rendered suffix.
     */
    final case class And(left: OnIntervalModifier, right: OnIntervalModifier) extends OnIntervalModifier {
      /**
       * The rendered suffix of the left modifier followed by that of the right one. A `None` side contributes nothing.
       */
      val render: String =
        (left, right) match {
          case (None, r) => r.render
          case (l, None) => l.render
          case _         => s"${left.render}${right.render}"
        }
    }
    /**
     * A modifier that adds nothing to the attribute name of `data-on-interval`.
     */
    case object None                                                          extends OnIntervalModifier {
      /**
       * An empty string.
       */
      val render: String = ""
    }
    /**
     * The `__duration.<ms>ms` modifier of `data-on-interval`, with `.leading` appended when `leading` is set.
     */
    final case class Duration(duration: java.time.Duration, leading: Boolean) extends OnIntervalModifier {
      /**
       * `__duration.<ms>ms`, followed by `.leading` when `leading` is set.
       */
      val render: String = {
        val base = s"__duration.${duration.toMillis}ms"
        if (leading) s"$base.leading" else base
      }
      /**
       * Returns a copy with `leading` set to `true`.
       */
      def lead: Duration = copy(leading = true)
    }
    /**
     * The `__viewtransition` modifier of `data-on-interval`.
     */
    case object ViewTransition                                                extends OnIntervalModifier {
      /**
       * `__viewtransition`.
       */
      val render: String = "__viewtransition"
    }
  }

  /**
   * The `data-init` attribute, which runs an expression when the element loads. The methods add modifiers to the attribute name.
   */
  final case class DataInit(prefix: String, modifier: InitModifier) {
    private val full = s"$prefix-init${modifier.render}"

    /**
     * Returns the `data-init` attribute (with the rendered modifiers) whose value is the JavaScript `expression`.
     */
    def :=(expression: Js): Attribute = Dom.attr(full) := expression.value

    /**
     * Adds a `__delay.<ms>ms` modifier. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def delay(duration: Duration): DataInit = copy(modifier = modifier && InitModifier.Delay(duration))
    /**
     * Adds the `__viewtransition` modifier.
     */
    def viewTransition: DataInit            = copy(modifier = modifier && InitModifier.ViewTransition)
  }

  /**
   * A modifier for `data-init`. It renders as a suffix of the attribute name, and `and` joins two modifiers.
   */
  sealed trait InitModifier extends Product with Serializable {
    /**
     * The suffix this modifier adds to the attribute name, such as `__once`. It is an empty string when the modifier adds nothing.
     */
    val render: String
    /**
     * Alias for `and`.
     */
    def &&(other: InitModifier): InitModifier  = and(other)
    /**
     * Returns a modifier that renders this modifier's suffix followed by the suffix of `other`.
     */
    def and(other: InitModifier): InitModifier = InitModifier.And(this, other)
  }

  /**
   * The modifiers available for `data-init`.
   */
  object InitModifier {
    /**
     * Two `data-init` modifiers joined together. A `None` side is dropped from the rendered suffix.
     */
    final case class And(left: InitModifier, right: InitModifier) extends InitModifier {
      /**
       * The rendered suffix of the left modifier followed by that of the right one. A `None` side contributes nothing.
       */
      val render: String =
        (left, right) match {
          case (None, r) => r.render
          case (l, None) => l.render
          case _         => s"${left.render}${right.render}"
        }
    }
    /**
     * A modifier that adds nothing to the attribute name of `data-init`.
     */
    case object None                                              extends InitModifier {
      /**
       * An empty string.
       */
      val render: String = ""
    }
    /**
     * The `__delay.<ms>ms` modifier of `data-init`. It delays the expression by `duration`.
     */
    final case class Delay(duration: java.time.Duration)          extends InitModifier {
      /**
       * `__delay.<ms>ms`, with the duration in whole milliseconds.
       */
      val render: String = s"__delay.${duration.toMillis}ms"
    }
    /**
     * The `__viewtransition` modifier of `data-init`.
     */
    case object ViewTransition                                    extends InitModifier {
      /**
       * `__viewtransition`.
       */
      val render: String = "__viewtransition"
    }
  }

  // Binary compatibility: Keep DataOnLoad class (deprecated, use DataInit)
  // Note: Both generate the same data-init attribute per RC8
  /**
   * The result of the deprecated `dataOnLoad`. It renders the same `data-init` attribute as `DataInit`.
   */
  @deprecated("Use dataInit instead of dataOnLoad", "3.6.0")
  @nowarn("cat=deprecation")
  final case class DataOnLoad(prefix: String, modifier: LoadModifier) {
    private val full = s"$prefix-init${modifier.render}"

    /**
     * Returns the `data-init` attribute (with the rendered modifiers) whose value is the JavaScript `expression`.
     */
    def :=(expression: Js): Attribute = Dom.attr(full) := expression.value

    /**
     * Adds a `__delay.<ms>ms` modifier. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def delay(duration: Duration): DataOnLoad = copy(modifier = modifier && LoadModifier.Delay(duration))
    /**
     * Adds the `__viewtransition` modifier.
     */
    def viewTransition: DataOnLoad            = copy(modifier = modifier && LoadModifier.ViewTransition)
  }

  /**
   * A modifier for `data-init` (through the deprecated `dataOnLoad`). It renders as a suffix of the attribute name, and `and` joins two modifiers.
   */
  @deprecated("Use dataInit instead of dataOnLoad", "3.6.0")
  @nowarn("cat=deprecation")
  sealed trait LoadModifier extends Product with Serializable {
    /**
     * The suffix this modifier adds to the attribute name, such as `__once`. It is an empty string when the modifier adds nothing.
     */
    val render: String
    /**
     * Alias for `and`.
     */
    def &&(other: LoadModifier): LoadModifier  = and(other)
    /**
     * Returns a modifier that renders this modifier's suffix followed by the suffix of `other`.
     */
    def and(other: LoadModifier): LoadModifier = LoadModifier.And(this, other)
  }

  /**
   * The modifiers available for `data-init` (through the deprecated `dataOnLoad`).
   */
  @deprecated("Use dataInit instead of dataOnLoad", "3.6.0")
  object LoadModifier {
    /**
     * Two `data-init` (through the deprecated `dataOnLoad`) modifiers joined together. The suffix is the left suffix followed by the right one.
     */
    final case class And(left: LoadModifier, right: LoadModifier) extends LoadModifier {
      /**
       * The rendered suffix of the left modifier followed by that of the right one. A `None` side contributes nothing.
       */
      val render: String = s"${left.render}${right.render}"
    }
    /**
     * A modifier that adds nothing to the attribute name of `data-init` (through the deprecated `dataOnLoad`).
     */
    case object None                                              extends LoadModifier {
      /**
       * An empty string.
       */
      val render: String = ""
    }
    /**
     * The `__delay.<ms>ms` modifier of `data-init` (through the deprecated `dataOnLoad`). It delays the expression by `duration`.
     */
    final case class Delay(duration: java.time.Duration)          extends LoadModifier {
      /**
       * `__delay.<ms>ms`, with the duration in whole milliseconds.
       */
      val render: String = s"__delay.${duration.toMillis}ms"
    }
    /**
     * The `__viewtransition` modifier of `data-init` (through the deprecated `dataOnLoad`).
     */
    case object ViewTransition                                    extends LoadModifier {
      /**
       * `__viewtransition`.
       */
      val render: String = "__viewtransition"
    }
  }

  /**
   * The `data-on-signal-patch` attribute, which runs an expression when signals are patched. The methods add modifiers to the attribute name.
   */
  final case class DataOnSignalPatch(prefix: String, modifier: OnSignalPatchModifier) {
    private val full = s"$prefix-on-signal-patch${modifier.render}"

    /**
     * Returns the `data-on-signal-patch` attribute (with the rendered modifiers) whose value is the JavaScript `expression`.
     */
    def :=(expression: Js): Attribute = Dom.attr(full) := expression.value

    /**
     * Adds a `__delay.<ms>ms` modifier. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def delay(duration: Duration): DataOnSignalPatch                                                           =
      copy(modifier = modifier && OnSignalPatchModifier.Delay(duration))
    /**
     * Adds a `__debounce.<ms>ms` modifier. `leading` appends `.leading` and `notrailing` appends `.notrailing`. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def debounce(duration: Duration, leading: Boolean = false, notrailing: Boolean = false): DataOnSignalPatch =
      copy(modifier = modifier && OnSignalPatchModifier.Debounce(duration, leading, notrailing))
    /**
     * Adds a `__throttle.<ms>ms` modifier. `noleading` appends `.noleading` and `trailing` appends `.trailing`. The duration is rendered in whole milliseconds (`Duration#toMillis`).
     */
    def throttle(duration: Duration, noleading: Boolean = false, trailing: Boolean = false): DataOnSignalPatch =
      copy(modifier = modifier && OnSignalPatchModifier.Throttle(duration, noleading, trailing))
  }

  /**
   * A modifier for `data-on-signal-patch`. It renders as a suffix of the attribute name, and `and` joins two modifiers.
   */
  sealed trait OnSignalPatchModifier extends Product with Serializable {
    /**
     * The suffix this modifier adds to the attribute name, such as `__once`. It is an empty string when the modifier adds nothing.
     */
    val render: String
    /**
     * Alias for `and`.
     */
    def &&(other: OnSignalPatchModifier): OnSignalPatchModifier  = and(other)
    /**
     * Returns a modifier that renders this modifier's suffix followed by the suffix of `other`.
     */
    def and(other: OnSignalPatchModifier): OnSignalPatchModifier = OnSignalPatchModifier.And(this, other)
  }

  /**
   * The modifiers available for `data-on-signal-patch`.
   */
  object OnSignalPatchModifier {
    /**
     * Two `data-on-signal-patch` modifiers joined together. A `None` side is dropped from the rendered suffix.
     */
    final case class And(left: OnSignalPatchModifier, right: OnSignalPatchModifier) extends OnSignalPatchModifier {
      /**
       * The rendered suffix of the left modifier followed by that of the right one. A `None` side contributes nothing.
       */
      val render: String =
        (left, right) match {
          case (None, r) => r.render
          case (l, None) => l.render
          case _         => s"${left.render}${right.render}"
        }
    }
    /**
     * A modifier that adds nothing to the attribute name of `data-on-signal-patch`.
     */
    case object None                                                                extends OnSignalPatchModifier {
      /**
       * An empty string.
       */
      val render: String = ""
    }
    /**
     * The `__delay.<ms>ms` modifier of `data-on-signal-patch`. It delays the expression by `duration`.
     */
    final case class Delay(duration: java.time.Duration)                            extends OnSignalPatchModifier {
      /**
       * `__delay.<ms>ms`, with the duration in whole milliseconds.
       */
      val render: String = s"__delay.${duration.toMillis}ms"
    }
    /**
     * The `__debounce.<ms>ms` modifier of `data-on-signal-patch`, with `.leading` and `.notrailing` appended when the flags are set.
     */
    final case class Debounce(duration: java.time.Duration, leading: Boolean = false, notrailing: Boolean = false)
        extends OnSignalPatchModifier {
      /**
       * `__debounce.<ms>ms`, followed by `.leading` and/or `.notrailing` when those flags are set.
       */
      val render: String     = {
        val base = s"__debounce.${duration.toMillis}ms"
        (leading, notrailing) match {
          case (false, false) => base
          case (true, false)  => s"$base.leading"
          case (false, true)  => s"$base.notrailing"
          case (true, true)   => s"$base.leading.notrailing"
        }
      }
      /**
       * Returns a copy with `leading` set to `true`.
       */
      def lead: Debounce     = copy(leading = true)
      /**
       * Returns a copy with `notrailing` set to `false`, so the suffix has no `.notrailing`.
       */
      def trailing: Debounce = copy(notrailing = false)
    }
    /**
     * The `__throttle.<ms>ms` modifier of `data-on-signal-patch`, with `.noleading` and `.trailing` appended when the flags are set.
     */
    final case class Throttle(duration: java.time.Duration, noleading0: Boolean = false, trailing0: Boolean = false)
        extends OnSignalPatchModifier {
      /**
       * Returns a copy with `noleading0` set to `true`, so the suffix ends in `.noleading`.
       */
      def noleading: Throttle = copy(noleading0 = true)
      /**
       * `__throttle.<ms>ms`, followed by `.noleading` and/or `.trailing` when those flags are set.
       */
      val render: String      = {
        val base = s"__throttle.${duration.toMillis}ms"
        (noleading0, trailing0) match {
          case (false, false) => base
          case (true, false)  => s"$base.noleading"
          case (false, true)  => s"$base.trailing"
          case (true, true)   => s"$base.noleading.trailing"
        }
      }
      /**
       * Returns a copy with `trailing0` set to `false`. The rendered suffix does not gain `.trailing`.
       */
      def trailing: Throttle  = copy(trailing0 = false)
    }
  }

  /**
   * The `data-bind` attribute before a signal is chosen. `caseModifier` converts signal names given as strings.
   */
  final case class PartialDataBind(prefix: String, caseModifier: CaseModifier = CaseModifier.Camel) {
    /**
     * Returns the `data-bind:<name>` attribute for the signal with the given name, converted with `caseModifier`.
     */
    def apply(signal: String): DataBind     = DataBind(prefix, SignalName(caseModifier)(signal), caseModifier)
    /**
     * Returns the `data-bind:<name>` attribute for the given `SignalName`.
     */
    def apply(signal: SignalName): DataBind = DataBind(prefix, signal, caseModifier)
    /**
     * Returns the `data-bind:<name>` attribute for the name of the given signal.
     */
    def apply(signal: Signal[_]): DataBind  = DataBind(prefix, signal.name, caseModifier)

    /**
     * Returns a copy that converts signal names given as strings to camel case. Camel case is the default, so the rendered attribute has no `__case` suffix.
     */
    def camel: PartialDataBind  = copy(caseModifier = CaseModifier.Camel)
    /**
     * Returns a copy that converts signal names given as strings to kebab case. The rendered attribute ends in `__case.kebab`.
     */
    def kebab: PartialDataBind  = copy(caseModifier = CaseModifier.Kebab)
    /**
     * Returns a copy that converts signal names given as strings to snake case. The rendered attribute ends in `__case.snake`.
     */
    def snake: PartialDataBind  = copy(caseModifier = CaseModifier.Snake)
    /**
     * Returns a copy that converts signal names given as strings to pascal case. The rendered attribute ends in `__case.pascal`.
     */
    def pascal: PartialDataBind = copy(caseModifier = CaseModifier.Pascal)
  }

  /**
   * The `data-bind:<signal>` attribute for one signal. It converts implicitly to a boolean attribute; `prop` and `event` add modifiers.
   */
  final case class DataBind(
    prefix: String,
    signalName: SignalName,
    caseModifier: CaseModifier = CaseModifier.Camel,
    modifier: BindModifier = BindModifier.None,
  ) {
    private val full =
      s"$prefix-bind:${signalName.name}${signalName.caseModifier.suffix(CaseModifier.Camel)}${modifier.render}"

    /**
     * Returns a copy that uses camel case for the signal name. Camel case is the default, so the rendered attribute has no `__case` suffix.
     */
    def camel: DataBind                                         =
      copy(caseModifier = CaseModifier.Camel, signalName = signalName.caseModifier(CaseModifier.Camel))
    /**
     * Returns a copy that uses kebab case for the signal name. The rendered attribute ends in `__case.kebab`.
     */
    def kebab: DataBind                                         =
      copy(caseModifier = CaseModifier.Kebab, signalName = signalName.caseModifier(CaseModifier.Kebab))
    /**
     * Returns a copy that uses snake case for the signal name. The rendered attribute ends in `__case.snake`.
     */
    def snake: DataBind                                         =
      copy(caseModifier = CaseModifier.Snake, signalName = signalName.caseModifier(CaseModifier.Snake))
    /**
     * Returns a copy that uses pascal case for the signal name. The rendered attribute ends in `__case.pascal`.
     */
    def pascal: DataBind                                        =
      copy(caseModifier = CaseModifier.Pascal, signalName = signalName.caseModifier(CaseModifier.Pascal))
    /**
     * Adds the `__prop.<propertyName>` modifier.
     */
    def prop(propertyName: String): DataBind                    =
      copy(modifier = modifier && BindModifier.Prop(propertyName))
    /**
     * Adds the `__event.<name>` modifier; several names are joined with `.`.
     */
    def event(eventName: String, eventNames: String*): DataBind =
      copy(modifier = modifier && BindModifier.Event(eventName +: eventNames))
  }

  /**
   * Conversions for `DataBind`.
   */
  object DataBind {
    /**
     * Converts to a boolean attribute named `data-bind:<signal>` followed by the rendered modifiers.
     */
    implicit def toAttribute(attr: DataBind): Attribute = Dom.boolAttr(attr.full)
  }

  /**
   * A modifier for `data-bind`. It renders as a suffix of the attribute name, and `and` joins two modifiers.
   */
  sealed trait BindModifier extends Product with Serializable {
    /**
     * The suffix this modifier adds to the attribute name. It is an empty string when the modifier adds nothing.
     */
    def render: String
    /**
     * Alias for `and`.
     */
    def &&(other: BindModifier): BindModifier  = and(other)
    /**
     * Returns a modifier that renders this modifier's suffix followed by the suffix of `other`.
     */
    def and(other: BindModifier): BindModifier = BindModifier.And(this, other)
  }

  /**
   * The modifiers available for `data-bind`.
   */
  object BindModifier {
    /**
     * A modifier that adds nothing to the attribute name of `data-bind`.
     */
    case object None                                              extends BindModifier {
      /**
       * An empty string.
       */
      val render: String = ""
    }
    /**
     * The `__prop.<propertyName>` modifier of `data-bind`.
     */
    final case class Prop(propertyName: String)                   extends BindModifier {
      /**
       * `__prop.` followed by the property name.
       */
      val render: String = s"__prop.$propertyName"
    }
    /**
     * The `__event.<name>` modifier of `data-bind`; several event names are joined with `.`.
     */
    final case class Event(eventNames: Seq[String])               extends BindModifier {
      /**
       * `__event.` followed by the event names joined with `.`.
       */
      val render: String = s"__event.${eventNames.mkString(".")}"
    }
    /**
     * Two `data-bind` modifiers joined together. A `None` side is dropped from the rendered suffix.
     */
    final case class And(left: BindModifier, right: BindModifier) extends BindModifier {
      /**
       * The rendered suffix of the left modifier followed by that of the right one. A `None` side contributes nothing.
       */
      val render: String = (left, right) match {
        case (None, r) => r.render
        case (l, None) => l.render
        case _         => s"${left.render}${right.render}"
      }
    }
  }

  /**
   * The `data-ref` attribute before a signal is chosen. `caseModifier` converts signal names given as strings.
   */
  final case class PartialDataRef(prefix: String, caseModifier: CaseModifier = CaseModifier.Camel) {
    /**
     * Returns the `data-ref:<name>` attribute for the signal with the given name, converted with `caseModifier`.
     */
    def apply(signal: String): DataRef     = DataRef(prefix, SignalName(caseModifier)(signal), caseModifier)
    /**
     * Returns the `data-ref:<name>` attribute for the given `SignalName`.
     */
    def apply(signal: SignalName): DataRef = DataRef(prefix, signal, caseModifier)

    /**
     * Returns a copy that converts signal names given as strings to camel case. Camel case is the default, so the rendered attribute has no `__case` suffix.
     */
    def camel: PartialDataRef  = copy(caseModifier = CaseModifier.Camel)
    /**
     * Returns a copy that converts signal names given as strings to kebab case. The rendered attribute ends in `__case.kebab`.
     */
    def kebab: PartialDataRef  = copy(caseModifier = CaseModifier.Kebab)
    /**
     * Returns a copy that converts signal names given as strings to snake case. The rendered attribute ends in `__case.snake`.
     */
    def snake: PartialDataRef  = copy(caseModifier = CaseModifier.Snake)
    /**
     * Returns a copy that converts signal names given as strings to pascal case. The rendered attribute ends in `__case.pascal`.
     */
    def pascal: PartialDataRef = copy(caseModifier = CaseModifier.Pascal)
  }

  /**
   * The `data-ref:<signal>` attribute for one signal name. It converts implicitly to a boolean attribute.
   */
  final case class DataRef(prefix: String, signalName: SignalName, caseModifier: CaseModifier = CaseModifier.Camel) {
    private val full = s"$prefix-ref:${signalName.name}${signalName.caseModifier.suffix(CaseModifier.Camel)}"

    /**
     * Returns a copy that uses camel case for the signal name. Camel case is the default, so the rendered attribute has no `__case` suffix.
     */
    def camel: DataRef  =
      copy(caseModifier = CaseModifier.Camel, signalName = signalName.caseModifier(CaseModifier.Camel))
    /**
     * Returns a copy that uses kebab case for the signal name. The rendered attribute ends in `__case.kebab`.
     */
    def kebab: DataRef  =
      copy(caseModifier = CaseModifier.Kebab, signalName = signalName.caseModifier(CaseModifier.Kebab))
    /**
     * Returns a copy that uses snake case for the signal name. The rendered attribute ends in `__case.snake`.
     */
    def snake: DataRef  =
      copy(caseModifier = CaseModifier.Snake, signalName = signalName.caseModifier(CaseModifier.Snake))
    /**
     * Returns a copy that uses pascal case for the signal name. The rendered attribute ends in `__case.pascal`.
     */
    def pascal: DataRef =
      copy(caseModifier = CaseModifier.Pascal, signalName = signalName.caseModifier(CaseModifier.Pascal))
  }

  /**
   * Conversions for `DataRef`.
   */
  object DataRef {
    /**
     * Converts to a boolean attribute named `data-ref:<signal>`.
     */
    implicit def toAttribute(attr: DataRef): Attribute = Dom.boolAttr(attr.full)
  }

  /**
   * A modifier for `data-on`. It renders as a suffix of the attribute name, and `and` joins two modifiers.
   */
  sealed trait EventModifier extends Product with Serializable {
    /**
     * The suffix this modifier adds to the attribute name, such as `__once`. It is an empty string when the modifier adds nothing.
     */
    val render: String
    /**
     * Alias for `and`.
     */
    def &&(other: EventModifier): EventModifier  = and(other)
    /**
     * Returns a modifier that renders this modifier's suffix followed by the suffix of `other`.
     */
    def and(other: EventModifier): EventModifier = EventModifier.And(this, other)
  }

  /**
   * A `data-on` modifier without a value. It renders as `__` plus the lower-cased case object name.
   */
  sealed trait OptionLess extends EventModifier {
    /**
     * `__` followed by the lower-cased case object name.
     */
    final val render: String = s"__${productPrefix.toLowerCase}"
  }

  /**
   * The modifiers available for `data-on`.
   */
  object EventModifier {
    /**
     * Two `data-on` modifiers joined together. A `None` side is dropped from the rendered suffix.
     */
    final case class And(left: EventModifier, right: EventModifier) extends EventModifier {
      /**
       * The rendered suffix of the left modifier followed by that of the right one. A `None` side contributes nothing.
       */
      val render: String =
        (left, right) match {
          case (None, r) => r.render
          case (l, None) => l.render
          case _         => s"${left.render}${right.render}"
        }
    }
    /**
     * The `__capture` modifier of `data-on`.
     */
    case object Capture                                             extends OptionLess
    /**
     * The `__case.<case>` modifier of `data-on`. It adds nothing for kebab case, the default.
     */
    final case class Case(caseModifier: CaseModifier)               extends EventModifier {
      /**
       * `__case.camel`, `__case.snake` or `__case.pascal`; an empty string for kebab case.
       */
      val render: String = {
        val suffix = caseModifier.suffix(CaseModifier.Kebab)
        if (suffix.isEmpty) "" else suffix
      }
    }
    /**
     * The `__debounce.<ms>ms` modifier of `data-on`, with `.leading` and `.notrailing` appended when the flags are set.
     */
    final case class Debounce(duration: Duration, leading: Boolean = false, notrailing: Boolean = false)
        extends EventModifier {
      /**
       * `__debounce.<ms>ms`, followed by `.leading` and/or `.notrailing` when those flags are set.
       */
      val render: String     = {
        val base = s"__debounce.${duration.toMillis}ms"
        (leading, notrailing) match {
          case (false, false) => base
          case (true, false)  => s"$base.leading"
          case (false, true)  => s"$base.notrailing"
          case (true, true)   => s"$base.leading.notrailing"
        }
      }
      /**
       * Returns a copy with `leading` set to `true`.
       */
      def lead: Debounce     = copy(leading = true)
      /**
       * Returns a copy with `notrailing` set to `false`, so the suffix has no `.notrailing`.
       */
      def trailing: Debounce = copy(notrailing = false)
    }
    /**
     * The `__delay.<ms>ms` modifier of `data-on`. It delays the expression by `duration`.
     */
    final case class Delay(duration: Duration)                      extends EventModifier {
      /**
       * `__delay.<ms>ms`, with the duration in whole milliseconds.
       */
      val render: String = s"__delay.${duration.toMillis}ms"
    }
    /**
     * A modifier that adds nothing to the attribute name of `data-on`.
     */
    case object None                                                extends EventModifier {
      /**
       * An empty string.
       */
      val render: String = ""
    }
    /**
     * The `__once` modifier of `data-on`.
     */
    case object Once                                                extends OptionLess
    /**
     * The `__outside` modifier of `data-on`.
     */
    case object Outside                                             extends OptionLess
    /**
     * The `__passive` modifier of `data-on`.
     */
    case object Passive                                             extends OptionLess
    /**
     * The `__prevent` modifier of `data-on`.
     */
    case object Prevent                                             extends OptionLess
    /**
     * The `__stop` modifier of `data-on`.
     */
    case object Stop                                                extends OptionLess
    /**
     * The `__throttle.<ms>ms` modifier of `data-on`, with `.noleading` and `.trailing` appended when the flags are set.
     */
    final case class Throttle(duration: Duration, noleading0: Boolean = false, trailing0: Boolean = false)
        extends EventModifier {
      /**
       * Returns a copy with `noleading0` set to `true`, so the suffix ends in `.noleading`.
       */
      def noleading: Throttle = copy(noleading0 = true)
      /**
       * `__throttle.<ms>ms`, followed by `.noleading` and/or `.trailing` when those flags are set.
       */
      val render: String      = {
        val base = s"__throttle.${duration.toMillis}ms"
        (noleading0, trailing0) match {
          case (false, false) => base
          case (true, false)  => s"$base.noleading"
          case (false, true)  => s"$base.trailing"
          case (true, true)   => s"$base.noleading.trailing"
        }
      }
      /**
       * Returns a copy with `trailing0` set to `false`. The rendered suffix does not gain `.trailing`.
       */
      def trailing: Throttle  = copy(trailing0 = false)
    }
    /**
     * The `__viewtransition` modifier of `data-on`.
     */
    case object ViewTransition extends OptionLess
    /**
     * The `__window` modifier of `data-on`.
     */
    case object Window   extends OptionLess
    /**
     * The `__document` modifier of `data-on`.
     */
    case object Document extends OptionLess
  }

  /**
   * A letter case for signal names and attribute keys: camel, kebab, snake or pascal.
   */
  sealed trait CaseModifier extends Product with Serializable { self =>
    /**
     * Converts `original` to this case with zio-json's `CamelCase`, `KebabCase`, `SnakeCase` or `PascalCase`. A leading `_` is kept in front of the converted text and is not converted.
     */
    def modify(original: String): String = {
      val privateSignal = original.startsWith("_")
      val toModify      = if (privateSignal) original.drop(1) else original
      val modified      =
        self match {
          case CaseModifier.Camel  => zio.json.CamelCase(toModify)
          case CaseModifier.Kebab  => zio.json.KebabCase(toModify)
          case CaseModifier.Snake  => zio.json.SnakeCase(toModify)
          case CaseModifier.Pascal => zio.json.PascalCase(toModify)
        }
      if (privateSignal) "_" + modified else modified
    }

    /**
     * Returns an empty string when this case equals `default`, otherwise `__case.camel`, `__case.kebab`, `__case.snake` or `__case.pascal`.
     */
    def suffix(default: CaseModifier): String = self match {
      case `default`           => ""
      case CaseModifier.Camel  => "__case.camel"
      case CaseModifier.Kebab  => "__case.kebab"
      case CaseModifier.Snake  => "__case.snake"
      case CaseModifier.Pascal => "__case.pascal"
    }
  }

  /**
   * The four letter cases.
   */
  object CaseModifier {
    /**
     * Camel case.
     */
    case object Camel  extends CaseModifier
    /**
     * Kebab case.
     */
    case object Kebab  extends CaseModifier
    /**
     * Snake case.
     */
    case object Snake  extends CaseModifier
    /**
     * Pascal case.
     */
    case object Pascal extends CaseModifier
  }
}
