package zio.http.datastar

import zio._
import zio.stream.ZStream

import zio.http.template2.Dom

/** Utilities to map streams into Datastar events. */
object StreamOps {

  /** Map each stream element to a collection of DOM fragments and emit as
    * Datastar PatchElements events using the provided options.
    */
  def eventsFromStream[A, E](
    stream: ZStream[Any, E, A],
  )(mapToDom: A => Iterable[Dom], options: PatchElementOptions = PatchElementOptions.default): ZIO[Datastar, E, Unit] =
    stream.foreach { a =>
      ServerSentEventGenerator.patchElements(mapToDom(a), options)
    }

  /** Convenience overload for mapping to a single Dom. */
  def eventsFromStream[A, E](
    stream: ZStream[Any, E, A],
  )(mapToDom: A => Dom, options: PatchElementOptions): ZIO[Datastar, E, Unit] =
    eventsFromStream(stream)(a => List(mapToDom(a)), options)

  /** Convenience overload for mapping to raw HTML string(s). */
  def eventsFromStreamStrings[A, E](
    stream: ZStream[Any, E, A],
  )(mapToHtml: A => Iterable[String], options: PatchElementOptions = PatchElementOptions.default): ZIO[Datastar, E, Unit] =
    stream.foreach { a =>
      ServerSentEventGenerator.patchElements(mapToHtml(a), options)
    }

  /** Map each stream element to signal updates and emit as Datastar
    * PatchSignals events using the provided options.
    */
  def signalsFromStream[A, E](
    stream: ZStream[Any, E, A],
  )(mapToSignals: A => Iterable[String], options: PatchSignalOptions = PatchSignalOptions.default): ZIO[Datastar, E, Unit] =
    stream.foreach { a =>
      ServerSentEventGenerator.patchSignals(mapToSignals(a), options)
    }

  // Aliases with short names for a nicer call-site
  def events[A, E](
    stream: ZStream[Any, E, A],
  )(mapToDom: A => Iterable[Dom], options: PatchElementOptions = PatchElementOptions.default): ZIO[Datastar, E, Unit] =
    eventsFromStream(stream)(mapToDom, options)

  def signals[A, E](
    stream: ZStream[Any, E, A],
  )(mapToSignals: A => Iterable[String], options: PatchSignalOptions = PatchSignalOptions.default): ZIO[Datastar, E, Unit] =
    signalsFromStream(stream)(mapToSignals, options)
}


