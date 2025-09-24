package zio.http.datastar

import java.time.Duration

import scala.language.implicitConversions

import zio.schema.StandardType

import zio.http.template2._

trait Attributes {
  self =>

  private[datastar] val prefix: String

  import Attributes._

  /**
   * data-attr-* – Set arbitrary attributes. Doc:
   * [[https://data-star.dev/reference/attributes#data-attr]]
   */
  final def dataAttr(attr: Dom.PartialAttribute): DatastarAttribute =
    DatastarAttribute(s"$prefix-attr-${attr.name}")

  /**
   * data-attr-* – Set arbitrary attributes. Doc:
   * [[https://data-star.dev/reference/attributes#data-attr]]
   */
  final def dataAttr(attr: Dom.PartialMultiAttribute): DatastarAttribute =
    DatastarAttribute(s"$prefix-attr-${attr.name}")

  /**
   * data-attr-* – Set arbitrary attributes. Doc:
   * [[https://data-star.dev/reference/attributes#data-attr]]
   */
  final def dataAttr(attr: String): DatastarAttribute =
    DatastarAttribute(s"$prefix-attr-$attr")

  final def dataAttr: DatastarAttribute = DatastarAttribute(s"$prefix-attr")

  /**
   * data-bind-* – Binds the given signal name to the value of input, select,
   * textarea or web components. Doc:
   * [[https://data-star.dev/reference/attributes#data-bind]]
   */
  final def dataBind: PartialDataBind = PartialDataBind(prefix)

  /**
   * data-bind-* – Binds the given signal name to the value of input, select,
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
   * data-on* – Event listener. Doc:
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
   * data-on-load – Execute when element loads. Doc:
   * [[https://data-star.dev/reference/attributes#data-on-load]]
   */
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
    Dom.attr(s"$prefix-preserve-attr", (attribute +: attributes).map(_.name).mkString(" "))

  /**
   * data-preserve-attr – Preserve attributes during morphing. Doc:
   * [[https://data-star.dev/reference/attributes#data-preserve-attr]]
   */
  final def dataPreserveAttr(attribute: String, attributes: String*): Dom.Attribute =
    Dom.attr(s"$prefix-preserve-attr", (attribute +: attributes).mkString(" "))

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
   * data-style-* – Dynamically set inline style(s). Doc:
   * [[https://data-star.dev/reference/attributes#data-style]]
   */
  final def dataStyle(styleName: String): DatastarAttribute = DatastarAttribute(s"$prefix-style-$styleName")

  /**
   * data-text – Sets element text content from an expression / signal. Doc:
   * [[https://data-star.dev/reference/attributes#data-text]]
   */
  final def dataText: DatastarAttribute = DatastarAttribute(s"$prefix-text")

}

object Attributes {
  import Dom._

  sealed trait DataClass {
    private[datastar] def full: String

    def :=(value: Js): CompleteAttribute = Dom.attr(full) := value.value
  }

  object DataClass {

    final case class Multi(prefix: String) extends DataClass {
      private[datastar] val full = s"$prefix-class"
    }

    private val defaultCaseModifier = CaseModifier.Kebab

    final case class Single(prefix: String, className: String, caseModifier: CaseModifier = defaultCaseModifier)
        extends DataClass {
      assert(className.nonEmpty, "Class name cannot be empty")
      private[datastar] val full = s"$prefix-class-$className${caseModifier.suffix(defaultCaseModifier)}"

      def :=(signal: Signal[_]): CompleteAttribute = Dom.attr(full) := signal.name.ref

      def camel: Single  = copy(caseModifier = CaseModifier.Camel)
      def kebab: Single  = copy(caseModifier = CaseModifier.Kebab)
      def snake: Single  = copy(caseModifier = CaseModifier.Snake)
      def pascal: Single = copy(caseModifier = CaseModifier.Pascal)
    }
  }

  final case class PartialSignalAttr(attrName: String, caseModifier: CaseModifier = CaseModifier.Camel) {
    def apply[A: StandardType](signal: String): SignalAttr[A]     =
      SignalAttr(attrName, SignalName(signal).toSignal, caseModifier)
    def apply[A: StandardType](signal: SignalName): SignalAttr[A] = SignalAttr(attrName, signal.toSignal, caseModifier)
    def apply[A: StandardType](signal: Signal[A]): SignalAttr[A]  = SignalAttr(attrName, signal, caseModifier)
  }

  final case class SignalAttr[A](attrName: String, signal: Signal[A], caseModifier: CaseModifier = CaseModifier.Camel) {
    private val full = s"$attrName-${signal.name.name}${caseModifier.suffix(CaseModifier.Camel)}"

    def :=(expression: Js): Attribute = Dom.attr(full) := expression.value
  }

  object SignalAttr {
    implicit def toAttribute[A](attr: SignalAttr[A]): Attribute =
      Dom.attr(attr.full) := attr.signal.name.ref
  }

  final case class PartialSignalsAttr(
    prefix: String,
    caseModifier: CaseModifier = CaseModifier.Camel,
    ifMissing: Boolean = false,
  ) {
    def apply[A: StandardType](signal: String): SignalsAttr[A]     =
      SignalsAttr(prefix, SignalName(signal).toSignal[A], caseModifier)
    def apply[A: StandardType](signal: SignalName): SignalsAttr[A] =
      SignalsAttr(prefix, signal.toSignal[A], caseModifier)
    def apply[A: StandardType](signal: Signal[A]): SignalsAttr[A]  = SignalsAttr(prefix, signal, caseModifier)

    def :=(expression: Js): Attribute =
      Dom.attr(s"$prefix-signals${caseModifier.suffix(CaseModifier.Camel)}") := expression.value
  }

  final case class SignalsAttr[A: StandardType](
    prefix: String,
    signal: Signal[A],
    caseModifier: CaseModifier,
    ifMissing: Boolean = false,
  ) {
    private val full = {
      val ifMissing0: String = if (ifMissing) "__if-missing" else ""
      s"$prefix-signals-${signal.name.name}${caseModifier.suffix(CaseModifier.Camel)}$ifMissing0"
    }

    def :=(expression: Js): Attribute = Dom.attr(full) := expression.value

    def camel: SignalsAttr[A]  = copy(caseModifier = CaseModifier.Camel)
    def kebab: SignalsAttr[A]  = copy(caseModifier = CaseModifier.Kebab)
    def snake: SignalsAttr[A]  = copy(caseModifier = CaseModifier.Snake)
    def pascal: SignalsAttr[A] = copy(caseModifier = CaseModifier.Pascal)
  }

  final case class PartialDataIndicator(prefix: String, caseModifier: CaseModifier = CaseModifier.Camel) {
    def apply(signal: String): DataIndicatorAttr =
      DataIndicatorAttr(prefix, SignalName(signal).toSignal[Boolean], caseModifier)

    def apply(signal: SignalName): DataIndicatorAttr =
      DataIndicatorAttr(prefix, signal.toSignal[Boolean], caseModifier)

    def apply(signal: Signal[Boolean]): DataIndicatorAttr =
      DataIndicatorAttr(prefix, signal, caseModifier)

    def camel: PartialDataIndicator  = copy(caseModifier = CaseModifier.Camel)
    def kebab: PartialDataIndicator  = copy(caseModifier = CaseModifier.Kebab)
    def snake: PartialDataIndicator  = copy(caseModifier = CaseModifier.Snake)
    def pascal: PartialDataIndicator = copy(caseModifier = CaseModifier.Pascal)
  }

  final case class DataIndicatorAttr(
    prefix: String,
    signal: Signal[Boolean],
    caseModifier: CaseModifier = CaseModifier.Camel,
  ) {
    private val full = s"$prefix-indicator-${signal.name.name}${caseModifier.suffix(CaseModifier.Camel)}"

    def camel: DataIndicatorAttr  = copy(caseModifier = CaseModifier.Camel)
    def kebab: DataIndicatorAttr  = copy(caseModifier = CaseModifier.Kebab)
    def snake: DataIndicatorAttr  = copy(caseModifier = CaseModifier.Snake)
    def pascal: DataIndicatorAttr = copy(caseModifier = CaseModifier.Pascal)
  }

  object DataIndicatorAttr {
    implicit def toAttribute(attr: DataIndicatorAttr): Attribute =
      Dom.boolAttr(attr.full)
  }

  final case class DataJsonSignals(prefix: String, terseOn: Boolean = false) {
    def :=(expression: Js): Attribute =
      if (terseOn) Dom.attr(s"$prefix-json-signals__terse") := expression.value
      else Dom.attr(s"$prefix-json-signals")                := expression.value

    def terse: DataJsonSignals = copy(terseOn = true)

  }

  final case class DatastarAttribute(name: String) {

    def :=(expression: Js): Attribute = Dom.attr(name) := expression.value
  }

  final case class PartialDataOn(prefix: String, modifier: EventModifier) {
    def :=(expression: Js): Attribute = Dom.attr(s"$prefix-on") := expression.value
    def event(name: String)           = DataOn(prefix, name, modifier)

    def modify(mod: EventModifier): PartialDataOn = copy(modifier = modifier && mod)
    def capture: PartialDataOn                    = modify(EventModifier.Capture)
    def camel: PartialDataOn                      = modify(EventModifier.Case(CaseModifier.Camel))
    def kebab: PartialDataOn                      = modify(EventModifier.Case(CaseModifier.Kebab))
    def snake: PartialDataOn                      = modify(EventModifier.Case(CaseModifier.Snake))
    def pascal: PartialDataOn                     = modify(EventModifier.Case(CaseModifier.Pascal))
    def debounce(duration: Duration, leading: Boolean = false, notrail: Boolean = false): PartialDataOn =
      modify(EventModifier.Debounce(duration, leading, notrail))
    def delay(duration: Duration): PartialDataOn = modify(EventModifier.Delay(duration))
    def once: PartialDataOn                      = modify(EventModifier.Once)
    def outside: PartialDataOn                   = modify(EventModifier.Outside)
    def passive: PartialDataOn                   = modify(EventModifier.Passive)
    def prevent: PartialDataOn                   = modify(EventModifier.Prevent)
    def preventDefault: PartialDataOn            = modify(EventModifier.Prevent)
    def stop: PartialDataOn                      = modify(EventModifier.Stop)
    def stopPropagation: PartialDataOn           = modify(EventModifier.Stop)
    def throttle(duration: Duration, noleading: Boolean = false, trailing: Boolean = false): PartialDataOn =
      modify(EventModifier.Throttle(duration, noleading, trailing))
    def viewTransition: PartialDataOn = modify(EventModifier.ViewTransition)
    def window: PartialDataOn         = modify(EventModifier.Window)

    // ---------------------------------------------------------------------------
    // Mouse Events
    // ---------------------------------------------------------------------------
    def click: DataOn       = event("click")
    def dblclick: DataOn    = event("dblclick")
    def mousedown: DataOn   = event("mousedown")
    def mouseup: DataOn     = event("mouseup")
    def mouseover: DataOn   = event("mouseover")
    def mouseout: DataOn    = event("mouseout")
    def mouseenter: DataOn  = event("mouseenter")
    def mouseleave: DataOn  = event("mouseleave")
    def mousemove: DataOn   = event("mousemove")
    def contextmenu: DataOn = event("contextmenu")
    def wheel: DataOn       = event("wheel")

    // ---------------------------------------------------------------------------
    // Keyboard Events
    // ---------------------------------------------------------------------------
    def keydown: DataOn  = event("keydown")
    def keyup: DataOn    = event("keyup")
    def keypress: DataOn = event("keypress")

    // ---------------------------------------------------------------------------
    // Form Events
    // ---------------------------------------------------------------------------
    def input: DataOn   = event("input")
    def change: DataOn  = event("change")
    def submit: DataOn  = event("submit")
    def reset: DataOn   = event("reset")
    def select: DataOn  = event("select")
    def invalid: DataOn = event("invalid")

    // ---------------------------------------------------------------------------
    // Focus Events
    // ---------------------------------------------------------------------------
    def focus: DataOn    = event("focus")
    def blur: DataOn     = event("blur")
    def focusin: DataOn  = event("focusin")
    def focusout: DataOn = event("focusout")

    // ---------------------------------------------------------------------------
    // Touch Events
    // ---------------------------------------------------------------------------
    def touchstart: DataOn  = event("touchstart")
    def touchend: DataOn    = event("touchend")
    def touchmove: DataOn   = event("touchmove")
    def touchcancel: DataOn = event("touchcancel")

    // ---------------------------------------------------------------------------
    // Window/Document Events
    // ---------------------------------------------------------------------------
    def load: DataOn         = event("load")
    def unload: DataOn       = event("unload")
    def beforeunload: DataOn = event("beforeunload")
    def resize: DataOn       = event("resize")
    def scroll: DataOn       = event("scroll")
    def hashchange: DataOn   = event("hashchange")
    def popstate: DataOn     = event("popstate")

    // ---------------------------------------------------------------------------
    // Custom Events
    // ---------------------------------------------------------------------------
    def custom(eventName: String): DataOn = event(eventName)
  }

  final case class DataOn(prefix: String, event: String, modifier: EventModifier) {
    private val full = s"$prefix-on-$event${modifier.render}"

    def :=(expression: Js): Attribute = Dom.attr(full) := expression.value

    def modify(mod: EventModifier): DataOn = copy(modifier = modifier && mod)
    def capture: DataOn                    = modify(EventModifier.Capture)
    def camel: DataOn                      = modify(EventModifier.Case(CaseModifier.Camel))
    def kebab: DataOn                      = modify(EventModifier.Case(CaseModifier.Kebab))
    def snake: DataOn                      = modify(EventModifier.Case(CaseModifier.Snake))
    def pascal: DataOn                     = modify(EventModifier.Case(CaseModifier.Pascal))
    def debounce(duration: Duration, leading: Boolean = false, notrail: Boolean = false): DataOn =
      modify(EventModifier.Debounce(duration, leading, notrail))
    def delay(duration: Duration): DataOn = modify(EventModifier.Delay(duration))
    def once: DataOn                      = modify(EventModifier.Once)
    def outside: DataOn                   = modify(EventModifier.Outside)
    def passive: DataOn                   = modify(EventModifier.Passive)
    def prevent: DataOn                   = modify(EventModifier.Prevent)
    def preventDefault: DataOn            = modify(EventModifier.Prevent)
    def stop: DataOn                      = modify(EventModifier.Stop)
    def stopPropagation: DataOn           = modify(EventModifier.Stop)
    def throttle(duration: Duration, noleading: Boolean = false, trailing: Boolean = false): DataOn =
      modify(EventModifier.Throttle(duration, noleading, trailing))
    def viewTransition: DataOn = modify(EventModifier.ViewTransition)
    def window: DataOn         = modify(EventModifier.Window)
  }

  final case class DataOnIntersect(prefix: String, modifier: IntersectModifier) {
    def :=(expression: Js): Attribute = Dom.attr(s"$prefix-on-intersect") := expression.value

    def modify(mod: IntersectModifier): DataOnIntersect = copy(modifier = modifier && mod)
    def once: DataOnIntersect                           = modify(IntersectModifier.Once)
    def half: DataOnIntersect                           = modify(IntersectModifier.Half)
    def full: DataOnIntersect                           = modify(IntersectModifier.Full)
    def delay(duration: Duration): DataOnIntersect      = modify(IntersectModifier.Delay(duration))
    def debounce(duration: Duration, leading: Boolean = false, notrail: Boolean = false): DataOnIntersect    =
      modify(IntersectModifier.Debounce(duration, leading, notrail))
    def throttle(duration: Duration, noleading: Boolean = false, trailing: Boolean = false): DataOnIntersect =
      modify(IntersectModifier.Throttle(duration, noleading, trailing))
    def viewTransition: DataOnIntersect = modify(IntersectModifier.ViewTransition)
  }

  sealed trait IntersectModifier extends Product with Serializable {
    val render: String
    def &&(other: IntersectModifier): IntersectModifier  = and(other)
    def and(other: IntersectModifier): IntersectModifier = IntersectModifier.And(this, other)
  }

  sealed trait OptionLessIntersect extends IntersectModifier {
    final val render: String = s"__${productPrefix.toLowerCase}"
  }

  object IntersectModifier                                                      {
    final case class And(left: IntersectModifier, right: IntersectModifier) extends IntersectModifier {
      val render: String = s"${left.render}${right.render}"
    }
    case object None                                                        extends OptionLessIntersect
    case object Once                                                        extends OptionLessIntersect
    case object Half                                                        extends OptionLessIntersect
    case object Full                                                        extends OptionLessIntersect
    final case class Delay(duration: Duration)                              extends IntersectModifier {
      val render: String = s"__delay.${duration.toMillis}ms"
    }
    final case class Debounce(duration: Duration, leading: Boolean = false, notrail: Boolean = false)
        extends IntersectModifier {
      val render: String  = {
        val base = s"__debounce.${duration.toMillis}ms"
        (leading, notrail) match {
          case (false, false) => base
          case (true, false)  => s"$base.leading"
          case (false, true)  => s"$base.notrail"
          case (true, true)   => s"$base.leading.notrail"
        }
      }
      def lead: Debounce  = copy(leading = true)
      def trail: Debounce = copy(notrail = false)
    }
    final case class Throttle(duration: Duration, noleading0: Boolean = false, trailing0: Boolean = false)
        extends IntersectModifier {
      def noleading: Throttle = copy(noleading0 = true)
      val render: String      = {
        val base = s"__throttle.${duration.toMillis}ms"
        (noleading0, trailing0) match {
          case (false, false) => base
          case (true, false)  => s"$base.noleading"
          case (false, true)  => s"$base.trailing"
          case (true, true)   => s"$base.noleading.trailing"
        }
      }
      def trailing: Throttle  = copy(trailing0 = false)
    }
    case object ViewTransition                                              extends OptionLessIntersect
  }
  final case class DataOnInterval(prefix: String, modifier: OnIntervalModifier) {
    private val full = s"$prefix-on-interval${modifier.render}"

    def :=(expression: Js): Attribute = Dom.attr(full) := expression.value

    def duration(duration: Duration, leading: Boolean = false): DataOnInterval = copy(
      modifier = this.modifier && OnIntervalModifier.Duration(duration, leading),
    )
    def viewTransition: DataOnInterval = copy(modifier = this.modifier && OnIntervalModifier.ViewTransition)
  }

  sealed trait OnIntervalModifier extends Product with Serializable {
    val render: String
    def &&(other: OnIntervalModifier): OnIntervalModifier  = and(other)
    def and(other: OnIntervalModifier): OnIntervalModifier = OnIntervalModifier.And(this, other)
  }

  object OnIntervalModifier {
    final case class And(left: OnIntervalModifier, right: OnIntervalModifier) extends OnIntervalModifier {
      val render: String = s"${left.render}${right.render}"
    }
    case object None                                                          extends OnIntervalModifier {
      val render: String = ""
    }
    final case class Duration(duration: java.time.Duration, leading: Boolean) extends OnIntervalModifier {
      val render: String = {
        val base = s"__duration.${duration.toMillis}ms"
        if (leading) s"$base.leading" else base
      }
      def lead: Duration = copy(leading = true)
    }
    case object ViewTransition                                                extends OnIntervalModifier {
      val render: String = "__viewtransition"
    }
  }

  final case class DataOnLoad(prefix: String, modifier: LoadModifier) {
    private val full = s"$prefix-on-load${modifier.render}"

    def :=(expression: Js): Attribute = Dom.attr(full) := expression.value

    def delay(duration: Duration): DataOnLoad = copy(modifier = modifier && LoadModifier.Delay(duration))
    def viewTransition: DataOnLoad            = copy(modifier = modifier && LoadModifier.ViewTransition)
  }

  sealed trait LoadModifier extends Product with Serializable {
    val render: String
    def &&(other: LoadModifier): LoadModifier  = and(other)
    def and(other: LoadModifier): LoadModifier = LoadModifier.And(this, other)
  }

  object LoadModifier {
    final case class And(left: LoadModifier, right: LoadModifier) extends LoadModifier {
      val render: String = s"${left.render}${right.render}"
    }
    case object None                                              extends LoadModifier {
      val render: String = ""
    }
    final case class Delay(duration: java.time.Duration)          extends LoadModifier {
      val render: String = s"__delay.${duration.toMillis}ms"
    }
    case object ViewTransition                                    extends LoadModifier {
      val render: String = "__viewtransition"
    }
  }

  final case class DataOnSignalPatch(prefix: String, modifier: OnSignalPatchModifier) {
    private val full = s"$prefix-on-signal-patch${modifier.render}"

    def :=(expression: Js): Attribute = Dom.attr(full) := expression.value

    def delay(duration: Duration): DataOnSignalPatch                                                           =
      copy(modifier = modifier && OnSignalPatchModifier.Delay(duration))
    def debounce(duration: Duration, leading: Boolean = false, notrail: Boolean = false): DataOnSignalPatch    =
      copy(modifier = modifier && OnSignalPatchModifier.Debounce(duration, leading, notrail))
    def throttle(duration: Duration, noleading: Boolean = false, trailing: Boolean = false): DataOnSignalPatch =
      copy(modifier = modifier && OnSignalPatchModifier.Throttle(duration, noleading, trailing))
  }

  sealed trait OnSignalPatchModifier extends Product with Serializable {
    val render: String
    def &&(other: OnSignalPatchModifier): OnSignalPatchModifier  = and(other)
    def and(other: OnSignalPatchModifier): OnSignalPatchModifier = OnSignalPatchModifier.And(this, other)
  }

  object OnSignalPatchModifier {
    final case class And(left: OnSignalPatchModifier, right: OnSignalPatchModifier) extends OnSignalPatchModifier {
      val render: String = s"${left.render}${right.render}"
    }
    case object None                                                                extends OnSignalPatchModifier {
      val render: String = ""
    }
    final case class Delay(duration: java.time.Duration)                            extends OnSignalPatchModifier {
      val render: String = s"__delay.${duration.toMillis}ms"
    }
    final case class Debounce(duration: java.time.Duration, leading: Boolean = false, notrail: Boolean = false)
        extends OnSignalPatchModifier {
      val render: String  = {
        val base = s"__debounce.${duration.toMillis}ms"
        (leading, notrail) match {
          case (false, false) => base
          case (true, false)  => s"$base.leading"
          case (false, true)  => s"$base.notrail"
          case (true, true)   => s"$base.leading.notrail"
        }
      }
      def lead: Debounce  = copy(leading = true)
      def trail: Debounce = copy(notrail = false)
    }
    final case class Throttle(duration: java.time.Duration, noleading0: Boolean = false, trailing0: Boolean = false)
        extends OnSignalPatchModifier {
      def noleading: Throttle = copy(noleading0 = true)
      val render: String      = {
        val base = s"__throttle.${duration.toMillis}ms"
        (noleading0, trailing0) match {
          case (false, false) => base
          case (true, false)  => s"$base.noleading"
          case (false, true)  => s"$base.trailing"
          case (true, true)   => s"$base.noleading.trailing"
        }
      }
      def trailing: Throttle  = copy(trailing0 = false)
    }
  }

  final case class PartialDataBind(prefix: String, caseModifier: CaseModifier = CaseModifier.Camel) {
    def apply(signal: String): DataBind     = DataBind(prefix, SignalName(signal), caseModifier)
    def apply(signal: SignalName): DataBind = DataBind(prefix, signal, caseModifier)

    def camel: PartialDataBind  = copy(caseModifier = CaseModifier.Camel)
    def kebab: PartialDataBind  = copy(caseModifier = CaseModifier.Kebab)
    def snake: PartialDataBind  = copy(caseModifier = CaseModifier.Snake)
    def pascal: PartialDataBind = copy(caseModifier = CaseModifier.Pascal)
  }

  final case class DataBind(prefix: String, signalName: SignalName, caseModifier: CaseModifier = CaseModifier.Camel) {
    private val full = s"$prefix-bind-${signalName.name}${caseModifier.suffix(CaseModifier.Camel)}"

    def camel: DataBind  = copy(caseModifier = CaseModifier.Camel)
    def kebab: DataBind  = copy(caseModifier = CaseModifier.Kebab)
    def snake: DataBind  = copy(caseModifier = CaseModifier.Snake)
    def pascal: DataBind = copy(caseModifier = CaseModifier.Pascal)
  }

  object DataBind {
    implicit def toAttribute(attr: DataBind): Attribute = Dom.boolAttr(attr.full)
  }

  final case class PartialDataRef(prefix: String, caseModifier: CaseModifier = CaseModifier.Kebab) {
    def apply(signal: String): DataRef     = DataRef(prefix, SignalName(signal), caseModifier)
    def apply(signal: SignalName): DataRef = DataRef(prefix, signal, caseModifier)

    def camel: PartialDataRef  = copy(caseModifier = CaseModifier.Camel)
    def kebab: PartialDataRef  = copy(caseModifier = CaseModifier.Kebab)
    def snake: PartialDataRef  = copy(caseModifier = CaseModifier.Snake)
    def pascal: PartialDataRef = copy(caseModifier = CaseModifier.Pascal)
  }

  final case class DataRef(prefix: String, signalName: SignalName, caseModifier: CaseModifier = CaseModifier.Kebab) {
    private val full = s"$prefix-ref-${signalName.name}${caseModifier.suffix(CaseModifier.Kebab)}"

    def camel: DataRef  = copy(caseModifier = CaseModifier.Camel)
    def kebab: DataRef  = copy(caseModifier = CaseModifier.Kebab)
    def snake: DataRef  = copy(caseModifier = CaseModifier.Snake)
    def pascal: DataRef = copy(caseModifier = CaseModifier.Pascal)
  }

  object DataRef {
    implicit def toAttribute(attr: DataRef): Attribute = Dom.boolAttr(attr.full)
  }

  sealed trait EventModifier extends Product with Serializable {
    val render: String
    def &&(other: EventModifier): EventModifier  = and(other)
    def and(other: EventModifier): EventModifier = EventModifier.And(this, other)
  }

  sealed trait OptionLess extends EventModifier {
    final val render: String = s"__${productPrefix.toLowerCase}"
  }

  object EventModifier {
    final case class And(left: EventModifier, right: EventModifier) extends EventModifier {
      val render: String = s"${left.render}${right.render}"
    }
    case object Capture                                             extends OptionLess
    final case class Case(caseModifier: CaseModifier)               extends EventModifier {
      val render: String = {
        val suffix = caseModifier.suffix(CaseModifier.Kebab)
        if (suffix.isEmpty) "" else suffix
      }
    }
    final case class Debounce(duration: Duration, leading: Boolean = false, notrail: Boolean = false)
        extends EventModifier {
      val render: String  = {
        val base = s"__debounce.${duration.toMillis}ms"
        (leading, notrail) match {
          case (false, false) => base
          case (true, false)  => s"$base.leading"
          case (false, true)  => s"$base.notrail"
          case (true, true)   => s"$base.leading.notrail"
        }
      }
      def lead: Debounce  = copy(leading = true)
      def trail: Debounce = copy(notrail = false)
    }
    final case class Delay(duration: Duration)                      extends EventModifier {
      val render: String = s"__delay.${duration.toMillis}ms"
    }
    case object None                                                extends EventModifier {
      val render: String = ""
    }
    case object Once                                                extends OptionLess
    case object Outside                                             extends OptionLess
    case object Passive                                             extends OptionLess
    case object Prevent                                             extends OptionLess
    case object Stop                                                extends OptionLess
    final case class Throttle(duration: Duration, noleading0: Boolean = false, trailing0: Boolean = false)
        extends EventModifier {
      def noleading: Throttle = copy(noleading0 = true)
      val render: String      = {
        val base = s"__throttle.${duration.toMillis}ms"
        (noleading0, trailing0) match {
          case (false, false) => base
          case (true, false)  => s"$base.noleading"
          case (false, true)  => s"$base.trailing"
          case (true, true)   => s"$base.noleading.trailing"
        }
      }
      def trailing: Throttle  = copy(trailing0 = false)
    }
    case object ViewTransition extends OptionLess
    case object Window extends OptionLess
  }

  sealed trait CaseModifier extends Product with Serializable {
    def suffix(default: CaseModifier): String = this match {
      case `default`           => ""
      case CaseModifier.Camel  => "__case.camel"
      case CaseModifier.Kebab  => "__case.kebab"
      case CaseModifier.Snake  => "__case.snake"
      case CaseModifier.Pascal => "__case.pascal"
    }
  }

  object CaseModifier {
    case object Camel  extends CaseModifier
    case object Kebab  extends CaseModifier
    case object Snake  extends CaseModifier
    case object Pascal extends CaseModifier
  }
}
