package zio.http

import zio.http.ResultType._

/**
 * Handles unexpected errors (defects) that escape from route handlers.
 *
 * When a handler throws an exception that is not a [[Halt]], the server
 * invokes [[handleDefect]] to produce a response or a deliberate [[Halt]].
 * This prevents raw exception leakage to clients.
 *
 * Register a custom handler via [[Server.routeDefectHandler]].
 */
trait DefectHandler {

  /**
   * Called when a route handler throws an unexpected [[Throwable]].
   *
   * @param request   the HTTP request being processed when the error occurred
   * @param throwable the unexpected error
 * @return a [[Response]] to send to the client, or a [[Halt]] to deliberately
 *         short-circuit request processing with a specific response
   */
  def handleDefect(request: Request, throwable: Throwable): Response | Halt
}

object DefectHandler {

  /**
   * Default defect handler: returns HTTP 500 Internal Server Error for all
   * unexpected errors.
   */
  val default: DefectHandler = new DefectHandler {
    def handleDefect(request: Request, throwable: Throwable): Response | Halt =
      Response(Status.InternalServerError)
  }
}
