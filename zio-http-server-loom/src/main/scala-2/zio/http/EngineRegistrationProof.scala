package zio.http

/** Tuple facsimiles for the shared `LoomServer` (Scala 2 only). */
sealed trait Tuple
sealed trait EmptyTuple          extends Tuple
sealed trait *:[+H, +T <: Tuple] extends Tuple

/**
 * Max-one-engine-per-version proof (Scala 2: unchecked, enforced on Scala 3
 * only).
 */
sealed trait EngineNotRegistered[Ps <: Tuple, P]

object EngineNotRegistered {
  implicit def proof[Ps <: Tuple, P]: EngineNotRegistered[Ps, P] =
    new EngineNotRegistered[Ps, P] {}
}
