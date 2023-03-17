/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.model.headers.values

import scala.annotation.tailrec
import scala.util.Try

import zio.Chunk

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
  final case class Multiple(values: Chunk[CacheControl]) extends CacheControl {
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

  def parse(value: String): Either[String, CacheControl] = {
    val index = value.indexOf(",")

    @tailrec def loop(value: String, index: Int, acc: Multiple): Either[String, Multiple] = {
      if (index == -1) {
        identifyCacheControl(value) match {
          case Left(value)         => Left(value)
          case Right(cacheControl) => Right(acc.copy(values = acc.values :+ cacheControl))
        }
      } else {
        val valueChunk = value.substring(0, index)
        val remaining  = value.substring(index + 1)
        val nextIndex  = remaining.indexOf(",")
        identifyCacheControl(valueChunk) match {
          case Left(error)         => Left(error)
          case Right(cacheControl) =>
            loop(
              remaining,
              nextIndex,
              acc.copy(values = acc.values :+ cacheControl),
            )
        }
      }
    }

    if (index == -1)
      identifyCacheControl(value)
    else
      loop(value, index, Multiple(Chunk.empty[CacheControl]))
  }

  def render(value: CacheControl): String = {
    value match {
      case Immutable                         => Immutable.raw
      case m @ MaxAge(freshForSeconds)       => s"${m.raw}=$freshForSeconds"
      case m @ MaxStale(staleWithinSeconds)  => s"${m.raw}=$staleWithinSeconds"
      case m @ MinFresh(freshAtLeastSeconds) => s"${m.raw}=$freshAtLeastSeconds"
      case MustRevalidate                    => MustRevalidate.raw
      case MustUnderstand                    => MustUnderstand.raw
      case Multiple(values)                  => values.map(render).mkString(",")
      case NoCache                           => NoCache.raw
      case NoStore                           => NoStore.raw
      case NoTransform                       => NoTransform.raw
      case OnlyIfCached                      => OnlyIfCached.raw
      case Private                           => Private.raw
      case ProxyRevalidate                   => ProxyRevalidate.raw
      case Public                            => Public.raw
      case s @ SMaxAge(freshForSeconds)      => s"${s.raw}=$freshForSeconds"
      case s @ StaleIfError(seconds)         => s"${s.raw}=$seconds"
      case s @ StaleWhileRevalidate(seconds) => s"${s.raw}=$seconds"
    }
  }

  private def identifyCacheControl(value: String): Either[String, CacheControl] = {
    val index = value.indexOf("=")
    if (index == -1)
      identifyCacheControlValue(value)
    else
      identifyCacheControlValue(value.substring(0, index), Try(value.substring(index + 1).toInt).toOption)

  }

  private def identifyCacheControlValue(value: String, seconds: Option[Int] = None): Either[String, CacheControl] = {
    val trimmedValue = value.trim()
    trimmedValue match {
      case "max-age"                => Right(MaxAge(seconds.getOrElse(0)))
      case "max-stale"              => Right(MaxStale(seconds.getOrElse(0)))
      case "min-fresh"              => Right(MinFresh(seconds.getOrElse(0)))
      case "s-maxage"               => Right(SMaxAge(seconds.getOrElse(0)))
      case NoCache.raw              => Right(NoCache)
      case NoStore.raw              => Right(NoStore)
      case NoTransform.raw          => Right(NoTransform)
      case OnlyIfCached.raw         => Right(OnlyIfCached)
      case MustRevalidate.raw       => Right(MustRevalidate)
      case ProxyRevalidate.raw      => Right(ProxyRevalidate)
      case MustUnderstand.raw       => Right(MustUnderstand)
      case Private.raw              => Right(Private)
      case Public.raw               => Right(Public)
      case Immutable.raw            => Right(Immutable)
      case "stale-while-revalidate" => Right(StaleWhileRevalidate(seconds.getOrElse(0)))
      case "stale-if-error"         => Right(StaleIfError(seconds.getOrElse(0)))
      case _                        => Left(s"Unknown cache control value: $trimmedValue")
    }
  }

}
