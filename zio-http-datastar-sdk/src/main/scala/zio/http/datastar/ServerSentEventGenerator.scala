package zio.http.datastar

import zio._

import zio.http.ServerSentEvent
import zio.http.template2._

sealed trait EventType extends Product with Serializable

object EventType {
  case object PatchElements extends EventType {
    override def toString: String = "datastar-patch-elements"
  }
  case object PatchSignals  extends EventType {
    override def toString: String = "datastar-patch-signals"
  }
}

sealed trait ElementPatchMode extends Product with Serializable {
  override def toString: String = this.productPrefix.toLowerCase
}

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
}

final case class PatchElementOptions(
  selector: Option[CssSelector] = None,
  mode: ElementPatchMode = ElementPatchMode.Outer,
  useViewTransition: Boolean = false,
  eventId: Option[String] = None,
  retryDuration: Duration = 1000.millis,
)

object PatchElementOptions {
  val default: PatchElementOptions = PatchElementOptions()
}

final case class PatchSignalOptions(
  onlyIfMissing: Boolean = false,
  eventId: Option[String] = None,
  retryDuration: Duration = 1000.millis,
)

object PatchSignalOptions {
  val default: PatchSignalOptions = PatchSignalOptions()
}

final case class ExecuteScriptOptions(
  autoRemove: Boolean = true,
  attributes: Seq[(String, String)] = Seq.empty,
  eventId: Option[String] = None,
  retryDuration: Duration = 1000.millis,
)

object ExecuteScriptOptions {
  val default: ExecuteScriptOptions = ExecuteScriptOptions()
}

object ServerSentEventGenerator {

  def executeScript(
    script0: String,
    options: ExecuteScriptOptions = ExecuteScriptOptions(),
  ): ZIO[Datastar, Nothing, Unit] = {
    val removeAttr = if (options.autoRemove) Dom.attr("data-effect", "el.remove") else Dom.empty
    patchElements(
      script(Dom.text(script0), removeAttr)(options.attributes.map(a => Dom.attr(a._1, a._2)): _*),
      PatchElementOptions(eventId = options.eventId, retryDuration = options.retryDuration),
    )
  }

  def patchElements(
    elements: String,
    options: PatchElementOptions,
  ): ZIO[Datastar, Nothing, Unit] =
    patchElements(elements.split('\n').map(Dom.raw).toList, options)

  def patchElements(
    elements: String,
  ): ZIO[Datastar, Nothing, Unit] =
    patchElements(elements, PatchElementOptions.default)

  def patchElements(
    element: Dom,
    options: PatchElementOptions,
  ): ZIO[Datastar, Nothing, Unit] = patchElements(Iterable(element), options)

  def patchElements(
    element: Dom,
  ): ZIO[Datastar, Nothing, Unit] =
    patchElements(Iterable(element), PatchElementOptions.default)

  def patchElements(
    elements: Iterable[Dom],
  ): ZIO[Datastar, Nothing, Unit] =
    patchElements(elements, PatchElementOptions.default)

  def patchElements(
    elements: Iterable[Dom],
    options: PatchElementOptions,
  ): ZIO[Datastar, Nothing, Unit] = {

    val sb = new StringBuilder

    options.selector.foreach(s => sb.append("selector ").append(s.render).append("\n"))

    if (options.mode != ElementPatchMode.Outer) {
      sb.append("mode ").append(options.mode.toString).append("\n")
    }

    if (options.useViewTransition) {
      sb.append("useViewTransition true\n")
    }

    elements.foreach(d => {
      sb.append("elements ").append(d.render).append("\n")
    })

    val retry = if (options.retryDuration != 1000.millis) Some(options.retryDuration) else None
    send(EventType.PatchElements, sb.toString(), options.eventId, retry)
  }

  def patchSignals(
    signal: String,
    options: PatchSignalOptions,
  ): ZIO[Datastar, Nothing, Unit] =
    patchSignals(Iterable(signal), options)

  def patchSignals(
    signal: String,
  ): ZIO[Datastar, Nothing, Unit] =
    patchSignals(Iterable(signal), PatchSignalOptions.default)

  def patchSignals(
    signals: Iterable[String],
  ): ZIO[Datastar, Nothing, Unit] =
    patchSignals(signals, PatchSignalOptions.default)

  def patchSignals(
    signals: Iterable[String],
    options: PatchSignalOptions,
  ): ZIO[Datastar, Nothing, Unit] = {

    val sb = new StringBuilder

    if (options.onlyIfMissing) {
      sb.append("onlyIfMissing true\n")
    }

    signals.foreach(s => {
      sb.append("signals ").append(s).append("\n")
    })

    val retry = if (options.retryDuration != 1000.millis) Some(options.retryDuration) else None
    send(EventType.PatchSignals, sb.toString(), options.eventId, retry)
  }

  private def send(
    eventType: EventType,
    dataLines: String,
    eventId: Option[String] = None,
    retry: Option[Duration] = Some(1000.millis),
  ): ZIO[Datastar, Nothing, Unit] =
    ZIO.serviceWithZIO[Datastar] { `d*` =>
      val retry0 = if (retry.contains(1000.millis)) None else retry
      `d*`.queue
        .offer(ServerSentEvent(data = dataLines, eventType = Some(eventType.toString), id = eventId, retry = retry0))
        .unit
    }

}
