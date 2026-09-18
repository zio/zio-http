package zio.http

import java.io.IOException

/**
 * Thrown when a pooled response body dies mid-transfer.
 *
 * The pooled leg ([[PooledLoomH2Client]]) streams bodies lazily through
 * `Body.toArray`, which treats a mid-pull `java.io.IOException` as truncation
 * and returns collected-so-far silently. A bare `IOException` there would
 * therefore come back as an empty body and the caller could not tell a dead
 * server (or a broken first `WINDOW_UPDATE`) from an empty response. As an
 * unchecked failure carrying the transport cause, the trip surfaces loudly on
 * every drain instead; catch it explicitly.
 *
 * Only checked transport failures are wrapped: deadline expiries keep surfacing
 * as [[java.util.concurrent.TimeoutException]], cancels as
 * [[java.util.concurrent.CancellationException]], and the body cap as
 * [[ResponseBodyTooLarge]] — all unchecked already and all passing through
 * untouched.
 */
final class H2BodyStreamFailure(val streamId: Int, val ioCause: IOException)
    extends RuntimeException(
      s"HTTP/2 response body failed mid-transfer (stream $streamId): $ioCause",
      ioCause,
    )

object H2BodyStreamFailure {
  def apply(streamId: Int, cause: IOException): H2BodyStreamFailure =
    new H2BodyStreamFailure(streamId, cause)

  def unapply(error: H2BodyStreamFailure): Option[(Int, IOException)] =
    if (error == null) None else Some((error.streamId, error.ioCause))
}
