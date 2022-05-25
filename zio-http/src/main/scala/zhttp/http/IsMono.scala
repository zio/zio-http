package zhttp.http

import scala.annotation.implicitNotFound

/**
 * IsMono is a type-constraint that is used by the middleware api for allowing
 * some operators only when the following condition is met.
 *
 * Condition: Since a middleware takes in an Http and returns a new Http,
 * IsMono, makes sure that the type parameters of the incoming Http and the ones
 * for the outgoing Http is the same.
 *
 * For Eg: IsMono will be defined for a middleware that looks as follows
 * ```
 * val mid: Middleware[Any, Nothing, Request, Response, Request, Response]
 * ```
 *
 * This is because both the middleware is defined from (Request, Response) =>
 * (Request, Response). Consider another example:
 *
 * ```
 * val mid: Middleware[Any, Nothing, Request, Response, UserRequest, UserResponse]
 * ```
 *
 * In this case, the incoming and outgoing types are different viz. (Request,
 * Response) => (UserRequest, UserResponse), hence there is no IsMono defined
 * for such middlewares.
 */
@implicitNotFound(
  "This operation is only valid if the incoming and outgoing type of Http are same.",
)
sealed trait IsMono[-AIn, +BIn, +AOut, -BOut] {}

object IsMono extends IsMono[Any, Nothing, Nothing, Any] {
  implicit def mono[AIn, BIn, AOut, BOut](implicit a: AIn =:= AOut, b: BIn =:= BOut): IsMono[AIn, BIn, AOut, BOut] =
    IsMono
}
