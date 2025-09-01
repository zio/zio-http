package zio.http.template2

sealed abstract case class Js(value: String) {
  override def toString: String = value
}

object Js {
  private[template2] def apply(value: String): Js = new Js(value) {}
}
