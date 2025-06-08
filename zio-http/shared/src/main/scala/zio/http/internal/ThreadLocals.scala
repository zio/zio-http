package zio.http.internal

private[http] object ThreadLocals {
  val StringBuilder: ThreadLocal[StringBuilder] =
    new ThreadLocal[StringBuilder] {
      override def initialValue(): StringBuilder = null.asInstanceOf[StringBuilder]
    }

  def stringBuilder: StringBuilder = {
    val sb = StringBuilder.get()
    if (sb == null) {
      val newSb = new StringBuilder(1024)
      StringBuilder.set(newSb)
      newSb
    } else {
      sb.clear()
      sb
    }
  }

}
