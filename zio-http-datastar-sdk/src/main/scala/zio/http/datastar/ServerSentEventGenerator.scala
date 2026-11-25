package zio.http.datastar

import zio._

import zio.schema.Schema

import zio.http.ServerSentEvent
import zio.http.internal.StringBuilderPool
import zio.http.template2.Dom.AttributeValue
import zio.http.template2._

/**
 * The type of a Datastar server-sent event, written in the `event` field of the event.
 */
sealed trait EventType extends Product with Serializable {
  /** Returns the name written in the `event` field. */
  def render: String
  final override def toString: String = this.render
}

/** The two event types that Datastar sends. */
object EventType {
  /**
   * Patches elements of the page. Its name on the wire is `datastar-patch-elements`.
   */
  case object PatchElements extends EventType {
    override def render: String = "datastar-patch-elements"
  }
  /** Patches signals. Its name on the wire is `datastar-patch-signals`. */
  case object PatchSignals  extends EventType {
    override def render: String = "datastar-patch-signals"
  }
}

/**
 * How a patched element is merged into the page, sent in the `mode` line of an element patch. The mode `Outer` is the default and is not sent.
 */
sealed trait ElementPatchMode extends Product with Serializable {
  /**
   * Returns the lower-case name of the mode, for example `outer` or `prepend`.
   */
  final def render: String            = this.productPrefix.toLowerCase
  final override def toString: String = this.render
}

/** The modes that Datastar supports for an element patch. */
object ElementPatchMode {

  /** Morph entire element, preserving state */
  case object Outer extends ElementPatchMode

  /** Morph inner HTML only, preserving state */
  case object Inner extends ElementPatchMode

  /** Replace entire element, reset state */
  case object Replace extends ElementPatchMode

  /** Insert at beginning inside target */
  case object Prepend extends ElementPatchMode

  /** Insert at end inside target */
  case object Append extends ElementPatchMode

  /** Insert before target element */
  case object Before extends ElementPatchMode

  /** Insert after target element */
  case object After extends ElementPatchMode

  /** Remove target element from DOM */
  case object Remove extends ElementPatchMode

  /**
   * A schema that reads and writes a mode as its lower-case name. Reading any other string fails with the message `Invalid ElementPatchMode:` followed by the string.
   */
  implicit val schema: Schema[ElementPatchMode] = Schema[String].transformOrFail(
    {
      case "outer"   => Right(Outer)
      case "inner"   => Right(Inner)
      case "replace" => Right(Replace)
      case "prepend" => Right(Prepend)
      case "append"  => Right(Append)
      case "before"  => Right(Before)
      case "after"   => Right(After)
      case "remove"  => Right(Remove)
      case other     => Left(s"Invalid ElementPatchMode: $other")
    },
    {
      case Outer   => Right("outer")
      case Inner   => Right("inner")
      case Replace => Right("replace")
      case Prepend => Right("prepend")
      case Append  => Right("append")
      case Before  => Right("before")
      case After   => Right("after")
      case Remove  => Right("remove")
    },
  )
}

/**
 * Options for sending an element patch.
 *
 * @param selector
 *   The element to patch. No `selector` line is sent when it is `None`.
 * @param mode
 *   How the HTML is merged into the target element.
 * @param useViewTransition
 *   Asks the browser to run the update as a view transition.
 * @param namespace
 *   The namespace sent in a `namespace` line when it is defined.
 * @param eventId
 *   The `id` of the server-sent event.
 * @param retryDuration
 *   The `retry` field of the server-sent event. It is sent only when it is not 1000 milliseconds.
 */
final case class PatchElementOptions(
  selector: Option[CssSelector] = None,
  mode: ElementPatchMode = ElementPatchMode.Outer,
  useViewTransition: Boolean = false,
  namespace: Option[String] = None,
  eventId: Option[String] = None,
  retryDuration: Duration = 1000.millis,
)

/** Holds the default options for an element patch. */
object PatchElementOptions {
  /** Options with every field at its default value. */
  val default: PatchElementOptions = PatchElementOptions()
}

/**
 * Options for sending a signal patch.
 *
 * @param onlyIfMissing
 *   When true, an `onlyIfMissing true` line is sent.
 * @param eventId
 *   The `id` of the server-sent event.
 * @param retryDuration
 *   The `retry` field of the server-sent event. It is sent only when it is not 1000 milliseconds.
 */
final case class PatchSignalOptions(
  onlyIfMissing: Boolean = false,
  eventId: Option[String] = None,
  retryDuration: Duration = 1000.millis,
)

/** Holds the default options for a signal patch. */
object PatchSignalOptions {
  /** Options with every field at its default value. */
  val default: PatchSignalOptions = PatchSignalOptions()
}

/**
 * Options for sending a script. The script is sent as an element patch that appends a `script` element to the `body`.
 *
 * @param autoRemove
 *   When true, the element gets the attribute `data-effect="el.remove()"`.
 * @param attributes
 *   Name and value pairs that are added to the element as attributes.
 * @param eventId
 *   The `id` of the server-sent event.
 * @param retryDuration
 *   The `retry` field of the server-sent event. It is sent only when it is not 1000 milliseconds.
 */
final case class ExecuteScriptOptions(
  autoRemove: Boolean = true,
  attributes: Seq[(String, String)] = Seq.empty,
  eventId: Option[String] = None,
  retryDuration: Duration = 1000.millis,
)

/** Holds the default options for sending a script. */
object ExecuteScriptOptions {
  /** Options with every field at its default value. */
  val default: ExecuteScriptOptions = ExecuteScriptOptions()
}

/**
 * Options for dispatching a browser `CustomEvent`. The event is sent as a script, so `autoRemove`, `eventId` and `retryDuration` apply to that script.
 *
 * @param source
 *   The element to dispatch the event on. The event goes to the `document` when it is `None`, and nothing is dispatched when no element matches the selector.
 * @param bubbles
 *   Whether the event bubbles.
 * @param cancelable
 *   Whether the event is cancelable.
 * @param composed
 *   Whether the event crosses shadow DOM boundaries.
 * @param autoRemove
 *   When true, the script element gets the attribute `data-effect="el.remove()"`.
 * @param eventId
 *   The `id` of the server-sent event.
 * @param retryDuration
 *   The `retry` field of the server-sent event. It is sent only when it is not 1000 milliseconds.
 */
final case class DispatchEventOptions(
  source: Option[CssSelector] = None,
  bubbles: Boolean = true,
  cancelable: Boolean = false,
  composed: Boolean = false,
  autoRemove: Boolean = true,
  eventId: Option[String] = None,
  retryDuration: Duration = 1000.millis,
)

/** Holds the default options for dispatching an event. */
object DispatchEventOptions {
  /** Options with every field at its default value. */
  val default: DispatchEventOptions = DispatchEventOptions()
}

/**
 * Sends Datastar events from inside a handler passed to `events`. Each method queues one event on the `Datastar` in the environment and completes once the event is queued.
 */
object ServerSentEventGenerator {

  private[datastar] val DefaultRetryDelay: Duration = 1000.millis

  /**
   * Sends a script to the browser by appending a `script` element to the `body`. `script0` is the JavaScript source. The element gets the attribute `data-effect="el.remove()"`.
   */
  def executeScript(script0: Js): ZIO[Datastar, Nothing, Unit] =
    executeScript(script(script0), ExecuteScriptOptions.default)

  /**
   * Sends a script to the browser by appending a `script` element to the `body`. `script0` is the JavaScript source. `options.autoRemove`, `options.attributes`, `options.eventId` and `options.retryDuration` set the removal attribute, the extra attributes, the event id and the retry duration.
   */
  def executeScript(script0: Js, options: ExecuteScriptOptions): ZIO[Datastar, Nothing, Unit] =
    executeScript(script(script0), options)

  /**
   * Sends a script to the browser by appending a `script` element to the `body`. `script0` is the JavaScript source. The element gets the attribute `data-effect="el.remove()"`.
   */
  def executeScript(script0: String): ZIO[Datastar, Nothing, Unit] =
    executeScript(script0, ExecuteScriptOptions.default)

  /**
   * Sends a script to the browser by appending a `script` element to the `body`. `script0` is the JavaScript source. `options.autoRemove`, `options.attributes`, `options.eventId` and `options.retryDuration` set the removal attribute, the extra attributes, the event id and the retry duration.
   */
  def executeScript(script0: String, options: ExecuteScriptOptions): ZIO[Datastar, Nothing, Unit] =
    executeScript(script(script0), options)

  /**
   * Sends a script to the browser by appending a `script` element to the `body`. `script0` is the `script` element to send. The element gets the attribute `data-effect="el.remove()"`.
   */
  def executeScript(script0: Dom.Element.Script): ZIO[Datastar, Nothing, Unit] =
    executeScript(script0, ExecuteScriptOptions.default)

  /**
   * Sends a script to the browser by appending a `script` element to the `body`. `script0` is the `script` element to send. `options.autoRemove`, `options.attributes`, `options.eventId` and `options.retryDuration` set the removal attribute, the extra attributes, the event id and the retry duration.
   */
  def executeScript(
    script0: Dom.Element.Script,
    options: ExecuteScriptOptions,
  ): ZIO[Datastar, Nothing, Unit] = {
    val removeAttr =
      if (options.autoRemove) Dom.attr("data-effect", AttributeValue.StringValue("el.remove()")) else Dom.empty
    patchElements(
      script0(removeAttr)(options.attributes.map(a => Dom.attr(a._1, AttributeValue.StringValue(a._2)))),
      PatchElementOptions(
        eventId = options.eventId,
        retryDuration = options.retryDuration,
        selector = Some(body),
        mode = ElementPatchMode.Append,
      ),
    )
  }

  /**
   * Dispatches a browser `CustomEvent` named `eventName`. The event is sent as a script, like `executeScript`, and is dispatched on the `document`. The `detail` of the event is the JSON encoding of `payload`, which is encoded with its `Schema`.
   */
  def dispatchEvent[T <: Product: Schema](eventName: String, payload: T): ZIO[Datastar, Nothing, Unit] =
    dispatchEvent(eventName, payload, DispatchEventOptions.default)

  /**
   * Dispatches a browser `CustomEvent` named `eventName`. The event is sent as a script, like `executeScript`, and is dispatched on the `document` unless `options.source` is defined. The `detail` of the event is the JSON encoding of `payload`, which is encoded with its `Schema`.
   */
  def dispatchEvent[T <: Product: Schema](
    eventName: String,
    payload: T,
    options: DispatchEventOptions,
  ): ZIO[Datastar, Nothing, Unit] = {
    val event = DatastarEvent.dispatchEvent(eventName, payload, options)
    executeScript(
      event.script,
      ExecuteScriptOptions(
        autoRemove = options.autoRemove,
        eventId = options.eventId,
        retryDuration = options.retryDuration,
      ),
    )
  }

  /**
   * Dispatches a browser `CustomEvent` named `eventName`. The event is sent as a script, like `executeScript`, and is dispatched on the `document`. `payload` is a JavaScript expression that becomes the `detail` of the event.
   */
  def dispatchEvent(eventName: String, payload: Js): ZIO[Datastar, Nothing, Unit] =
    dispatchEvent(eventName, payload, DispatchEventOptions.default)

  /**
   * Dispatches a browser `CustomEvent` named `eventName`. The event is sent as a script, like `executeScript`, and is dispatched on the `document` unless `options.source` is defined. `payload` is a JavaScript expression that becomes the `detail` of the event.
   */
  def dispatchEvent(eventName: String, payload: Js, options: DispatchEventOptions): ZIO[Datastar, Nothing, Unit] = {
    val event = DatastarEvent.dispatchEvent(eventName, payload, options)
    executeScript(
      event.script,
      ExecuteScriptOptions(
        autoRemove = options.autoRemove,
        eventId = options.eventId,
        retryDuration = options.retryDuration,
      ),
    )
  }

  /**
   * Patches the page with the given HTML. `elements` is sent as raw HTML, and `options` sets the selector, mode, view transition, namespace, event id and retry duration.
   */
  def patchElements(
    elements: String,
    options: PatchElementOptions,
  ): ZIO[Datastar, Nothing, Unit] =
    patchElements(Dom.raw(elements), options)

  /**
   * Patches the page with the given HTML. `elements` is sent as raw HTML with `PatchElementOptions.default`.
   */
  def patchElements(
    elements: String,
  ): ZIO[Datastar, Nothing, Unit] =
    patchElements(elements, PatchElementOptions.default)

  /**
   * Patches the page with the given HTML. `elements` is rendered to minified HTML, and `options` sets the selector, mode, view transition, namespace, event id and retry duration. When the rendered HTML has several lines, each line is sent as its own `elements` line.
   */
  def patchElements(
    elements: Dom,
    options: PatchElementOptions,
  ): ZIO[Datastar, Nothing, Unit] = {

    StringBuilderPool.withStringBuilder { sb =>
      options.selector.foreach(s => sb.append("selector ").append(s.render).append('\n'))

      if (options.mode != ElementPatchMode.Outer) {
        sb.append("mode ").append(options.mode.render).append('\n')
      }

      if (options.useViewTransition) {
        sb.append("useViewTransition true\n")
      }

      options.namespace.foreach(ns => sb.append("namespace ").append(ns).append('\n'))

      val rendered = elements.renderMinified
      if (rendered.contains('\n'))
        rendered.split('\n').foreach(line => sb.append("elements ").append(line).append('\n'))
      else
        sb.append("elements ").append(rendered).append('\n')

      val retry = if (options.retryDuration != DefaultRetryDelay) Some(options.retryDuration) else None
      send(EventType.PatchElements, sb.toString(), options.eventId, retry)
    }
  }

  /**
   * Patches the page with the given HTML. `element` is rendered to minified HTML and sent with `PatchElementOptions.default`.
   */
  def patchElements(
    element: Dom,
  ): ZIO[Datastar, Nothing, Unit] =
    patchElements(element, PatchElementOptions.default)

  /**
   * Patches the page with the given HTML. The elements are rendered together, one after the other, and sent with `PatchElementOptions.default`.
   */
  def patchElements(
    elements: Iterable[Dom],
  ): ZIO[Datastar, Nothing, Unit] =
    patchElements(elements, PatchElementOptions.default)

  /**
   * Patches the page with the given HTML. The elements are rendered together, one after the other, and `options` sets the selector, mode, view transition, namespace, event id and retry duration.
   */
  def patchElements(
    elements: Iterable[Dom],
    options: PatchElementOptions,
  ): ZIO[Datastar, Nothing, Unit] =
    patchElements(Dom.Fragment(elements), options)

  /**
   * Patches the signals in the browser. `signal` is JSON text that holds the signals to patch, and `options` sets `onlyIfMissing`, the event id and the retry duration.
   */
  def patchSignals(
    signal: String,
    options: PatchSignalOptions,
  ): ZIO[Datastar, Nothing, Unit] =
    patchSignals(Iterable(signal), options)

  /**
   * Patches the signals in the browser. `signal` is JSON text that holds the signals to patch.
   */
  def patchSignals(
    signal: String,
  ): ZIO[Datastar, Nothing, Unit] =
    patchSignals(Iterable(signal), PatchSignalOptions.default)

  /**
   * Patches the signals in the browser. Each string is JSON text and is sent as its own `signals` line.
   */
  def patchSignals(
    signals: Iterable[String],
  ): ZIO[Datastar, Nothing, Unit] =
    patchSignals(signals, PatchSignalOptions.default)

  /**
   * Patches the signals in the browser. Each string is JSON text and is sent as its own `signals` line. `options` sets `onlyIfMissing`, the event id and the retry duration.
   */
  def patchSignals(
    signals: Iterable[String],
    options: PatchSignalOptions,
  ): ZIO[Datastar, Nothing, Unit] = {

    StringBuilderPool.withStringBuilder { sb =>
      if (options.onlyIfMissing) {
        sb.append("onlyIfMissing true\n")
      }

      signals.foreach(s => sb.append("signals ").append(s).append('\n'))

      val retry = if (options.retryDuration != DefaultRetryDelay) Some(options.retryDuration) else None
      send(EventType.PatchSignals, sb.toString(), options.eventId, retry)
    }
  }

  private def send(
    eventType: EventType,
    dataLines: String,
    eventId: Option[String],
    retry: Option[Duration],
  ): ZIO[Datastar, Nothing, Unit] =
    ZIO.serviceWithZIO[Datastar] { `d*` =>
      val retry0 = if (retry.contains(DefaultRetryDelay)) None else retry
      `d*`.queue
        .offer(ServerSentEvent(data = dataLines, eventType = Some(eventType.render), id = eventId, retry = retry0))
        .unit
    }

}
