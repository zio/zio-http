package zio.http.template2

/**
 * JavaScript code, as created by the `js` string interpolator or by `Js(code)`.
 * It holds the code as a string: `value` and `toString` return it. It can be
 * the value of an attribute, as in `Dom.attr("onclick") := js"save()"`, or the
 * content of a `script` element.
 */
sealed abstract case class Js(value: String) {
  override def toString: String = value

  /**
   * Returns a copy with `stripMargin` applied to the code: on each line,
   * leading blanks or control characters followed by `|` are removed.
   */
  def stripMargin: Js = Js(value.stripMargin)
}

/**
 * The constructor of [[Js]] values.
 */
object Js {

  /**
   * Creates a [[Js]] value that holds the code `value`, without checking it.
   */
  def apply(value: String): Js = new Js(value) {}
}
