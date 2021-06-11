package zio.web

import com.github.ghik.silencer.silent
import zio.{ =!=, URIO }

/**
 * A `Handler[M, P, R, I, O]` represents a handler for `Endpoint[M, P, I, O]`  that requires environment `R`.
 */
sealed trait Handler[+M[+_], P, -R, I, O] { self =>
  type Id

  final def +[M1[+_] >: M[_], R1 <: R](that: Handler[M1, _, R1, _, _]): Handlers[M1, R1, Id with that.Id] =
    Handlers(self, that)

  def endpoint: Endpoint[M, P, I, O]
  def handler: (P, I) => URIO[R, O]
}

object Handler {

  type Aux[+M[+_], P, -R, I, O, Id0] =
    Handler[M, P, R, I, O] {
      type Id = Id0
    }

  def apply[M[+_], P, R, I, O](
    endpoint0: Endpoint[M, P, I, O],
    handler0: (P, I) => URIO[R, O]
  ): Handler.Aux[M, P, R, I, O, endpoint0.Id] =
    new Handler[M, P, R, I, O] {
      type Id = endpoint0.Id

      def endpoint: Endpoint[M, P, I, O] = endpoint0
      def handler: (P, I) => URIO[R, O]  = handler0
    }

  def make[M[+_], P, R, I, O](
    endpoint: Endpoint[M, P, I, O]
  ): HandlerMaker[M, P, R, I, O, endpoint.Id] =
    new HandlerMaker[M, P, R, I, O, endpoint.Id](endpoint)

  final class HandlerMaker[M[+_], P, R, I, O, Id](val endpoint: Endpoint.Aux[M, P, I, O, Id]) {

    @silent("never used")
    def apply(handler: (P, I) => URIO[R, O])(implicit ev: P =!= Unit): Handler.Aux[M, P, R, I, O, Id] =
      Handler.apply(endpoint, handler)

    @silent("never used")
    def apply(handler: I => URIO[R, O])(implicit ev: P =:= Unit): Handler.Aux[M, P, R, I, O, Id] =
      Handler.apply(endpoint, (_, i) => handler(i))
  }
}
