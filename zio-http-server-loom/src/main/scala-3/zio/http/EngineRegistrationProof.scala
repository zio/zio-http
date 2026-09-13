package zio.http

import scala.util.NotGiven

/**
 * Max-one-engine-per-version proof (Scala 3: enforced via conformance).
 *
 * `NotGiven[Ps <:< P]` is sound where `NotGiven` over a `Boolean` match type
 * was vacuous: `<:<` instances track real subtyping (one exists exactly when
 * `Ps` conforms to `P`), so the proof is refused precisely when `P` is already
 * registered and summons otherwise.
 */
sealed trait EngineNotRegistered[Ps, P]

object EngineNotRegistered {
  implicit def proof[Ps, P](implicit ev: NotGiven[Ps <:< P]): EngineNotRegistered[Ps, P] =
    new EngineNotRegistered[Ps, P] {}
}
