package zio.http

import scala.annotation.implicitNotFound

import zio.Zippable

@implicitNotFound("""
Your request handler is required to accept both parameters ${I}, as well as the incoming [[zio.http.Request]].
This is true even if you wish to ignore some parameters or the request itself. Try to add missing parameters 
until you no longer receive this error message. If all else fails, you can construct a handler manually using 
the constructors in the companion object of [[zio.http.Handler]] using the precise type.""")
final class RequestHandlerInput[A, I](val zippable: Zippable.Out[A, Request, I])
object RequestHandlerInput {
  implicit def apply[A, I](implicit zippable: Zippable.Out[A, Request, I]): RequestHandlerInput[A, I] =
    new RequestHandlerInput(zippable)
}
