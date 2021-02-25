package zio.web

import zio.URIO

sealed trait Handler[+M[+_], P, -R, I, O] { self =>
  type Identity
  type Input  = I
  type Output = O

  final def +[M1[+_] >: M[_], R1 <: R](that: Handler[M1, _, R1, _, _]): Handlers[M1, R1, Identity with that.Identity] =
    Handlers(self, that)

  def endpoint: Endpoint[M, P, I, O]
  def handler: (I, P) => URIO[R, O]
}

object Handler {

  type Aux[+M[+_], P, -R, I, O, Identity0] =
    Handler[M, P, R, I, O] {
      type Identity = Identity0
    }

  def apply[M[+_], P, R, I, O](
    endpoint0: Endpoint[M, P, I, O],
    handler0: (I, P) => URIO[R, O]
  ): Handler.Aux[M, P, R, I, O, endpoint0.Identity] =
    new Handler[M, P, R, I, O] {
      type Identity = endpoint0.Identity

      def endpoint: Endpoint[M, P, I, O] = endpoint0
      def handler: (I, P) => URIO[R, O]  = handler0
    }

  def make[M[+_], P, R, I, O](
    endpoint: Endpoint[M, P, I, O]
  ): HandlerMaker[M, P, R, I, O] =
    new HandlerMaker[M, P, R, I, O](endpoint)

  class HandlerMaker[M[+_], P, R, I, O](val endpoint: Endpoint[M, P, I, O]) {

    def apply(handler: (I, P) => URIO[R, O]): Handler.Aux[M, P, R, I, O, endpoint.Identity] =
      Handler.apply(endpoint, handler)

    def apply(handler: I => URIO[R, O]): Handler.Aux[M, P, R, I, O, endpoint.Identity] =
      Handler.apply(endpoint, (i, _) => handler(i))
  }
}
