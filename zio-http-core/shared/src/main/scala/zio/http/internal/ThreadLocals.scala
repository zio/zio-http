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

}
