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

package zio.http

import java.net.{InetAddress, UnknownHostException}
import java.time.Instant

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import com.lihaoyi.unroll

/**
 * Resolves host names to IP addresses. The client resolves the host of each new
 * connection with the `DnsResolver` in its environment.
 *
 * The layers `default`, `configured` and `live` build a resolver that caches
 * the results; `system` builds one without a cache.
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/configs/dns-resolver/ DNS Resolver Config]]
 */
trait DnsResolver {

  /**
   * Resolves `host` to its addresses. Fails with an `UnknownHostException` when
   * the host cannot be resolved.
   */
  def resolve(host: String)(implicit trace: Trace): ZIO[Any, UnknownHostException, Chunk[InetAddress]]
}

/** Layers and configuration for `DnsResolver`. */
object DnsResolver {

  /** Resolves `host` with the `DnsResolver` in the environment. */
  def resolve(host: String)(implicit trace: Trace): ZIO[DnsResolver, UnknownHostException, Chunk[InetAddress]] =
    ZIO.serviceWithZIO(_.resolve(host))

  private final case class SystemResolver() extends DnsResolver {
    override def resolve(host: String)(implicit trace: Trace): ZIO[Any, UnknownHostException, Chunk[InetAddress]] =
      ZIO
        .attemptBlocking(InetAddress.getAllByName(host))
        .refineToOrDie[UnknownHostException]
        .map(Chunk.fromArray)
  }

  private[http] final case class CacheEntry(
    resolvedAddresses: Promise[UnknownHostException, Chunk[InetAddress]],
    previousAddresses: Option[Chunk[InetAddress]],
    lastUpdatedAt: Instant,
    failureCount: Int = 0,
  )

  /**
   * What a caching resolver does with an entry whose time to live has passed:
   * `Refresh` or `Drop`.
   */
  sealed trait ExpireAction

  /** The expire actions, and their `Config` reader. */
  object ExpireAction {

    /**
     * Resolves the host again in the background. Until the new lookup
     * completes, or when it fails, the resolver returns the previous addresses.
     */
    case object Refresh extends ExpireAction

    /**
     * Removes the entry, so that the next request for the host resolves it
     * again.
     */
    case object Drop extends ExpireAction

    /**
     * Reads one of the strings `refresh` or `drop`. Any other value fails with
     * a message that starts with `Invalid expire action:`.
     */
    val config: zio.Config[ExpireAction] =
      zio.Config.string.mapOrFail {
        case "refresh" => Right(Refresh)
        case "drop"    => Right(Drop)
        case other     => Left(zio.Config.Error.InvalidData(message = s"Invalid expire action: $other"))
      }
  }

  private sealed trait CachePatch
  private object CachePatch {
    final case class Remove(host: String)                           extends CachePatch
    final case class Update(host: String, updatedEntry: CacheEntry) extends CachePatch
  }

  private class CachingResolver(
    resolver: DnsResolver,
    ttl: Duration,
    unknownHostTtl: Duration,
    maxCount: Int,
    expireAction: ExpireAction,
    maxRetries: Int,
    semaphore: Semaphore,
    entries: Ref[Map[String, CacheEntry]],
  ) extends DnsResolver {
    override def resolve(host: String)(implicit trace: Trace): ZIO[Any, UnknownHostException, Chunk[InetAddress]] =
      for {
        now       <- Clock.instant
        fiberId   <- ZIO.fiberId
        getResult <- entries.modify { entries =>
          entries.get(host) match {
            case Some(entry) =>
              entry.previousAddresses match {
                case Some(previous) =>
                  (
                    ZIO.ifZIO(entry.resolvedAddresses.isDone)(
                      onTrue = entry.resolvedAddresses.await.orElseSucceed(previous),
                      onFalse = ZIO.succeed(previous),
                    ),
                    entries,
                  )
                case None           =>
                  (entry.resolvedAddresses.await, entries)
              }
            case None        =>
              val newPromise = Promise.unsafe.make[UnknownHostException, Chunk[InetAddress]](fiberId)(Unsafe.unsafe)
              (
                startResolvingHost(host, newPromise).zipRight(newPromise.await),
                entries.updated(host, CacheEntry(newPromise, None, now)),
              )
          }
        }
        result    <- getResult
      } yield result

    /** Gets a snapshot of the cache state, for testing only */
    private[http] def snapshot()(implicit trace: Trace): ZIO[DnsResolver, Nothing, Map[String, CacheEntry]] =
      entries.get

    private def startResolvingHost(
      host: String,
      targetPromise: Promise[UnknownHostException, Chunk[InetAddress]],
    )(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
      semaphore.withPermit {
        resolver.resolve(host).intoPromise(targetPromise)
      }.fork.unit

    private def refreshAndCleanup()(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
      // Resolve only adds new entries for unseen hosts, so it is safe to do this non-atomically
      for {
        fiberId            <- ZIO.fiberId
        snapshot           <- entries.get
        refreshPatches     <- refreshOrDropEntries(fiberId, snapshot)
        sizeControlPatches <- ensureMaxSize(applyPatches(snapshot, refreshPatches))
        _                  <- entries.update(applyPatches(_, refreshPatches ++ sizeControlPatches))
      } yield ()

    private def refreshOrDropEntries(
      fiberId: FiberId,
      entries: Map[String, CacheEntry],
    )(implicit trace: Trace): ZIO[Any, Nothing, Chunk[CachePatch]] =
      Clock.instant.flatMap { now =>
        ZIO
          .foreach(Chunk.fromIterable(entries)) { case (host, entry) =>
            entry.resolvedAddresses.poll.flatMap {
              case Some(getResolvedAddresses) =>
                getResolvedAddresses.foldZIO(
                  failure = (_: UnknownHostException) => {
                    if (entry.lastUpdatedAt.plus(unknownHostTtl).isBefore(now)) {
                      if (expireAction == ExpireAction.Refresh) {
                        if (entry.failureCount + 1 >= maxRetries) {
                          ZIO.some(CachePatch.Remove(host))
                        } else {
                          val newEntry = CacheEntry(
                            Promise.unsafe.make(fiberId)(Unsafe.unsafe),
                            None,
                            now,
                            failureCount = entry.failureCount + 1,
                          )
                          startResolvingHost(host, newEntry.resolvedAddresses)
                            .as(Some(CachePatch.Update(host, newEntry)))
                        }
                      } else {
                        ZIO.some(CachePatch.Remove(host))
                      }
                    } else {
                      // failed entry not expired yet
                      ZIO.none
                    }
                  },
                  success = addresses => {
                    if (entry.lastUpdatedAt.plus(ttl).isBefore(now)) {
                      if (expireAction == ExpireAction.Refresh) {
                        val newEntry = CacheEntry(
                          Promise.unsafe.make(fiberId)(Unsafe.unsafe),
                          Some(addresses),
                          now,
                          failureCount = 0,
                        )
                        startResolvingHost(host, newEntry.resolvedAddresses)
                          .as(Some(CachePatch.Update(host, newEntry)))
                      } else {
                        ZIO.some(CachePatch.Remove(host))
                      }
                    } else {
                      // successful entry not expired yet
                      ZIO.none
                    }
                  },
                )
              case None                       =>
                ZIO.none
            }
          }
          .map(_.flatten)
      }

    private def ensureMaxSize(
      entries: Map[String, CacheEntry],
    )(implicit trace: Trace): ZIO[Any, Nothing, Chunk[CachePatch]] =
      if (entries.size > maxCount) {
        val toDrop = Chunk.fromIterable(entries).sortBy(_._2.lastUpdatedAt).take(entries.size - maxCount)
        ZIO
          .foreach(toDrop) { case (host, entry) =>
            entry.resolvedAddresses.interrupt.as(CachePatch.Remove(host))
          }
      } else {
        ZIO.succeed(Chunk.empty)
      }

    private def applyPatches(entries: Map[String, CacheEntry], patches: Chunk[CachePatch]): Map[String, CacheEntry] =
      patches.foldLeft(entries) { case (entries, patch) =>
        patch match {
          case CachePatch.Remove(host)               =>
            entries - host
          case CachePatch.Update(host, updatedEntry) =>
            entries.updated(host, updatedEntry)
        }
      }
  }

  private object CachingResolver {
    def make(
      resolver: DnsResolver,
      ttl: Duration,
      unknownHostTtl: Duration,
      maxCount: Int,
      maxConcurrentResolutions: Int,
      expireAction: ExpireAction,
      refreshRate: Duration,
      maxRetries: Int,
    )(implicit trace: Trace): ZIO[Scope, Nothing, DnsResolver] =
      for {
        semaphore <- Semaphore.make(maxConcurrentResolutions.toLong)
        entries   <- Ref.make(Map.empty[String, CacheEntry])
        cachingResolver = new CachingResolver(
          resolver,
          ttl,
          unknownHostTtl,
          maxCount,
          expireAction,
          maxRetries,
          semaphore,
          entries,
        )
        _ <- cachingResolver.refreshAndCleanup().scheduleFork(Schedule.fixed(refreshRate))
      } yield cachingResolver
  }

  private[http] def snapshot()(implicit trace: Trace): ZIO[DnsResolver, Nothing, Map[String, CacheEntry]] =
    ZIO.service[DnsResolver].flatMap {
      case cachingResolver: CachingResolver => cachingResolver.snapshot()
      case _ => ZIO.dieMessage(s"Unexpected DnsResolver implementation: ${getClass.getName}")
    }

  /**
   * The settings of the caching resolver built by `live`.
   *
   * @param ttl
   *   how long the addresses of a successful lookup are used before the entry
   *   expires. Expiry is checked every `refreshRate`.
   * @param unknownHostTtl
   *   the same for a lookup that failed with an `UnknownHostException`
   * @param maxCount
   *   the maximum number of cached hosts. When the cache is checked and holds
   *   more, the entries that were updated longest ago are dropped.
   * @param maxConcurrentResolutions
   *   the maximum number of lookups that run at the same time
   * @param expireAction
   *   what happens to an entry that has expired
   * @param refreshRate
   *   how often the cache is checked for expired entries and for its size
   * @param maxRetries
   *   with `ExpireAction.Refresh`, the number of failed lookups of a host,
   *   counting the first one, after which the host is dropped from the cache
   *   instead of being looked up again. It defaults to 3.
   *
   * @see
   *   [[https://zio.dev/zio-http/reference/configs/dns-resolver/ DNS Resolver Config]]
   */
  final case class Config(
    ttl: Duration,
    unknownHostTtl: Duration,
    maxCount: Int,
    maxConcurrentResolutions: Int,
    expireAction: ExpireAction,
    refreshRate: Duration,
    @unroll
    maxRetries: Int = 3,
  )

  /** The `Config` reader and the default value of `DnsResolver.Config`. */
  object Config {

    /**
     * Reads the settings from the keys `ttl`, `unknown-host-ttl`, `max-count`,
     * `max-concurrent-resolutions`, `expire-action`, `refresh-rate` and
     * `max-retries`. A missing key gives the value of `Config.default`.
     */
    def config: zio.Config[Config] =
      (zio.Config.duration("ttl").withDefault(Config.default.ttl) ++
        zio.Config.duration("unknown-host-ttl").withDefault(Config.default.unknownHostTtl) ++
        zio.Config.int("max-count").withDefault(Config.default.maxCount) ++
        zio.Config.int("max-concurrent-resolutions").withDefault(Config.default.maxConcurrentResolutions) ++
        ExpireAction.config.nested("expire-action").withDefault(Config.default.expireAction) ++
        zio.Config.duration("refresh-rate").withDefault(Config.default.refreshRate) ++
        zio.Config.int("max-retries").withDefault(Config.default.maxRetries)).map {
        case (ttl, unknownHostTtl, maxCount, maxConcurrentResolutions, expireAction, refreshRate, maxRetries) =>
          Config(ttl, unknownHostTtl, maxCount, maxConcurrentResolutions, expireAction, refreshRate, maxRetries)
      }

    /**
     * The default settings: `ttl` 10 minutes, `unknownHostTtl` 1 minute,
     * `maxCount` 4096, `maxConcurrentResolutions` 16, `expireAction`
     * `ExpireAction.Refresh`, `refreshRate` 2 seconds and `maxRetries` 3.
     */
    def default: Config = Config(
      ttl = 10.minutes,
      unknownHostTtl = 1.minute,
      maxCount = 4096,
      maxConcurrentResolutions = 16,
      expireAction = ExpireAction.Refresh,
      refreshRate = 2.seconds,
      maxRetries = 3,
    )
  }

  /**
   * A layer that reads the `Config` from the config provider under `path`,
   * which defaults to `zio.http.dns`, and builds the caching resolver of `live`
   * from it. It fails with a `zio.Config.Error` when the config is invalid.
   */
  def configured(
    path: NonEmptyChunk[String] = NonEmptyChunk("zio", "http", "dns"),
  )(implicit trace: Trace): ZLayer[Any, zio.Config.Error, DnsResolver] =
    ZLayer(ZIO.config(Config.config.nested(path.head, path.tail: _*))) >>> live

  /** A layer for a caching resolver with the settings of `Config.default`. */
  val default: ZLayer[Any, Nothing, DnsResolver] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.succeed(Config.default) >>> live
  }

  private[http] def explicit(
    ttl: Duration = 10.minutes,
    unknownHostTtl: Duration = 1.minute,
    maxCount: Int = 4096,
    maxConcurrentResolutions: Int = 16,
    expireAction: ExpireAction = ExpireAction.Refresh,
    refreshRate: Duration = 2.seconds,
    implementation: DnsResolver = SystemResolver(),
    maxRetries: Int = 3,
  ): ZLayer[Any, Nothing, DnsResolver] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.scoped {
      CachingResolver
        .make(
          implementation,
          ttl,
          unknownHostTtl,
          maxCount,
          maxConcurrentResolutions,
          expireAction,
          refreshRate,
          maxRetries,
        )
    }
  }

  /**
   * A layer for a resolver that caches the results of
   * `InetAddress.getAllByName` for the settings in the environment. The
   * resolver checks its cache every `refreshRate` in a background fiber that
   * lives as long as the layer.
   */
  val live: ZLayer[DnsResolver.Config, Nothing, DnsResolver] = {
    implicit val trace: Trace = Trace.empty

    ZLayer.scoped {
      for {
        config   <- ZIO.service[Config]
        resolver <- CachingResolver.make(
          SystemResolver(),
          config.ttl,
          config.unknownHostTtl,
          config.maxCount,
          config.maxConcurrentResolutions,
          config.expireAction,
          config.refreshRate,
          config.maxRetries,
        )
      } yield resolver
    }
  }

  /**
   * A layer for a resolver without a cache: each call runs
   * `InetAddress.getAllByName` on the blocking thread pool.
   */
  val system: ZLayer[Any, Nothing, DnsResolver] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.succeed(SystemResolver())
  }
}
