package zio.http.internal

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A concurrent-safe pool of StringBuilder instances to avoid repeated
 * allocations. StringBuilders are created with an initial capacity of 256
 * characters.
 */
private[http] object StringBuilderPool {
  private val pool            = new ConcurrentLinkedQueue[StringBuilder]()
  private val DefaultCapacity = 256
  private val MaxPoolSize     = 25

  /**
   * Borrows a StringBuilder from the pool. If none are available, creates a new
   * one. The StringBuilder is cleared and ready for use.
   */
  private def borrow(): StringBuilder = {
    val sb = pool.poll()
    if (sb != null) {
      sb.setLength(0)
      sb
    } else {
      new StringBuilder(DefaultCapacity)
    }
  }

  /**
   * Returns a StringBuilder to the pool for reuse. Only returns StringBuilders
   * that haven't grown too large to avoid memory bloat.
   */
  private def release(sb: StringBuilder): Unit = {
    // Only pool StringBuilders that are not too large
    if (sb.length <= DefaultCapacity * 4 && pool.size() < MaxPoolSize) {
      pool.offer(sb)
      ()
    }
  }

  /**
   * Executes a function with a borrowed StringBuilder, automatically returning
   * it to the pool.
   */
  def withStringBuilder[T](f: StringBuilder => T): T = {
    val sb = borrow()
    try {
      f(sb)
    } finally {
      release(sb)
    }
  }
}
