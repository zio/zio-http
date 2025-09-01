package zio.http.template2

sealed abstract case class Css(value: String) {
  override def toString: String = value
}

object Css {
  private[template2] def apply(value: String): Css = new Css(value) {}
}
