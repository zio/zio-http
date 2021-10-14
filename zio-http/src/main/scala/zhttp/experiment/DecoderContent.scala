package zhttp.experiment

import zhttp.http.Http

sealed trait Message[A, S]
object Message {
  case class LastContent[A, S](content: A, state: S) extends Message[A, S]
  case class Content[A, S](content: A, state: S)     extends Message[A, S]
}
case class DecoderContent[-R, +E, S, A, +B](decoder: Http[R, E, Message[A, S], (S, Option[B])])
