package zio.http

import java.time.Duration

import zio.blocks.schema.Schema

/**
 * Connection-pool sizing for an HTTP client.
 *
 * Idle-reclamation contract: pooled connections idle longer than
 * [[idleTimeout]] are eligible for background reclamation by the driver.
 * Reclamation is best-effort - a driver may reclaim earlier under memory
 * pressure but must never hand out a connection idle longer than
 * [[idleTimeout]] without a liveness check. [[queueSize]] bounds how many
 * borrowers may wait for a connection; drivers fail fast (rather than growing
 * unbounded queues) once it is exceeded.
 *
 * All sizes fail fast with [[IllegalArgumentException]] when incoherent, so
 * misconfiguration surfaces at construction time instead of as a hung pool at
 * runtime.
 */
final case class PoolConfig(
  maxPerHost: Int = PoolConfig.DefaultMaxPerHost,
  maxTotal: Int = PoolConfig.DefaultMaxTotal,
  idleTimeout: Duration = Duration.ofSeconds(60),
  queueSize: Int = PoolConfig.DefaultQueueSize,
) {
  if (maxPerHost <= 0)
    throw new IllegalArgumentException(s"PoolConfig.maxPerHost must be > 0, got $maxPerHost")
  if (maxTotal <= 0)
    throw new IllegalArgumentException(s"PoolConfig.maxTotal must be > 0, got $maxTotal")
  if (maxTotal < maxPerHost)
    throw new IllegalArgumentException(
      s"PoolConfig.maxTotal ($maxTotal) must be >= maxPerHost ($maxPerHost)",
    )
  if (queueSize < 0)
    throw new IllegalArgumentException(s"PoolConfig.queueSize must be >= 0, got $queueSize")
  if (idleTimeout.isNegative || idleTimeout.isZero)
    throw new IllegalArgumentException(s"PoolConfig.idleTimeout must be positive, got $idleTimeout")
}

object PoolConfig {
  val DefaultMaxPerHost: Int = 10
  val DefaultMaxTotal: Int   = 100
  val DefaultQueueSize: Int  = 1000

  implicit val schema: Schema[PoolConfig] = Schema.derived[PoolConfig]
}
