package zio.http

/**
 * Max-one-engine-per-version proof (Scala 3: enforced via `Tuple.Contains`).
 */
sealed trait EngineNotRegistered[Ps <: Tuple, P]

object EngineNotRegistered {
  implicit def proof[Ps <: Tuple, P](implicit
    ev: Tuple.Contains[Ps, P] =:= false,
  ): EngineNotRegistered[Ps, P] =
    new EngineNotRegistered[Ps, P] {}
}
