package zhttp.http

import zio.ZIO

trait HttpConstructors {

  /**
   * Creates an Http that always returns the same response and never fails.
   */
  def succeed[B](b: B): HttpChannel[Any, Nothing, Any, B] = HttpChannel.Succeed(b)

  /**
   * Creates an Http app from a function that returns a ZIO
   */
  def fromEffectFunction[A]: HttpChannel.MakeFromEffectFunction[A] = HttpChannel.MakeFromEffectFunction(())

  /**
   * Converts a ZIO to an Http type
   */
  def fromEffect[R, E, B](effect: ZIO[R, E, B]): HttpChannel[R, E, Any, B] = HttpChannel.fromEffectFunction(_ => effect)

  /**
   * Creates an Http that always fails
   */
  def fail[E](e: E): HttpChannel[Any, E, Any, Nothing] = HttpChannel.Fail(e)

  /**
   * Creates a pass thru Http instances
   */
  def identity[A]: HttpChannel[Any, Nothing, A, A] = HttpChannel.Identity

  /**
   * Creates an HTTP app which accepts a request and produces response.
   */
  def collect[A]: HttpChannel.MakeCollect[A] = HttpChannel.MakeCollect(())

  /**
   * Creates an HTTP app which accepts a request and produces response effectfully.
   */
  def collectM[A]: HttpChannel.MakeCollectM[A] = HttpChannel.MakeCollectM(())

  /**
   * Creates an HTTP app which for any request produces a response.
   */
  def succeedM[R, E, B](zio: ZIO[R, E, B]): HttpChannel[R, E, Any, B] = HttpChannel.fromEffectFunction(_ => zio)

  /**
   * Flattens an Http app of an Http app
   */
  def flatten[R, E, A, B](http: HttpChannel[R, E, A, HttpChannel[R, E, A, B]]): HttpChannel[R, E, A, B] =
    http.flatten

  /**
   * Flattens an Http app of an effectful response
   */
  def flattenM[R, E, A, B](http: HttpChannel[R, E, A, ZIO[R, E, B]]): HttpChannel[R, E, A, B] =
    http.flatMap(HttpChannel.fromEffect)
}
