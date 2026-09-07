package zio.http

import java.time.Duration

import zio.blocks.schema.Schema

/**
 * Per-stage deadlines for an HTTP client.
 *
 * Each stage is `Option[Duration]`: `None` means "inherit or disable" as
 * documented per field, `Some` overrides. Non-positive overrides fail fast
 * with [[IllegalArgumentException]] - a zero/negative deadline would either
 * fire immediately or never fire, both misleading.
 *
 * This reuses the existing `ClientConfig.connectTimeout` / `requestTimeout`
 * fields instead of duplicating them: [[connectTimeout]] / [[requestTimeout]]
 * default to `None` (inherit the top-level values), while [[streamTimeout]]
 * - which has no top-level legacy field - defaults to `None` (disabled).
 * Drivers must read timeouts through `ClientConfig.effective*` so the
 * precedence (override, else top-level, else disabled) lives in exactly one
 * place.
 */
final case class DeadlineConfig(
  connectTimeout: Option[Duration] = None,
  requestTimeout: Option[Duration] = None,
  streamTimeout: Option[Duration] = None,
) {
  connectTimeout.foreach { d =>
    if (d.isNegative || d.isZero)
      throw new IllegalArgumentException(s"DeadlineConfig.connectTimeout must be positive, got $d")
  }
  requestTimeout.foreach { d =>
    if (d.isNegative || d.isZero)
      throw new IllegalArgumentException(s"DeadlineConfig.requestTimeout must be positive, got $d")
  }
  streamTimeout.foreach { d =>
    if (d.isNegative || d.isZero)
      throw new IllegalArgumentException(s"DeadlineConfig.streamTimeout must be positive, got $d")
  }
}

object DeadlineConfig {
  implicit val schema: Schema[DeadlineConfig] = Schema.derived[DeadlineConfig]
}
