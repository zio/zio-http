package zio.http.model.headers.values

sealed trait CacheControl {
  val raw: String
}

object CacheControl {

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
   * The s-maxage response directive also indicates how long the response is
   * fresh for (similar to max-age) — but it is specific to shared caches, and
   * they will ignore max-age when it is present.
   */
  final case class SMaxAge(freshForSeconds: Int) extends CacheControl {
    override val raw: String = "s-maxage"
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
   * The must-revalidate response directive indicates that the response can be
   * stored in caches and can be reused while fresh. If the response becomes
   * stale, it must be validated with the origin server before reuse.
   */
  case object MustRevalidate extends CacheControl {
    override val raw: String = "must-revalidate"
  }

  /**
   * The proxy-revalidate response directive is the equivalent of
   * must-revalidate, but specifically for shared caches only.
   */
  case object ProxyRevalidate extends CacheControl {
    override val raw: String = "proxy-revalidate"
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
   * The private response directive indicates that the response can be stored
   * only in a private cache
   */
  case object Private extends CacheControl {
    override val raw: String = "private"
  }

  /**
   * The public response directive indicates that the response can be stored in
   * a shared cache.
   */
  case object Public extends CacheControl {
    override val raw: String = "public"
  }

  /**
   * The immutable response directive indicates that the response will not be
   * updated while it's fresh
   */
  case object Immutable extends CacheControl {
    override val raw: String = "immutable"
  }

  /**
   * The stale-while-revalidate response directive indicates that the cache
   * could reuse a stale response while it revalidates it to a cache.
   */
  final case class StaleWhileRevalidate(seconds: Int) extends CacheControl {
    override val raw: String = "stale-while-revalidate"
  }

  /**
   * The stale-if-error response directive indicates that the cache can reuse a
   * stale response when an origin server responds with an error (500, 502, 503,
   * or 504).
   */
  final case class StaleIfError(seconds: Int) extends CacheControl {
    override val raw: String = "stale-if-error"
  }

}
