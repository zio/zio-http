package zio.web

import zio.URIO

sealed trait Handler[M, -R, I, O] { self =>
  type Identity
  type Input  = I
  type Output = O

  final def +[M2, R2](that: Handler[M2, R2, _, _]): Handlers[M with M2, R with R2, Identity with that.Identity] =
    Handlers[M, R, M2, R2](self, that)

  def endpoint: Endpoint[M, I, O]
  def handler: I => URIO[R, O]
}

object Handler {

  type Aux[M, -R, I, O, Identity0] =
    Handler[M, R, I, O] {
      type Identity = Identity0
    }

  def apply[M, R, I, O](
    endpoint0: Endpoint[M, I, O],
    handler0: I => URIO[R, O]
  ): Handler.Aux[M, R, I, O, endpoint0.Identity] =
    new Handler[M, R, I, O] {
      type Identity = endpoint0.Identity

      def endpoint: Endpoint[M, I, O] = endpoint0
      def handler: I => URIO[R, O]    = handler0
    }

  def make[M, R, I, O](
    endpoint: Endpoint[M, I, O]
  )(handler: I => URIO[R, O]): Handler.Aux[M, R, I, O, endpoint.Identity] =
    apply(endpoint, handler)
}
