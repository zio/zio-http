package zio.http

/**
 * Max-one-engine-per-version proof (Scala 2: unchecked, enforced on Scala 3
 * only).
 */
sealed trait EngineNotRegistered[Ps, P]

object EngineNotRegistered {
  implicit def proof[Ps, P]: EngineNotRegistered[Ps, P] =
    new EngineNotRegistered[Ps, P] {}
}
