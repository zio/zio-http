package zhttp.http.headers

import zhttp.http.Headers

/**
 * A trait that provides a ton of powerful operators when extended. Any type that extends HeaderExtension needs to
 * implement the two methods viz. `getHeaders` and `updateHeaders`. All other operators are built on top these two
 * methods.
 */
private[zhttp] trait HeaderExtension[+A] extends HeaderModifier[A] with HeaderGetters[A] with HeaderChecks[A] {
  self: A =>

  /**
   * Returns the Headers object on the current type A
   */
  def getHeaders: Headers

  /**
   * Updates the current Headers with new one, using the provided update function passed.
   */
  def updateHeaders(update: Headers => Headers): A
}
