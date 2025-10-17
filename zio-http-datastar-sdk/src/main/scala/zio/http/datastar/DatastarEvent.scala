package zio.http.datastar

import zio._

import zio.http.ServerSentEvent
import zio.http.datastar.ServerSentEventGenerator.DefaultRetryDelay
import zio.http.template2.Dom.AttributeValue
import zio.http.template2._

sealed trait DatastarEvent {
  def eventType: EventType

  def toServerSentEvent: ServerSentEvent[String]
}

object DatastarEvent {

  final case class PatchElements(
    elements: Dom,
    selector: Option[CssSelector] = None,
    mode: ElementPatchMode = ElementPatchMode.Outer,
    useViewTransition: Boolean = false,
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

      sb.append("elements ").append(elements.renderMinified).append('\n')

      val retry = if (retryDuration != DefaultRetryDelay) Some(retryDuration) else None
      ServerSentEvent(sb.toString(), Some(eventType.render), eventId, retry)
    }
  }

  final case class PatchSignals(
    signals: Iterable[String],
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

      signals.foreach(s => sb.append("signals ").append(s).append('\n'))

      val retry = if (retryDuration != DefaultRetryDelay) Some(retryDuration) else None
      ServerSentEvent(sb.toString(), Some(eventType.render), eventId, retry)
    }
  }

  final case class ExecuteScript(
    script: Dom.Element.Script,
    eventId: Option[String] = None,
    retryDuration: Duration = 1000.millis,
  ) extends DatastarEvent {
    override val eventType: EventType = EventType.PatchElements

    override def toServerSentEvent: ServerSentEvent[String] = {
      val sb = new StringBuilder()
      sb.append("selector ").append(body.render).append('\n')
      sb.append("mode append\n")

      val rendered = script.render
      if (rendered.contains('\n'))
        rendered.split('\n').foreach(line => sb.append("elements ").append(line).append('\n'))
      else
        sb.append("elements ").append(rendered).append('\n')

      val retry = if (retryDuration != DefaultRetryDelay) Some(retryDuration) else None
      ServerSentEvent(sb.toString(), Some(eventType.render), eventId, retry)
    }
  }

  def executeScript(script0: Js): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions.default)

  def executeScript(script0: Js, options: ExecuteScriptOptions): ExecuteScript =
    executeScript(Dom.script(script0), options)

  def executeScript(script0: Js, autoRemove: Boolean): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove))

  def executeScript(script0: Js, autoRemove: Boolean, attributes: Seq[(String, String)]): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove, attributes = attributes))

  def executeScript(
    script0: Js,
    autoRemove: Boolean,
    attributes: Seq[(String, String)],
    eventId: Option[String],
  ): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove, attributes = attributes, eventId = eventId))

  def executeScript(
    script0: Js,
    autoRemove: Boolean,
    attributes: Seq[(String, String)],
    eventId: Option[String],
    retryDuration: Duration,
  ): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove, attributes, eventId, retryDuration))

  def executeScript(script0: String): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions.default)

  def executeScript(script0: String, options: ExecuteScriptOptions): ExecuteScript =
    executeScript(Dom.script(script0), options)

  def executeScript(script0: String, autoRemove: Boolean): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove))

  def executeScript(script0: String, autoRemove: Boolean, attributes: Seq[(String, String)]): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove, attributes = attributes))

  def executeScript(
    script0: String,
    autoRemove: Boolean,
    attributes: Seq[(String, String)],
    eventId: Option[String],
  ): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove, attributes = attributes, eventId = eventId))

  def executeScript(
    script0: String,
    autoRemove: Boolean,
    attributes: Seq[(String, String)],
    eventId: Option[String],
    retryDuration: Duration,
  ): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove, attributes, eventId, retryDuration))

  def executeScript(script0: Dom.Element.Script): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions.default)

  def executeScript(
    script0: Dom.Element.Script,
    options: ExecuteScriptOptions,
  ): ExecuteScript = {
    val removeAttr      =
      if (options.autoRemove) Dom.attr("data-effect", AttributeValue.StringValue("el.remove")) else Dom.empty
    val scriptWithAttrs =
      script0(removeAttr)(options.attributes.map(a => Dom.attr(a._1, AttributeValue.StringValue(a._2))))

    ExecuteScript(
      script = scriptWithAttrs,
      eventId = options.eventId,
      retryDuration = options.retryDuration,
    )
  }

  def executeScript(script0: Dom.Element.Script, autoRemove: Boolean): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove))

  def executeScript(
    script0: Dom.Element.Script,
    autoRemove: Boolean,
    attributes: Seq[(String, String)],
  ): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove, attributes = attributes))

  def executeScript(
    script0: Dom.Element.Script,
    autoRemove: Boolean,
    attributes: Seq[(String, String)],
    eventId: Option[String],
  ): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove = autoRemove, attributes = attributes, eventId = eventId))

  def executeScript(
    script0: Dom.Element.Script,
    autoRemove: Boolean,
    attributes: Seq[(String, String)],
    eventId: Option[String],
    retryDuration: Duration,
  ): ExecuteScript =
    executeScript(script0, ExecuteScriptOptions(autoRemove, attributes, eventId, retryDuration))

  def patchElements(elements: String): PatchElements =
    patchElements(elements, PatchElementOptions.default)

  def patchElements(elements: String, options: PatchElementOptions): PatchElements =
    patchElements(Dom.raw(elements), options)

  def patchElements(elements: String, selector: Option[CssSelector]): PatchElements =
    patchElements(elements, PatchElementOptions(selector = selector))

  def patchElements(elements: String, selector: Option[CssSelector], mode: ElementPatchMode): PatchElements =
    patchElements(elements, PatchElementOptions(selector = selector, mode = mode))

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

  def patchElements(
    elements: String,
    selector: Option[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
    eventId: Option[String],
  ): PatchElements =
    patchElements(elements, PatchElementOptions(selector, mode, useViewTransition, eventId))

  def patchElements(
    elements: String,
    selector: Option[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
    eventId: Option[String],
    retryDuration: Duration,
  ): PatchElements =
    patchElements(elements, PatchElementOptions(selector, mode, useViewTransition, eventId, retryDuration))

  def patchElements(element: Dom): PatchElements =
    patchElements(element, PatchElementOptions.default)

  def patchElements(element: Dom, options: PatchElementOptions): PatchElements =
    patchElements(
      element,
      options.selector,
      options.mode,
      options.useViewTransition,
      options.eventId,
      options.retryDuration,
    )

  def patchElements(element: Dom, selector: Option[CssSelector]): PatchElements =
    patchElements(element, PatchElementOptions(selector = selector))

  def patchElements(element: Dom, selector: Option[CssSelector], mode: ElementPatchMode): PatchElements =
    patchElements(element, PatchElementOptions(selector = selector, mode = mode))

  def patchElements(
    element: Dom,
    selector: Option[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
  ): PatchElements =
    patchElements(element, PatchElementOptions(selector = selector, mode = mode, useViewTransition = useViewTransition))

  def patchElements(
    element: Dom,
    selector: Option[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
    eventId: Option[String],
  ): PatchElements =
    patchElements(element, PatchElementOptions(selector, mode, useViewTransition, eventId))

  def patchElements(
    element: Dom,
    selector: Option[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
    eventId: Option[String],
    retryDuration: Duration,
  ): PatchElements =
    PatchElements(element, selector, mode, useViewTransition, eventId, retryDuration)

  def patchSignals(signal: String): PatchSignals =
    patchSignals(List(signal), PatchSignalOptions.default)

  def patchSignals(signal: String, options: PatchSignalOptions): PatchSignals =
    patchSignals(List(signal), options)

  def patchSignals(signal: String, onlyIfMissing: Boolean): PatchSignals =
    patchSignals(signal, PatchSignalOptions(onlyIfMissing = onlyIfMissing))

  def patchSignals(signal: String, onlyIfMissing: Boolean, eventId: Option[String]): PatchSignals =
    patchSignals(signal, PatchSignalOptions(onlyIfMissing = onlyIfMissing, eventId = eventId))

  def patchSignals(
    signal: String,
    onlyIfMissing: Boolean,
    eventId: Option[String],
    retryDuration: Duration,
  ): PatchSignals =
    patchSignals(signal, PatchSignalOptions(onlyIfMissing, eventId, retryDuration))

  def patchSignals(signals: Iterable[String]): PatchSignals =
    patchSignals(signals, PatchSignalOptions.default)

  def patchSignals(signals: Iterable[String], options: PatchSignalOptions): PatchSignals = {
    PatchSignals(
      signals = signals,
      onlyIfMissing = options.onlyIfMissing,
      eventId = options.eventId,
      retryDuration = options.retryDuration,
    )
  }

  def patchSignals(signals: Iterable[String], onlyIfMissing: Boolean): PatchSignals =
    patchSignals(signals, PatchSignalOptions(onlyIfMissing = onlyIfMissing))

  def patchSignals(signals: Iterable[String], onlyIfMissing: Boolean, eventId: Option[String]): PatchSignals =
    patchSignals(signals, PatchSignalOptions(onlyIfMissing = onlyIfMissing, eventId = eventId))

  def patchSignals(
    signals: Iterable[String],
    onlyIfMissing: Boolean,
    eventId: Option[String],
    retryDuration: Duration,
  ): PatchSignals =
    patchSignals(signals, PatchSignalOptions(onlyIfMissing, eventId, retryDuration))

}
