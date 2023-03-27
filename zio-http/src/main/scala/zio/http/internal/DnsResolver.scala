package zio.http.internal

import zio._

import java.net.{InetAddress, UnknownHostException}
import java.time.Instant

trait DnsResolver {
  def resolve(host: String): ZIO[Any, UnknownHostException, Chunk[InetAddress]]
}

object DnsResolver {
  private final case class SystemResolver() extends DnsResolver {
    override def resolve(host: String): ZIO[Any, UnknownHostException, Chunk[InetAddress]] =
      ZIO
        .attemptBlocking(InetAddress.getAllByName(host))
        .refineToOrDie[UnknownHostException]
        .map(Chunk.fromArray)
  }

  private final case class CacheEntry(
    resolvedAddresses: Promise[UnknownHostException, Chunk[InetAddress]],
    previousAddresses: Option[Chunk[InetAddress]],
    lastUpdatedAt: Instant,
  )

  sealed trait ExpireAction
  object ExpireAction {
    case object Refresh extends ExpireAction
    case object Drop    extends ExpireAction
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
    override def resolve(host: String): ZIO[Any, UnknownHostException, Chunk[InetAddress]] =
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

    private def startResolvingHost(
      host: String,
      targetPromise: Promise[UnknownHostException, Chunk[InetAddress]],
    ): ZIO[Any, Nothing, Unit] =
      semaphore.withPermit {
        resolver.resolve(host).intoPromise(targetPromise)
      }.fork.unit

    private def refreshAndCleanup(): ZIO[Any, Nothing, Unit] =
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
    ): ZIO[Any, Nothing, Chunk[CachePatch]] =
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
                        startResolvingHost(host, newEntry.resolvedAddresses).as(Some(CachePatch.Update(host, newEntry)))
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

    private def ensureMaxSize(entries: Map[String, CacheEntry]): ZIO[Any, Nothing, Chunk[CachePatch]] =
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

  object CachingResolver {
    def make(
      resolver: DnsResolver,
      ttl: Duration,
      unknownHostTtl: Duration,
      maxCount: Int,
      maxConcurrentResolutions: Int,
      expireAction: ExpireAction,
      refreshRate: Duration,
    ): ZIO[Scope, Nothing, DnsResolver] =
      for {
        semaphore <- Semaphore.make(maxConcurrentResolutions)
        entries   <- Ref.make(Map.empty[String, CacheEntry])
        cachingResolver = new CachingResolver(resolver, ttl, unknownHostTtl, maxCount, expireAction, semaphore, entries)
        _ <- cachingResolver.refreshAndCleanup().scheduleFork(Schedule.spaced(refreshRate))
      } yield cachingResolver
  }
}
