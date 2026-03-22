package zio.http.internal

/**
 * Pool of ByteArrayOutputStream instances to avoid allocation overhead.
 * Thread-safe implementation using synchronized access.
 */
private object ByteArrayOutputStreamPool {
  private val MaxPoolSize = 50
  private val pool        = new java.util.concurrent.ConcurrentLinkedQueue[java.io.ByteArrayOutputStream]()

  /**
   * Borrows a ByteArrayOutputStream from the pool. If the pool is empty,
   * creates a new instance.
   */
  private def borrow(): java.io.ByteArrayOutputStream = {
    val stream = pool.poll()
    if (stream != null) {
      stream.reset() // Clear any existing data
      stream
    } else {
      new java.io.ByteArrayOutputStream()
    }
  }

  /**
   * Returns a ByteArrayOutputStream to the pool for reuse. Only returns to pool
   * if under the maximum size limit.
   */
  private def returnToPool(stream: java.io.ByteArrayOutputStream): Unit = {
    if (pool.size() < MaxPoolSize) {
      stream.reset()
      pool.offer(stream)
      ()
    }
  }

  /**
   * Executes a function with a borrowed ByteArrayOutputStream and automatically
   * returns it to the pool afterwards.
   */
  def withStream[T](f: java.io.ByteArrayOutputStream => T): T = {
    val stream = borrow()
    try {
      f(stream)
    } finally {
      returnToPool(stream)
    }
  }
}
