package zio.http.internal

private[http] object ThreadLocals {
  // We need some java api and the Scala StringBuilder has no public access to the underlying
  // Java StringBuilder in 2.12
  // Since this is only used internally that should be fine.
  val StringBuilder: ThreadLocal[java.lang.StringBuilder] =
    new ThreadLocal[java.lang.StringBuilder] {
      override def initialValue(): java.lang.StringBuilder = null.asInstanceOf[java.lang.StringBuilder]
    }

  def stringBuilder: java.lang.StringBuilder = {
    val sb = StringBuilder.get()
    if (sb == null) {
      val newSb = new java.lang.StringBuilder(1024)
      StringBuilder.set(newSb)
      newSb
    } else {
      sb.setLength(0)
      sb
    }
  }

  private val Deque: ThreadLocal[java.util.ArrayDeque[Any]] =
    new ThreadLocal[java.util.ArrayDeque[Any]] {
      override def initialValue(): java.util.ArrayDeque[Any] = null.asInstanceOf[java.util.ArrayDeque[Any]]
    }

  def deque: java.util.ArrayDeque[Any] = {
    val d = Deque.get()
    if (d == null) {
      val newD = new java.util.ArrayDeque[Any](8)
      Deque.set(newD)
      newD
    } else {
      d.clear()
      d
    }
  }

}
