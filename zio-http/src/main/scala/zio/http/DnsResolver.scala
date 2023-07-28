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

trait DnsResolver {
  def resolve(host: String)(implicit trace: Trace): ZIO[Any, UnknownHostException, Chunk[InetAddress]]
}

object DnsResolver {
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
  )

  sealed trait ExpireAction
  object ExpireAction {
    case object Refresh extends ExpireAction
    case object Drop    extends ExpireAction

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
                      onTrue = entry.resolvedAddresses.await,
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
                        val newEntry = CacheEntry(
                          Promise.unsafe.make(fiberId)(Unsafe.unsafe),
                          None,
                          now,
                        )
                        startResolvingHost(host, newEntry.resolvedAddresses)
                          .as(Some(CachePatch.Update(host, newEntry)))
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
    )(implicit trace: Trace): ZIO[Scope, Nothing, DnsResolver] =
      for {
        semaphore <- Semaphore.make(maxConcurrentResolutions)
        entries   <- Ref.make(Map.empty[String, CacheEntry])
        cachingResolver = new CachingResolver(resolver, ttl, unknownHostTtl, maxCount, expireAction, semaphore, entries)
        _ <- cachingResolver.refreshAndCleanup().scheduleFork(Schedule.fixed(refreshRate))
      } yield cachingResolver
  }

  private[http] def snapshot()(implicit trace: Trace): ZIO[DnsResolver, Nothing, Map[String, CacheEntry]] =
    ZIO.service[DnsResolver].flatMap {
      case cachingResolver: CachingResolver => cachingResolver.snapshot()
      case _ => ZIO.dieMessage(s"Unexpected DnsResolver implementation: ${getClass.getName}")
    }

  final case class Config(
    ttl: Duration,
    unknownHostTtl: Duration,
    maxCount: Int,
    maxConcurrentResolutions: Int,
    expireAction: ExpireAction,
    refreshRate: Duration,
  )

  object Config {
    lazy val config: zio.Config[Config] =
      (zio.Config.duration("ttl").withDefault(Config.default.ttl) ++
        zio.Config.duration("unknown-host-ttl").withDefault(Config.default.unknownHostTtl) ++
        zio.Config.int("max-count").withDefault(Config.default.maxCount) ++
        zio.Config.int("max-concurrent-resolutions").withDefault(Config.default.maxConcurrentResolutions) ++
        ExpireAction.config.nested("expire-action").withDefault(Config.default.expireAction) ++
        zio.Config.duration("refresh-rate").withDefault(Config.default.refreshRate)).map {
        case (ttl, unknownHostTtl, maxCount, maxConcurrentResolutions, expireAction, refreshRate) =>
          Config(ttl, unknownHostTtl, maxCount, maxConcurrentResolutions, expireAction, refreshRate)
      }

    lazy val default: Config = Config(
      ttl = 10.minutes,
      unknownHostTtl = 1.minute,
      maxCount = 4096,
      maxConcurrentResolutions = 16,
      expireAction = ExpireAction.Refresh,
      refreshRate = 2.seconds,
    )
  }

  def configured(
    path: NonEmptyChunk[String] = NonEmptyChunk("zio", "http", "dns"),
  )(implicit trace: Trace): ZLayer[Any, zio.Config.Error, DnsResolver] =
    ZLayer(ZIO.config(Config.config.nested(path.head, path.tail: _*))) >>> live

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
        )
    }
  }

  lazy val live: ZLayer[DnsResolver.Config, Nothing, DnsResolver] = {
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
        )
      } yield resolver
    }
  }

  val system: ZLayer[Any, Nothing, DnsResolver] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.succeed(SystemResolver())
  }
}
