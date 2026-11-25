package zio.http.datastar

import zio._
import zio.json._
import zio.json.ast.Json

import zio.schema.Schema

import zio.http.ServerSentEvent
import zio.http.datastar.ServerSentEventGenerator.DefaultRetryDelay
import zio.http.template2.Dom.AttributeValue
import zio.http.template2._

/**
 * A Datastar event: an element patch, a signal patch, or a script to run.
 * Values come from the methods of the `DatastarEvent` companion. Return them
 * from the handlers passed to `event` and `events`, or turn them into a
 * `ServerSentEvent` with `toServerSentEvent`.
 */
sealed trait DatastarEvent extends Product with Serializable {

  /**
   * Returns the Datastar event type that this event is sent as. Script events
   * are sent as `datastar-patch-elements`, the same type as element patches.
   */
  def eventType: EventType

  /**
   * Returns this event as a `ServerSentEvent` whose data lines follow the
   * Datastar protocol, for example `selector`, `mode` and `elements` lines for
   * an element patch. The `retry` field is set only when the retry duration is
   * not 1000 milliseconds.
   */
  def toServerSentEvent: ServerSentEvent[String]
}

/**
 * Constructors for `DatastarEvent` values. They build an event without sending
 * it. To send events from inside a handler passed to `events`, use
 * `ServerSentEventGenerator`.
 */
object DatastarEvent {

  private[http] final case class PatchElements(
    elements: Dom,
    selector: Option[CssSelector] = None,
    mode: ElementPatchMode = ElementPatchMode.Outer,
    useViewTransition: Boolean = false,
    namespace: Option[String] = None,
    eventId: Option[String] = None,
    retryDuration: Duration = 1000.millis,
  ) extends DatastarEvent {
    assert(
      mode != ElementPatchMode.Remove || (selector.nonEmpty && elements.isEmpty),
      "When using mode 'remove', 'selector' must be defined and 'elements' must be empty",
    )
    override val eventType: EventType                       = EventType.PatchElements
    override def toServerSentEvent: ServerSentEvent[String] = {
      val sb = new StringBuilder()

      selector.foreach(s => sb.append("selector ").append(s.render).append('\n'))

      if (mode != ElementPatchMode.Outer) {
        sb.append("mode ").append(mode.render).append('\n')
      }

      if (useViewTransition) {
        sb.append("useViewTransition true\n")
      }

      namespace.foreach(ns => sb.append("namespace ").append(ns).append('\n'))

      val rendered = elements.renderMinified
      if (rendered.contains('\n'))
        rendered.split('\n').foreach(line => sb.append("elements ").append(line).append('\n'))
      else
        sb.append("elements ").append(rendered).append('\n')

      val retry = if (retryDuration != DefaultRetryDelay) Some(retryDuration) else None
      ServerSentEvent(sb.toString(), Some(eventType.render), eventId, retry)
    }
  }

  private[http] final case class PatchSignals(
    signals: String,
    onlyIfMissing: Boolean = false,
    eventId: Option[String] = None,
    retryDuration: Duration = 1000.millis,
  ) extends DatastarEvent {
    override val eventType: EventType = EventType.PatchSignals

    override def toServerSentEvent: ServerSentEvent[String] = {
      val sb = new StringBuilder()

      if (onlyIfMissing) {
        sb.append("onlyIfMissing true\n")
      }

      sb.append("signals ").append(signals).append('\n')

      val retry = if (retryDuration != DefaultRetryDelay) Some(retryDuration) else None
      ServerSentEvent(sb.toString(), Some(eventType.render), eventId, retry)
    }
  }

  private[http] final case class ExecuteScript(
    script: Dom.Element.Script,
    eventId: Option[String] = None,
    retryDuration: Duration = 1000.millis,
  ) extends DatastarEvent {
    override val eventType: EventType = EventType.PatchElements

    override def toServerSentEvent: ServerSentEvent[String] = {
      val sb = new StringBuilder()
      sb.append("selector body\n")
      sb.append("mode append\n")

      val rendered = escapeScriptContent(script.render)
      if (rendered.contains('\n'))
        rendered.split('\n').foreach(line => sb.append("elements ").append(line).append('\n'))
      else
        sb.append("elements ").append(rendered).append('\n')

      val retry = if (retryDuration != DefaultRetryDelay) Some(retryDuration) else None
      ServerSentEvent(sb.toString(), Some(eventType.render), eventId, retry)
    }
  }

  private val ScriptEndPattern = "(?i)</script".r

  private def escapeScriptContent(rendered: String): String = {
    val closingTagIdx = rendered.lastIndexOf("</script>")
    if (closingTagIdx < 0) rendered
    else {
      val content    = rendered.substring(0, closingTagIdx)
      val closingTag = rendered.substring(closingTagIdx)
      ScriptEndPattern.replaceAllIn(content, "<\\\\/script") + closingTag
    }
  }

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * JavaScript source.
   */
  def executeScript(script0: Js): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions.default)

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * JavaScript source.
   *
   * Uses `options` for the remaining settings: the `autoRemove`, `eventId` and
   * `retryDuration` values come from `options`, and `options.attributes` are
   * added to the element as attributes.
   */
  def executeScript(script0: Js, options: ExecuteScriptOptions): ExecuteScript =
    executeScript(Dom.script(script0), options)

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * JavaScript source.
   *
   * When `autoRemove` is true, the element gets the attribute
   * `data-effect="el.remove()"`.
   */
  def executeScript(script0: Js, autoRemove: Boolean): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove))

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * JavaScript source.
   *
   * When `autoRemove` is true, the element gets the attribute
   * `data-effect="el.remove()"`. `attributes` are added to the element as
   * attributes.
   */
  def executeScript(script0: Js, autoRemove: Boolean, attributes: Seq[(String, String)]): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove, attributes = attributes))

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * JavaScript source.
   *
   * When `autoRemove` is true, the element gets the attribute
   * `data-effect="el.remove()"`. `attributes` are added to the element as
   * attributes, and `eventId` becomes the `id` of the server-sent event.
   */
  def executeScript(
    script0: Js,
    autoRemove: Boolean,
    attributes: Seq[(String, String)],
    eventId: Option[String],
  ): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove, attributes = attributes, eventId = eventId))

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * JavaScript source.
   *
   * When `autoRemove` is true, the element gets the attribute
   * `data-effect="el.remove()"`. `attributes` are added to the element as
   * attributes, `eventId` becomes the `id` of the server-sent event, and
   * `retryDuration` is sent as its `retry` field when it is not 1000
   * milliseconds.
   */
  def executeScript(
    script0: Js,
    autoRemove: Boolean,
    attributes: Seq[(String, String)],
    eventId: Option[String],
    retryDuration: Duration,
  ): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove, attributes, eventId, retryDuration))

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * JavaScript source.
   */
  def executeScript(script0: String): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions.default)

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * JavaScript source.
   *
   * Uses `options` for the remaining settings: the `autoRemove`, `eventId` and
   * `retryDuration` values come from `options`, and `options.attributes` are
   * added to the element as attributes.
   */
  def executeScript(script0: String, options: ExecuteScriptOptions): ExecuteScript =
    executeScript(Dom.script(script0), options)

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * JavaScript source.
   *
   * When `autoRemove` is true, the element gets the attribute
   * `data-effect="el.remove()"`.
   */
  def executeScript(script0: String, autoRemove: Boolean): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove))

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * JavaScript source.
   *
   * When `autoRemove` is true, the element gets the attribute
   * `data-effect="el.remove()"`. `attributes` are added to the element as
   * attributes.
   */
  def executeScript(script0: String, autoRemove: Boolean, attributes: Seq[(String, String)]): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove, attributes = attributes))

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * JavaScript source.
   *
   * When `autoRemove` is true, the element gets the attribute
   * `data-effect="el.remove()"`. `attributes` are added to the element as
   * attributes, and `eventId` becomes the `id` of the server-sent event.
   */
  def executeScript(
    script0: String,
    autoRemove: Boolean,
    attributes: Seq[(String, String)],
    eventId: Option[String],
  ): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove, attributes = attributes, eventId = eventId))

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * JavaScript source.
   *
   * When `autoRemove` is true, the element gets the attribute
   * `data-effect="el.remove()"`. `attributes` are added to the element as
   * attributes, `eventId` becomes the `id` of the server-sent event, and
   * `retryDuration` is sent as its `retry` field when it is not 1000
   * milliseconds.
   */
  def executeScript(
    script0: String,
    autoRemove: Boolean,
    attributes: Seq[(String, String)],
    eventId: Option[String],
    retryDuration: Duration,
  ): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove, attributes, eventId, retryDuration))

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * `script` element to send.
   */
  def executeScript(script0: Dom.Element.Script): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions.default)

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * `script` element to send.
   *
   * Uses `options` for the remaining settings: the `autoRemove`, `eventId` and
   * `retryDuration` values come from `options`, and `options.attributes` are
   * added to the element as attributes.
   */
  def executeScript(
    script0: Dom.Element.Script,
    options: ExecuteScriptOptions,
  ): ExecuteScript = {
    val removeAttr      =
      if (options.autoRemove) Dom.attr("data-effect", AttributeValue.StringValue("el.remove()")) else Dom.empty
    val scriptWithAttrs =
      script0(removeAttr)(options.attributes.map(a => Dom.attr(a._1, AttributeValue.StringValue(a._2))))

    ExecuteScript(
      script = scriptWithAttrs,
      eventId = options.eventId,
      retryDuration = options.retryDuration,
    )
  }

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * `script` element to send.
   *
   * When `autoRemove` is true, the element gets the attribute
   * `data-effect="el.remove()"`.
   */
  def executeScript(script0: Dom.Element.Script, autoRemove: Boolean): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove))

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * `script` element to send.
   *
   * When `autoRemove` is true, the element gets the attribute
   * `data-effect="el.remove()"`. `attributes` are added to the element as
   * attributes.
   */
  def executeScript(
    script0: Dom.Element.Script,
    autoRemove: Boolean,
    attributes: Seq[(String, String)],
  ): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove, attributes = attributes))

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * `script` element to send.
   *
   * When `autoRemove` is true, the element gets the attribute
   * `data-effect="el.remove()"`. `attributes` are added to the element as
   * attributes, and `eventId` becomes the `id` of the server-sent event.
   */
  def executeScript(
    script0: Dom.Element.Script,
    autoRemove: Boolean,
    attributes: Seq[(String, String)],
    eventId: Option[String],
  ): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove, attributes = attributes, eventId = eventId))

  /**
   * Returns an event that runs a script in the browser. The event is an element
   * patch that appends a `script` element to the `body`. `script0` is the
   * `script` element to send.
   *
   * When `autoRemove` is true, the element gets the attribute
   * `data-effect="el.remove()"`. `attributes` are added to the element as
   * attributes, `eventId` becomes the `id` of the server-sent event, and
   * `retryDuration` is sent as its `retry` field when it is not 1000
   * milliseconds.
   */
  def executeScript(
    script0: Dom.Element.Script,
    autoRemove: Boolean,
    attributes: Seq[(String, String)],
    eventId: Option[String],
    retryDuration: Duration,
  ): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove, attributes, eventId, retryDuration))

  /**
   * Returns an event that dispatches a browser `CustomEvent` named `eventName`.
   * The event is sent as a script, like `executeScript`. The `detail` of the
   * `CustomEvent` is the JSON encoding of `payload`, which is encoded with its
   * `Schema`. The event is dispatched on the `document`, bubbles, and is
   * neither cancelable nor composed.
   */
  def dispatchEvent[T <: Product: Schema](eventName: String, payload: T): ExecuteScript =
    dispatchEvent(eventName, payload, DispatchEventOptions.default)

  /**
   * Returns an event that dispatches a browser `CustomEvent` named `eventName`.
   * The event is sent as a script, like `executeScript`. The `detail` of the
   * `CustomEvent` is the JSON encoding of `payload`, which is encoded with its
   * `Schema`. `options` sets `bubbles`, `cancelable` and `composed`, the
   * element to dispatch on (`source`, the `document` when `None`) and the
   * script settings. When `source` is defined but no element matches it,
   * nothing is dispatched.
   */
  def dispatchEvent[T <: Product: Schema](
    eventName: String,
    payload: T,
    options: DispatchEventOptions,
  ): ExecuteScript = {
    val escapedName  = eventName.replace("\\", "\\\\").replace("'", "\\'")
    val jsonPayload  = zio.schema.codec.JsonCodec.jsonCodec(Schema[T]).encodeJson(payload, None).toString
    val eventOptions =
      s"detail:$jsonPayload,bubbles:${options.bubbles},cancelable:${options.cancelable},composed:${options.composed}"
    val jsCode       = options.source match {
      case None           =>
        s"document.dispatchEvent(new CustomEvent('$escapedName',{$eventOptions}))"
      case Some(selector) =>
        val sel = selector.render.replace("\\", "\\\\").replace("'", "\\'")
        s"(function(){var el=document.querySelector('$sel');if(el)el.dispatchEvent(new CustomEvent('$escapedName',{$eventOptions}))})()"
    }
    executeScript(
      jsCode,
      ExecuteScriptOptions(
        autoRemove = options.autoRemove,
        eventId = options.eventId,
        retryDuration = options.retryDuration,
      ),
    )
  }

  /**
   * Returns an event that dispatches a browser `CustomEvent` named `eventName`.
   * The event is sent as a script, like `executeScript`. The `detail` of the
   * `CustomEvent` is the JSON encoding of `payload`, which is encoded with its
   * `Schema`. `source` is the element to dispatch on; when it is `None` the
   * event is dispatched on the `document`, and when no element matches it
   * nothing is dispatched.
   */
  def dispatchEvent[T <: Product: Schema](eventName: String, payload: T, source: Option[CssSelector]): ExecuteScript =
    dispatchEvent(eventName, payload, DispatchEventOptions(source = source))

  /**
   * Returns an event that dispatches a browser `CustomEvent` named `eventName`.
   * The event is sent as a script, like `executeScript`. `payload` is a
   * JavaScript expression that becomes the `detail` of the `CustomEvent`. The
   * event is dispatched on the `document`, bubbles, and is neither cancelable
   * nor composed.
   */
  def dispatchEvent(eventName: String, payload: Js): ExecuteScript =
    dispatchEvent(eventName, payload, DispatchEventOptions.default)

  /**
   * Returns an event that dispatches a browser `CustomEvent` named `eventName`.
   * The event is sent as a script, like `executeScript`. `payload` is a
   * JavaScript expression that becomes the `detail` of the `CustomEvent`.
   * `options` sets `bubbles`, `cancelable` and `composed`, the element to
   * dispatch on (`source`, the `document` when `None`) and the script settings.
   * When `source` is defined but no element matches it, nothing is dispatched.
   */
  def dispatchEvent(eventName: String, payload: Js, options: DispatchEventOptions): ExecuteScript = {
    val escapedName  = eventName.replace("\\", "\\\\").replace("'", "\\'")
    val eventOptions =
      s"detail:${payload.value},bubbles:${options.bubbles},cancelable:${options.cancelable},composed:${options.composed}"
    val jsCode       = options.source match {
      case None           =>
        s"document.dispatchEvent(new CustomEvent('$escapedName',{$eventOptions}))"
      case Some(selector) =>
        val sel = selector.render.replace("\\", "\\\\").replace("'", "\\'")
        s"(function(){var el=document.querySelector('$sel');if(el)el.dispatchEvent(new CustomEvent('$escapedName',{$eventOptions}))})()"
    }
    executeScript(
      jsCode,
      ExecuteScriptOptions(
        autoRemove = options.autoRemove,
        eventId = options.eventId,
        retryDuration = options.retryDuration,
      ),
    )
  }

  /**
   * Returns an event that patches the page with the given HTML. `elements` is
   * sent as raw HTML. No selector is sent, the mode is
   * `ElementPatchMode.Outer`, and the other options keep their defaults.
   */
  def patchElements(elements: String): PatchElements =
    patchElements(elements, PatchElementOptions.default)

  /**
   * Returns an event that patches the page with the given HTML. `elements` is
   * sent as raw HTML. The selector, mode, view transition, namespace, event id
   * and retry duration come from `options`.
   *
   * Throws a `java.lang.AssertionError` when `mode` is
   * `ElementPatchMode.Remove` and `selector` is `None` or the elements are not
   * empty. A `String` is not treated as empty, not even `""`, so use the `Dom`
   * overloads with `Dom.empty` to remove an element.
   */
  def patchElements(elements: String, options: PatchElementOptions): PatchElements =
    patchElements(Dom.raw(elements), options)

  /**
   * Returns an event that patches the page with the given HTML. `elements` is
   * sent as raw HTML. `selector` names the element to patch; no `selector` line
   * is sent when it is `None`. The mode is `ElementPatchMode.Outer`.
   *
   * Throws a `java.lang.AssertionError` when `mode` is
   * `ElementPatchMode.Remove` and `selector` is `None` or the elements are not
   * empty. A `String` is not treated as empty, not even `""`, so use the `Dom`
   * overloads with `Dom.empty` to remove an element.
   */
  def patchElements(elements: String, selector: Option[CssSelector]): PatchElements =
    patchElements(elements, PatchElementOptions(selector = selector))

  /**
   * Returns an event that patches the page with the given HTML. `elements` is
   * sent as raw HTML. `selector` names the element to patch, and `mode` says
   * how the HTML is merged into it.
   *
   * Throws a `java.lang.AssertionError` when `mode` is
   * `ElementPatchMode.Remove` and `selector` is `None` or the elements are not
   * empty. A `String` is not treated as empty, not even `""`, so use the `Dom`
   * overloads with `Dom.empty` to remove an element.
   */
  def patchElements(elements: String, selector: Option[CssSelector], mode: ElementPatchMode): PatchElements =
    patchElements(elements, PatchElementOptions(selector = selector, mode = mode))

  /**
   * Returns an event that patches the page with the given HTML. `elements` is
   * sent as raw HTML. `selector` names the element to patch, `mode` says how
   * the HTML is merged into it, and `useViewTransition` asks the browser to run
   * the update as a view transition.
   *
   * Throws a `java.lang.AssertionError` when `mode` is
   * `ElementPatchMode.Remove` and `selector` is `None` or the elements are not
   * empty. A `String` is not treated as empty, not even `""`, so use the `Dom`
   * overloads with `Dom.empty` to remove an element.
   */
  def patchElements(
    elements: String,
    selector: Option[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
  ): PatchElements =
    patchElements(
      elements,
      PatchElementOptions(selector = selector, mode = mode, useViewTransition = useViewTransition),
    )

  /**
   * Returns an event that patches the page with the given HTML. `elements` is
   * sent as raw HTML. `selector`, `mode` and `useViewTransition` are as in the
   * overload without `eventId`; `eventId` becomes the `id` of the server-sent
   * event.
   *
   * Throws a `java.lang.AssertionError` when `mode` is
   * `ElementPatchMode.Remove` and `selector` is `None` or the elements are not
   * empty. A `String` is not treated as empty, not even `""`, so use the `Dom`
   * overloads with `Dom.empty` to remove an element.
   */
  def patchElements(
    elements: String,
    selector: Option[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
    eventId: Option[String],
  ): PatchElements =
    patchElements(
      elements,
      PatchElementOptions(selector = selector, mode = mode, useViewTransition = useViewTransition, eventId = eventId),
    )

  /**
   * Returns an event that patches the page with the given HTML. `elements` is
   * sent as raw HTML. `selector`, `mode`, `useViewTransition` and `eventId` are
   * as in the overload without `retryDuration`; `retryDuration` is sent as the
   * `retry` field when it is not 1000 milliseconds.
   *
   * Throws a `java.lang.AssertionError` when `mode` is
   * `ElementPatchMode.Remove` and `selector` is `None` or the elements are not
   * empty. A `String` is not treated as empty, not even `""`, so use the `Dom`
   * overloads with `Dom.empty` to remove an element.
   */
  def patchElements(
    elements: String,
    selector: Option[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
    eventId: Option[String],
    retryDuration: Duration,
  ): PatchElements =
    patchElements(
      elements,
      PatchElementOptions(
        selector = selector,
        mode = mode,
        useViewTransition = useViewTransition,
        eventId = eventId,
        retryDuration = retryDuration,
      ),
    )

  /**
   * Returns an event that patches the page with the given HTML. `element` is
   * rendered to minified HTML. No selector is sent, the mode is
   * `ElementPatchMode.Outer`, and the other options keep their defaults.
   */
  def patchElements(element: Dom): PatchElements =
    patchElements(element, PatchElementOptions.default)

  /**
   * Returns an event that patches the page with the given HTML. `element` is
   * rendered to minified HTML. The selector, mode, view transition, namespace,
   * event id and retry duration come from `options`.
   *
   * Throws a `java.lang.AssertionError` when `mode` is
   * `ElementPatchMode.Remove` and `selector` is `None` or `element` is not
   * empty.
   */
  def patchElements(element: Dom, options: PatchElementOptions): PatchElements =
    PatchElements(
      element,
      options.selector,
      options.mode,
      options.useViewTransition,
      options.namespace,
      options.eventId,
      options.retryDuration,
    )

  /**
   * Returns an event that patches the page with the given HTML. `element` is
   * rendered to minified HTML. `selector` names the element to patch; no
   * `selector` line is sent when it is `None`. The mode is
   * `ElementPatchMode.Outer`.
   *
   * Throws a `java.lang.AssertionError` when `mode` is
   * `ElementPatchMode.Remove` and `selector` is `None` or `element` is not
   * empty.
   */
  def patchElements(element: Dom, selector: Option[CssSelector]): PatchElements =
    patchElements(element, PatchElementOptions(selector = selector))

  /**
   * Returns an event that patches the page with the given HTML. `element` is
   * rendered to minified HTML. `selector` names the element to patch, and
   * `mode` says how the HTML is merged into it.
   *
   * Throws a `java.lang.AssertionError` when `mode` is
   * `ElementPatchMode.Remove` and `selector` is `None` or `element` is not
   * empty.
   */
  def patchElements(element: Dom, selector: Option[CssSelector], mode: ElementPatchMode): PatchElements =
    patchElements(element, PatchElementOptions(selector = selector, mode = mode))

  /**
   * Returns an event that patches the page with the given HTML. `element` is
   * rendered to minified HTML. `selector` names the element to patch, `mode`
   * says how the HTML is merged into it, and `useViewTransition` asks the
   * browser to run the update as a view transition.
   *
   * Throws a `java.lang.AssertionError` when `mode` is
   * `ElementPatchMode.Remove` and `selector` is `None` or `element` is not
   * empty.
   */
  def patchElements(
    element: Dom,
    selector: Option[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
  ): PatchElements =
    patchElements(element, PatchElementOptions(selector = selector, mode = mode, useViewTransition = useViewTransition))

  /**
   * Returns an event that patches the page with the given HTML. `element` is
   * rendered to minified HTML. `selector`, `mode` and `useViewTransition` are
   * as in the overload without `eventId`; `eventId` becomes the `id` of the
   * server-sent event.
   *
   * Throws a `java.lang.AssertionError` when `mode` is
   * `ElementPatchMode.Remove` and `selector` is `None` or `element` is not
   * empty.
   */
  def patchElements(
    element: Dom,
    selector: Option[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
    eventId: Option[String],
  ): PatchElements =
    patchElements(
      element,
      PatchElementOptions(selector = selector, mode = mode, useViewTransition = useViewTransition, eventId = eventId),
    )

  /**
   * Returns an event that patches the page with the given HTML. `element` is
   * rendered to minified HTML. The arguments are as in the overload without
   * `retryDuration`; `retryDuration` is sent as the `retry` field when it is
   * not 1000 milliseconds. No namespace is sent.
   *
   * Throws a `java.lang.AssertionError` when `mode` is
   * `ElementPatchMode.Remove` and `selector` is `None` or `element` is not
   * empty.
   */
  def patchElements(
    element: Dom,
    selector: Option[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
    eventId: Option[String],
    retryDuration: Duration,
  ): PatchElements =
    PatchElements(element, selector, mode, useViewTransition, None, eventId, retryDuration)

  /**
   * Returns an event that patches the page with the given HTML. `element` is
   * rendered to minified HTML. The arguments are as in the overload without
   * `namespace`; `namespace` is sent as a `namespace` line when it is defined.
   *
   * Throws a `java.lang.AssertionError` when `mode` is
   * `ElementPatchMode.Remove` and `selector` is `None` or `element` is not
   * empty.
   */
  def patchElements(
    element: Dom,
    selector: Option[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
    namespace: Option[String],
    eventId: Option[String],
    retryDuration: Duration,
  ): PatchElements =
    PatchElements(element, selector, mode, useViewTransition, namespace, eventId, retryDuration)

  /**
   * Returns an event that patches the signals in the browser. The pair is a
   * signal name and its value; the value is sent as a JSON string, so
   * `("count", "1")` is sent as `{"count":"1"}`.
   */
  def patchSignals(signal: (String, String)): PatchSignals =
    patchSignals(Seq(signal), PatchSignalOptions.default)

  /**
   * Returns an event that patches the signals in the browser. The pair is a
   * signal name and its value; the value is sent as a JSON string, so
   * `("count", "1")` is sent as `{"count":"1"}`. `options` sets
   * `onlyIfMissing`, the event id and the retry duration.
   */
  def patchSignals(signal: (String, String), options: PatchSignalOptions): PatchSignals =
    patchSignals(Seq(signal), options)

  /**
   * Returns an event that patches the signals in the browser. The pair is a
   * signal name and its value; the value is sent as a JSON string, so
   * `("count", "1")` is sent as `{"count":"1"}`. When `onlyIfMissing` is true,
   * an `onlyIfMissing true` line is sent.
   */
  def patchSignals(signal: (String, String), onlyIfMissing: Boolean): PatchSignals =
    patchSignals(signal, PatchSignalOptions(onlyIfMissing = onlyIfMissing))

  /**
   * Returns an event that patches the signals in the browser. The pair is a
   * signal name and its value; the value is sent as a JSON string, so
   * `("count", "1")` is sent as `{"count":"1"}`. When `onlyIfMissing` is true,
   * an `onlyIfMissing true` line is sent. `eventId` becomes the `id` of the
   * server-sent event.
   */
  def patchSignals(signal: (String, String), onlyIfMissing: Boolean, eventId: Option[String]): PatchSignals =
    patchSignals(signal, PatchSignalOptions(onlyIfMissing = onlyIfMissing, eventId = eventId))

  /**
   * Returns an event that patches the signals in the browser. The pair is a
   * signal name and its value; the value is sent as a JSON string, so
   * `("count", "1")` is sent as `{"count":"1"}`. When `onlyIfMissing` is true,
   * an `onlyIfMissing true` line is sent. `eventId` becomes the `id` of the
   * server-sent event, and `retryDuration` is sent as the `retry` field when it
   * is not 1000 milliseconds.
   */
  def patchSignals(
    signal: (String, String),
    onlyIfMissing: Boolean,
    eventId: Option[String],
    retryDuration: Duration,
  ): PatchSignals =
    patchSignals(Json.Obj((signal._1, Json.Str(signal._2))), PatchSignalOptions(onlyIfMissing, eventId, retryDuration))

  /**
   * Returns an event that patches the signals in the browser. `signals` is sent
   * as its JSON text.
   */
  def patchSignals(signals: Json.Obj): PatchSignals =
    patchSignals(signals, PatchSignalOptions.default)

  /**
   * Returns an event that patches the signals in the browser. `obj` is sent as
   * its JSON encoding, using its `Schema`.
   */
  def patchSignals[T <: Product: Schema](obj: T): PatchSignals =
    patchSignals(obj, PatchSignalOptions.default)

  /**
   * Returns an event that patches the signals in the browser. Each pair becomes
   * a field of one JSON object, and every value is sent as a JSON string.
   */
  def patchSignals(signals: Iterable[(String, String)]): PatchSignals =
    patchSignals(signals, PatchSignalOptions.default)

  /**
   * Returns an event that patches the signals in the browser. `signals` is sent
   * as its JSON text. `options` sets `onlyIfMissing`, the event id and the
   * retry duration.
   */
  def patchSignals(signals: Json.Obj, options: PatchSignalOptions): PatchSignals =
    PatchSignals(
      signals = signals.toJson,
      onlyIfMissing = options.onlyIfMissing,
      eventId = options.eventId,
      retryDuration = options.retryDuration,
    )

  /**
   * Returns an event that patches the signals in the browser. `obj` is sent as
   * its JSON encoding, using its `Schema`. `options` sets `onlyIfMissing`, the
   * event id and the retry duration.
   */
  def patchSignals[T <: Product: Schema](obj: T, options: PatchSignalOptions) =
    PatchSignals(
      zio.schema.codec.JsonCodec.jsonCodec(Schema[T]).encodeJson(obj, None).toString,
      onlyIfMissing = options.onlyIfMissing,
      eventId = options.eventId,
      retryDuration = options.retryDuration,
    )

  /**
   * Returns an event that patches the signals in the browser. Each pair becomes
   * a field of one JSON object, and every value is sent as a JSON string.
   * `options` sets `onlyIfMissing`, the event id and the retry duration.
   */
  def patchSignals(signals: Iterable[(String, String)], options: PatchSignalOptions): PatchSignals = {
    val jsonObj = Json.Obj(Chunk.fromIterable(signals.map { case (k, v) => k -> Json.Str(v) }))
    PatchSignals(
      signals = jsonObj.toJson,
      onlyIfMissing = options.onlyIfMissing,
      eventId = options.eventId,
      retryDuration = options.retryDuration,
    )
  }

  /**
   * Returns an event that patches the signals in the browser. `signals` is sent
   * as its JSON text. When `onlyIfMissing` is true, an `onlyIfMissing true`
   * line is sent.
   */
  def patchSignals(signals: Json.Obj, onlyIfMissing: Boolean): PatchSignals =
    patchSignals(signals, PatchSignalOptions(onlyIfMissing = onlyIfMissing))

  /**
   * Returns an event that patches the signals in the browser. `obj` is sent as
   * its JSON encoding, using its `Schema`. When `onlyIfMissing` is true, an
   * `onlyIfMissing true` line is sent.
   */
  def patchSignals[T <: Product: Schema](obj: T, onlyIfMissing: Boolean): PatchSignals =
    patchSignals(obj, PatchSignalOptions(onlyIfMissing = onlyIfMissing))

  /**
   * Returns an event that patches the signals in the browser. Each pair becomes
   * a field of one JSON object, and every value is sent as a JSON string. When
   * `onlyIfMissing` is true, an `onlyIfMissing true` line is sent.
   */
  def patchSignals(signals: Iterable[(String, String)], onlyIfMissing: Boolean): PatchSignals =
    patchSignals(signals, PatchSignalOptions(onlyIfMissing = onlyIfMissing))

  /**
   * Returns an event that patches the signals in the browser. `signals` is sent
   * as its JSON text. When `onlyIfMissing` is true, an `onlyIfMissing true`
   * line is sent. `eventId` becomes the `id` of the server-sent event.
   */
  def patchSignals(signals: Json.Obj, onlyIfMissing: Boolean, eventId: Option[String]): PatchSignals =
    patchSignals(signals, PatchSignalOptions(onlyIfMissing = onlyIfMissing, eventId = eventId))

  /**
   * Returns an event that patches the signals in the browser. `obj` is sent as
   * its JSON encoding, using its `Schema`. When `onlyIfMissing` is true, an
   * `onlyIfMissing true` line is sent. `eventId` becomes the `id` of the
   * server-sent event.
   */
  def patchSignals[T <: Product: Schema](obj: T, onlyIfMissing: Boolean, eventId: Option[String]): PatchSignals =
    patchSignals(obj, PatchSignalOptions(onlyIfMissing = onlyIfMissing, eventId = eventId))

  /**
   * Returns an event that patches the signals in the browser. Each pair becomes
   * a field of one JSON object, and every value is sent as a JSON string. When
   * `onlyIfMissing` is true, an `onlyIfMissing true` line is sent. `eventId`
   * becomes the `id` of the server-sent event.
   */
  def patchSignals(signals: Iterable[(String, String)], onlyIfMissing: Boolean, eventId: Option[String]): PatchSignals =
    patchSignals(signals, PatchSignalOptions(onlyIfMissing = onlyIfMissing, eventId = eventId))

  /**
   * Returns an event that patches the signals in the browser. `signals` is sent
   * as its JSON text. When `onlyIfMissing` is true, an `onlyIfMissing true`
   * line is sent. `eventId` becomes the `id` of the server-sent event, and
   * `retryDuration` is sent as the `retry` field when it is not 1000
   * milliseconds.
   */
  def patchSignals(
    signals: Json.Obj,
    onlyIfMissing: Boolean,
    eventId: Option[String],
    retryDuration: Duration,
  ): PatchSignals =
    patchSignals(signals, PatchSignalOptions(onlyIfMissing, eventId, retryDuration))

  /**
   * Returns an event that patches the signals in the browser. `obj` is sent as
   * its JSON encoding, using its `Schema`. When `onlyIfMissing` is true, an
   * `onlyIfMissing true` line is sent. `eventId` becomes the `id` of the
   * server-sent event, and `retryDuration` is sent as the `retry` field when it
   * is not 1000 milliseconds.
   */
  def patchSignals[T <: Product: Schema](
    obj: T,
    onlyIfMissing: Boolean,
    eventId: Option[String],
    retryDuration: Duration,
  ): PatchSignals =
    patchSignals(obj, PatchSignalOptions(onlyIfMissing, eventId, retryDuration))

  /**
   * Returns an event that patches the signals in the browser. Each pair becomes
   * a field of one JSON object, and every value is sent as a JSON string. When
   * `onlyIfMissing` is true, an `onlyIfMissing true` line is sent. `eventId`
   * becomes the `id` of the server-sent event, and `retryDuration` is sent as
   * the `retry` field when it is not 1000 milliseconds.
   */
  def patchSignals(
    signals: Iterable[(String, String)],
    onlyIfMissing: Boolean,
    eventId: Option[String],
    retryDuration: Duration,
  ): PatchSignals =
    patchSignals(signals, PatchSignalOptions(onlyIfMissing, eventId, retryDuration))

}
