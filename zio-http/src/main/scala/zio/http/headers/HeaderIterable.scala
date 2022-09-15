package zio.http.headers

import zio.http.Headers.Header

/**
 * A trait that provides a ton of powerful operators when extended. Any type
 * that extends HeaderExtension needs to implement the two methods viz.
 * `getHeaders` and `updateHeaders`. All other operators are built on top these
 * two methods.
 */
private[zio] trait HeaderIterable extends Iterable[Header] {}
