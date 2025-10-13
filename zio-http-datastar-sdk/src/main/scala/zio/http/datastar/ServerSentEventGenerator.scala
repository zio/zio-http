package zio.http.datastar

import zio._

import zio.schema.Schema

import zio.http.ServerSentEvent
import zio.http.internal.StringBuilderPool
import zio.http.template2.Dom.AttributeValue
import zio.http.template2._

sealed trait EventType extends Product with Serializable {
  def render: String
  final override def toString: String = this.render
}

object EventType {
  case object PatchElements extends EventType {
    override def render: String = "datastar-patch-elements"
  }
  case object PatchSignals  extends EventType {
    override def render: String = "datastar-patch-signals"
  }
}

sealed trait ElementPatchMode extends Product with Serializable {
  final def render: String            = this.productPrefix.toLowerCase
  final override def toString: String = this.render
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

  private[datastar] val DefaultRetryDelay: Duration = 1000.millis

  def executeScript(script0: Js): ZIO[Datastar, Nothing, Unit] =
    executeScript(script(script0), ExecuteScriptOptions.default)

  def executeScript(script0: Js, options: ExecuteScriptOptions): ZIO[Datastar, Nothing, Unit] =
    executeScript(script(script0), options)

  def executeScript(script0: String): ZIO[Datastar, Nothing, Unit] =
    executeScript(script0, ExecuteScriptOptions.default)

  def executeScript(script0: String, options: ExecuteScriptOptions): ZIO[Datastar, Nothing, Unit] =
    executeScript(script(script0), options)

  def executeScript(script0: Dom.Element.Script): ZIO[Datastar, Nothing, Unit] =
    executeScript(script0, ExecuteScriptOptions.default)

  def executeScript(
    script0: Dom.Element.Script,
    options: ExecuteScriptOptions,
  ): ZIO[Datastar, Nothing, Unit] = {
    val removeAttr =
      if (options.autoRemove) Dom.attr("data-effect", AttributeValue.StringValue("el.remove")) else Dom.empty
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

  def patchElements(
    elements: String,
    options: PatchElementOptions,
  ): ZIO[Datastar, Nothing, Unit] =
    patchElements(Dom.raw(elements), options)

  def patchElements(
    elements: String,
  ): ZIO[Datastar, Nothing, Unit] =
    patchElements(elements, PatchElementOptions.default)

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

      sb.append("elements ").append(elements.renderMinified).append('\n')

      val retry = if (options.retryDuration != DefaultRetryDelay) Some(options.retryDuration) else None
      send(EventType.PatchElements, sb.toString(), options.eventId, retry)
    }
  }

  def patchElements(
    element: Dom,
  ): ZIO[Datastar, Nothing, Unit] =
    patchElements(element, PatchElementOptions.default)

  def patchElements(
    elements: Iterable[Dom],
  ): ZIO[Datastar, Nothing, Unit] =
    patchElements(elements, PatchElementOptions.default)

  def patchElements(
    elements: Iterable[Dom],
    options: PatchElementOptions,
  ): ZIO[Datastar, Nothing, Unit] =
    patchElements(Dom.Fragment(elements), options)

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
