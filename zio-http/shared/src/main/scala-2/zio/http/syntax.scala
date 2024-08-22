package zio.http

/**
 * Copied from: https://github.com/ghostdogpr/caliban/pull/2366
 */
private[http] object syntax {
  val NullFn: () => AnyRef = () => null

  implicit final class EnrichedImmutableMapOps[K, V <: AnyRef](private val self: Map[K, V]) extends AnyVal {
    def getOrElseNull(key: K): V = self.getOrElse(key, NullFn()).asInstanceOf[V]
  }
}