package zio.http.template2

sealed abstract case class Css(value: String) {
  override def toString: String = value
  def stripMargin: Css          = Css(value.stripMargin)
}

object Css { def apply(value: String): Css = new Css(value) {} }
