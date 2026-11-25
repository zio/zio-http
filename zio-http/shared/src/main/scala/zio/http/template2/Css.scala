package zio.http.template2

/**
 * CSS code, as created by the `css` string interpolator or by `Css(code)`. It holds the code as a string: `value` and `toString` return it. A `style` element takes it as content, for example `style(css"p { margin: 0 }")`.
 */
sealed abstract case class Css(value: String) {
  override def toString: String = value

  /**
   * Returns a copy with `stripMargin` applied to the code: on each line, leading blanks or control characters followed by `|` are removed.
   */
  def stripMargin: Css          = Css(value.stripMargin)
}

/**
 * Creates a [[Css]] value from a string with `Css(code)`, without checking the code.
 */
object Css { def apply(value: String): Css = new Css(value) {} }
