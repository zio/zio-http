package zio.http

import scala.annotation.static

/**
 * Copied from: https://github.com/ghostdogpr/caliban/pull/2366
 */
private[http] object syntax {
  @static val NullFn: () => AnyRef = () => null

  extension [K, V <: AnyRef](inline map: Map[K, V]) {
    transparent inline def getOrElseNull(key: K): V = map.getOrElse(key, NullFn()).asInstanceOf[V]
  }
}

// Required for @static fields
private final class syntax private
