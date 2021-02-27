package zio-http.domain.http

import zio.ZIO

trait HttpConstructors {
  def succeed[B](b: B): Http[Any, Nothing, Any, B] = Http.Succeed(b)

  def fromEffectFunction[A]: Http.MakeFromEffectFunction[A] = Http.MakeFromEffectFunction(())

  def fail[E](e: E): Http[Any, E, Any, Nothing] = Http.Fail(e)

  def identity[A]: Http[Any, Nothing, A, A] = Http.Identity

  def collect[A]: Http.MakeCollect[A] = Http.MakeCollect(())

  def collectM[A]: Http.MakeCollectM[A] = Http.MakeCollectM(())

  def succeedM[R, E, B](zio: ZIO[R, E, B]): Http[R, E, Any, B] = Http.fromEffectFunction(_ => zio)
}
