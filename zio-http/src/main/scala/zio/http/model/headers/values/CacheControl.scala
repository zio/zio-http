package zio.http.model.headers.values

import zio.Chunk

import scala.annotation.tailrec
import scala.util.Try

/**
 * CacheControl header value.
 */
sealed trait CacheControl {
  val raw: String
}

object CacheControl {

  /**
   * The immutable response directive indicates that the response will not be
   * updated while it's fresh
   */
  case object Immutable extends CacheControl {
    override val raw: String = "immutable"
  }

  /**
   * Signals an invalid value present in the header value.
   */
  case object InvalidCacheControl extends CacheControl {
    override val raw: String = "Invalid header value"
  }

  /**
   * The max-age=N response directive indicates that the response remains fresh
   * until N seconds after the response is generated.
   *
   * The max-age=N request directive indicates that the client allows a stored
   * response that is generated on the origin server within N seconds
   */
  final case class MaxAge(freshForSeconds: Int) extends CacheControl {
    override val raw: String = "max-age"
  }

  /**
   * The max-stale=N request directive indicates that the client allows a stored
   * response that is stale within N seconds.
   */
  final case class MaxStale(staleWithinSeconds: Int) extends CacheControl {
    override val raw: String = "max-stale"
  }

  /**
   * The min-fresh=N request directive indicates that the client allows a stored
   * response that is fresh for at least N seconds.
   */
  final case class MinFresh(freshAtLeastSeconds: Int) extends CacheControl {
    override val raw: String = "min-fresh"
  }

  /**
   * The must-revalidate response directive indicates that the response can be
   * stored in caches and can be reused while fresh. If the response becomes
   * stale, it must be validated with the origin server before reuse.
   */
  case object MustRevalidate extends CacheControl {
    override val raw: String = "must-revalidate"
  }

  /**
   * The must-understand response directive indicates that a cache should store
   * the response only if it understands the requirements for caching based on
   * status code.
   */
  case object MustUnderstand extends CacheControl {
    override val raw: String = "must-understand"
  }

  /**
   * Maintains a chunk of CacheControl values.
   */
  final case class MultipleCacheControlValues(values: Chunk[CacheControl]) extends CacheControl {
    override val raw: String = values.map(_.raw).mkString(",")
  }

  /**
   * The no-cache response directive indicates that the response can be stored
   * in caches, but the response must be validated with the origin server before
   * each reuse.
   *
   * The no-cache request directive asks caches to validate the response with
   * the origin server before reuse.
   */
  case object NoCache extends CacheControl {
    override val raw: String = "no-cache"
  }

  /**
   * The no-store response directive indicates that any caches of any kind
   * (private or shared) should not store this response.
   *
   * The no-store request directive allows a client to request that caches
   * refrain from storing the request and corresponding response — even if the
   * origin server's response could be stored.
   */
  case object NoStore extends CacheControl {
    override val raw: String = "no-store"
  }

  /**
   * The no-transform indicates that any intermediary (regardless of whether it
   * implements a cache) shouldn't transform the response/request contents.
   */
  case object NoTransform extends CacheControl {
    override val raw: String = "no-transform"
  }

  /**
   * The client indicates that cache should obtain an already-cached response.
   * If a cache has stored a response, it's reused.
   */
  case object OnlyIfCached extends CacheControl {
    override val raw: String = "only-if-cached"
  }

  /**
   * The private response directive indicates that the response can be stored
   * only in a private cache
   */
  case object Private extends CacheControl {
    override val raw: String = "private"
  }

  /**
   * The proxy-revalidate response directive is the equivalent of
   * must-revalidate, but specifically for shared caches only.
   */
  case object ProxyRevalidate extends CacheControl {
    override val raw: String = "proxy-revalidate"
  }

  /**
   * The public response directive indicates that the response can be stored in
   * a shared cache.
   */
  case object Public extends CacheControl {
    override val raw: String = "public"
  }

  /**
   * The s-maxage response directive also indicates how long the response is
   * fresh for (similar to max-age) — but it is specific to shared caches, and
   * they will ignore max-age when it is present.
   */
  final case class SMaxAge(freshForSeconds: Int) extends CacheControl {
    override val raw: String = "s-maxage"
  }

  /**
   * The stale-if-error response directive indicates that the cache can reuse a
   * stale response when an origin server responds with an error (500, 502, 503,
   * or 504).
   */
  final case class StaleIfError(seconds: Int) extends CacheControl {
    override val raw: String = "stale-if-error"
  }

  /**
   * The stale-while-revalidate response directive indicates that the cache
   * could reuse a stale response while it revalidates it to a cache.
   */
  final case class StaleWhileRevalidate(seconds: Int) extends CacheControl {
    override val raw: String = "stale-while-revalidate"
  }

  def fromCacheControl(value: CacheControl): String = {
    value match {
      case Immutable                          => Immutable.raw
      case InvalidCacheControl                => ""
      case m @ MaxAge(freshForSeconds)        => s"${m.raw}=$freshForSeconds"
      case m @ MaxStale(staleWithinSeconds)   => s"${m.raw}=$staleWithinSeconds"
      case m @ MinFresh(freshAtLeastSeconds)  => s"${m.raw}=$freshAtLeastSeconds"
      case MustRevalidate                     => MustRevalidate.raw
      case MustUnderstand                     => MustUnderstand.raw
      case MultipleCacheControlValues(values) => values.map(fromCacheControl).mkString(",")
      case NoCache                            => NoCache.raw
      case NoStore                            => NoStore.raw
      case NoTransform                        => NoTransform.raw
      case OnlyIfCached                       => OnlyIfCached.raw
      case Private                            => Private.raw
      case ProxyRevalidate                    => ProxyRevalidate.raw
      case Public                             => Public.raw
      case s @ SMaxAge(freshForSeconds)       => s"${s.raw}=$freshForSeconds"
      case s @ StaleIfError(seconds)          => s"${s.raw}=$seconds"
      case s @ StaleWhileRevalidate(seconds)  => s"${s.raw}=$seconds"
    }
  }

  def toCacheControl(value: String): CacheControl = {
    val index = value.indexOf(",")

    @tailrec def loop(value: String, index: Int, acc: MultipleCacheControlValues): MultipleCacheControlValues = {
      if (index == -1) acc.copy(values = acc.values ++ Chunk(identifyCacheControl(value)))
      else {
        val valueChunk       = value.substring(0, index)
        val remaining        = value.substring(index + 1)
        val nextIndex        = remaining.indexOf(",")
        val acceptedEncoding = Chunk(identifyCacheControl(valueChunk))
        loop(
          remaining,
          nextIndex,
          acc.copy(values = acc.values ++ acceptedEncoding),
        )
      }
    }

    if (index == -1)
      identifyCacheControl(value)
    else
      loop(value, index, MultipleCacheControlValues(Chunk.empty[CacheControl]))
  }

  private def identifyCacheControl(value: String): CacheControl = {
    val index = value.indexOf("=")
    if (index == -1)
      identifyCacheControlValue(value)
    else
      identifyCacheControlValue(value.substring(0, index), Try(value.substring(index + 1).toInt).toOption)

  }

  private def identifyCacheControlValue(value: String, seconds: Option[Int] = None): CacheControl = {
    val valueNoSpace = value.trim()
    valueNoSpace match {
      case "max-age"                => MaxAge(seconds.getOrElse(0))
      case "max-stale"              => MaxStale(seconds.getOrElse(0))
      case "min-fresh"              => MinFresh(seconds.getOrElse(0))
      case "s-maxage"               => SMaxAge(seconds.getOrElse(0))
      case NoCache.raw              => NoCache
      case NoStore.raw              => NoStore
      case NoTransform.raw          => NoTransform
      case OnlyIfCached.raw         => OnlyIfCached
      case MustRevalidate.raw       => MustRevalidate
      case ProxyRevalidate.raw      => ProxyRevalidate
      case MustUnderstand.raw       => MustUnderstand
      case Private.raw              => Private
      case Public.raw               => Public
      case Immutable.raw            => Immutable
      case "stale-while-revalidate" => StaleWhileRevalidate(seconds.getOrElse(0))
      case "stale-if-error"         => StaleIfError(seconds.getOrElse(0))
      case _                        => InvalidCacheControl
    }
  }

}
