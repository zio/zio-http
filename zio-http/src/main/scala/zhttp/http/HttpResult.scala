package zhttp.http

import zio._

import scala.annotation.{tailrec, unused}

sealed trait HttpResult[-R, +E, +A] { self =>

  def map[B](ab: A => B): HttpResult[R, E, B] = self.flatMap(a => HttpResult.succeed(ab(a)))

  def >>=[R1 <: R, E1 >: E, B](ab: A => HttpResult[R1, E1, B]): HttpResult[R1, E1, B] =
    self.flatMap(ab)

  def *>[R1 <: R, E1 >: E, B](other: HttpResult[R1, E1, B]): HttpResult[R1, E1, B] =
    self.flatMap(_ => other)

  def <>[R1 <: R, E1, A1 >: A](other: HttpResult[R1, E1, A1]): HttpResult[R1, E1, A1] =
    self.flatMapError(_ => other)

  def flatMap[R1 <: R, E1 >: E, B](ab: A => HttpResult[R1, E1, B]): HttpResult[R1, E1, B] =
    HttpResult.flatMap(self, ab)

  def flatten[R1 <: R, E1 >: E, A1](implicit ev: A <:< HttpResult[R1, E1, A1]): HttpResult[R1, E1, A1] =
    self.flatMap(identity(_))

  def defaultWith[R1 <: R, E1 >: E, A1 >: A](other: HttpResult[R1, E1, A1]): HttpResult[R1, E1, A1] =
    self.foldM(HttpResult.fail, HttpResult.succeed, other)

  def <+>[R1 <: R, E1 >: E, A1 >: A](other: HttpResult[R1, E1, A1]): HttpResult[R1, E1, A1] =
    this defaultWith other

  def flatMapError[R1 <: R, E1, A1 >: A](h: E => HttpResult[R1, E1, A1])(implicit
    @unused ev: CanFail[E],
  ): HttpResult[R1, E1, A1] = HttpResult.flatMapError(self, h)

  def foldM[R1 <: R, E1, B1](
    ee: E => HttpResult[R1, E1, B1],
    aa: A => HttpResult[R1, E1, B1],
    dd: HttpResult[R1, E1, B1],
  ): HttpResult[R1, E1, B1] =
    HttpResult.foldM(self, ee, aa, dd)

  /**
   * Provides the `HttpResult` with its required environment, which eliminates its dependency on `R`.
   */
  def provide(r: R)(implicit ev: NeedsEnv[R]): HttpResult[Any, E, A] = HttpResult.Provide(self, r)

  /**
   * Provides some of the environment required to run this `HttpResult`, leaving the remainder `R0`.
   *
   * If your environment has the type `Has[_]`, please see [[zhttp.http.HttpResult.provideSomeLayer]]
   *
   * {{{
   * val res: HttpResult[Console with Logging, Nothing, Unit] = ???
   *
   * res.provideSome[Console](env =>
   *   new Console with Logging {
   *     val console = env.console
   *     val logging = new Logging.Service[Any] {
   *       def log(line: String) = console.putStrLn(line)
   *     }
   *   }
   * )
   * }}}
   */
  def provideSome[R0](f: R0 => R): HttpResult[R0, E, A] =
    HttpResult.effect(ZIO.access[R0](identity)).flatMap(e => { self.provide(f(e)) })

  /**
   * Provides a layer to the `HttpResult`, which translates it to another level.
   */
  def provideLayer[E1 >: E, R0, R1 <: R](
    layer: ZLayer[R0, E1, R1],
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): HttpResult[R0, E1, A] =
    HttpResult.ProvideLayer(self, layer.mapError(e => Some(e)))

  final private[zhttp] def evaluate: HttpResult.Out[R, E, A] = HttpResult.evaluate(self)
}

object HttpResult {
  sealed trait Out[-R, +E, +A] extends HttpResult[R, E, A] { self =>
    def asEffect: ZIO[R, Option[E], A] = self match {
      case Empty      => ZIO.fail(None)
      case Success(a) => ZIO.succeed(a)
      case Failure(e) => ZIO.fail(Option(e))
      case Effect(z)  => z
    }
  }

  // CTOR
  final case class Success[A](a: A)                         extends Out[Any, Nothing, A]
  final case class Failure[E](e: E)                         extends Out[Any, E, Nothing]
  final case class Effect[R, E, A](z: ZIO[R, Option[E], A]) extends Out[R, E, A]
  case object Empty                                         extends Out[Any, Nothing, Nothing]

  // OPR
  private final case class Suspend[R, E, A](r: () => HttpResult[R, E, A])   extends HttpResult[R, E, A]
  private final case class FoldM[R, E, EE, A, AA](
    rr: HttpResult[R, E, A],
    ee: E => HttpResult[R, EE, AA],
    aa: A => HttpResult[R, EE, AA],
    dd: HttpResult[R, EE, AA],
  )                                                                         extends HttpResult[R, EE, AA]
  private final case class Provide[R, E, A](r: HttpResult[R, E, A], env: R) extends HttpResult[Any, E, A]

  private final case class ProvideLayer[E, E1 >: E, R, R0, R1 <: R, A](
    r: HttpResult[R, E, A],
    layer: ZLayer[R0, Option[E1], R1],
  ) extends HttpResult[R0, E1, A]

  // Help
  def succeed[A](a: A): HttpResult.Out[Any, Nothing, A] = Success(a)
  def fail[E](e: E): HttpResult.Out[Any, E, Nothing]    = Failure(e)
  def empty: HttpResult.Out[Any, Nothing, Nothing]      = Empty

  def effect[R, E, A](z: ZIO[R, E, A]): HttpResult.Out[R, E, A] = Effect(z.mapError(Option(_)))
  def unit: HttpResult[Any, Nothing, Unit]                      = HttpResult.succeed(())

  def flatMap[R, E, A, B](r: HttpResult[R, E, A], aa: A => HttpResult[R, E, B]): HttpResult[R, E, B] =
    HttpResult.foldM(r, HttpResult.fail[E], aa, HttpResult.empty)

  def suspend[R, E, A](r: => HttpResult[R, E, A]): HttpResult[R, E, A] = HttpResult.Suspend(() => r)

  def foldM[R, E, EE, A, AA](
    r: HttpResult[R, E, A],
    ee: E => HttpResult[R, EE, AA],
    aa: A => HttpResult[R, EE, AA],
    dd: HttpResult[R, EE, AA],
  ): HttpResult[R, EE, AA] =
    HttpResult.FoldM(r, ee, aa, dd)

  def flatMapError[R, E, E1, A](r: HttpResult[R, E, A], ee: E => HttpResult[R, E1, A]): HttpResult[R, E1, A] =
    HttpResult.foldM(r, ee, HttpResult.succeed[A], HttpResult.empty)

  // EVAL
  @tailrec
  private[zhttp] def evaluate[R, R0, R1 <: R, E, E1 >: E, A](
    result: HttpResult[R, E, A],
  )(implicit ev: NeedsEnv[R]): Out[R, E, A] = {
    result match {
      case m: Out[_, _, _]                                     => m
      case Suspend(r)                                          => evaluate(r())
      case Provide(r, env)                                     => {
        evaluate(r match {
          case Effect(z)                                        => {
            Effect(z.provide(env))
          }
          case out: Out[R, E, A] @unchecked                     => out
          case Suspend(r)                                       => r().provide(env)
          case FoldM(rr, ee, aa, dd)                            => {
            rr.foldM(e => ee(e), a => aa(a), dd).evaluate.provide(env)
          }
          case Provide(self: HttpResult[R, E, A] @unchecked, _) => self
          case ProvideLayer(r2, _)                              => r2.provide(env)
        })
      }
      case ProvideLayer(r, layer: ZLayer[R, E, R1] @unchecked) => {
        evaluate(r match {
          case Effect(z)             => {
            Effect(z.provideLayer(layer))
          }
          case m: Out[_, _, _]       => m
          case Suspend(r)            => r().provideLayer(layer)
          case FoldM(rr, ee, aa, dd) => {
            rr.foldM(e => ee(e), a => aa(a), dd).evaluate.provideLayer(layer)
          }
          case p @ Provide(_, _)     => p.evaluate.provideLayer(layer)
          case ProvideLayer(r2, _)   => r2
        })
      }
      case FoldM(self, ee, aa, dd)                             =>
        evaluate(self match {
          case Empty                                                                 => dd
          case Success(a)                                                            => aa(a)
          case Failure(e)                                                            => ee(e)
          case Suspend(r)                                                            => r().foldM(ee, aa, dd)
          case Effect(z)                                                             => {
            Effect(
              z.foldM(
                {
                  case None    => ZIO.fail(None)
                  case Some(e) => ee(e).evaluate.asEffect
                },
                aa(_).evaluate.asEffect,
              ),
            )
          }
          case FoldM(self, ee0, aa0, dd0)                                            =>
            self.foldM(
              e => ee0(e).foldM(ee, aa, dd),
              a => aa0(a).foldM(ee, aa, dd),
              dd0.foldM(ee, aa, dd),
            )
          case Provide(r: HttpResult[R @unchecked, E @unchecked, A @unchecked], env) => {
            r.foldM(e => ee(e).provide(env), a => aa(a).provide(env), dd.provide(env))
          }
          case ProvideLayer(r, layer: ZLayer[R, E, R] @unchecked)                    => {
            r.foldM(ee, aa, dd).evaluate.provideLayer(layer)
          }
        })
    }
  }
}
