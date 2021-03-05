package zhttp.http

import zio.stream.ZStream

/**
 * Content holder for Requests and Responses
 */
sealed trait HttpContent[-R, +A] extends Product with Serializable

object HttpContent {
  case object Empty                                            extends HttpContent[Any, Nothing]
  final case class Complete[A](data: A)                        extends HttpContent[Any, A]
  final case class Chunked[R, A](data: ZStream[R, Nothing, A]) extends HttpContent[R, A]
}
