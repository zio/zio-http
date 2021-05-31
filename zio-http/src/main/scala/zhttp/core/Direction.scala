package zhttp.core

/**
 * HBuf can be created at two places viz. — From inside the ZIO Http service of the user-land application code.
 * The HBuf that's created by the service will always have direction set to `In` depicting "incoming" buffer.
 * Similarly content that is created by user-land code is marked as `Out` depicting that it is going out of the application.
 */
object Direction {
  type In
  type Out
  type Unknown

  trait Flip[A, B]
  object Flip {
    implicit object in  extends Flip[Direction.In, Direction.Out]
    implicit object out extends Flip[Direction.Out, Direction.In]
  }
}
