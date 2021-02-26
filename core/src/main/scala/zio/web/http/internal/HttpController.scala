package zio.web.http.internal

import zio.{ UIO, UManaged, URIO, ZIO, ZManaged }
import zio.web.{ AnyF, Endpoint, Handlers }

final private[http] class HttpController[-R](private val handlers: HttpController.HandlersMap, env: R) {

  def handle(endpoint: HttpController.AnyEndpoint)(input: Any, params: Any): UIO[endpoint.Output] =
    handlers.get(endpoint) match {
      case None => ZIO.dieMessage("Uh oh! Handler for given endpoint not found.")
      case Some(handler) =>
        type HandlerF = (endpoint.Input, endpoint.Params) => URIO[R, endpoint.Output]

        handler
          .asInstanceOf[HandlerF](input.asInstanceOf[endpoint.Input], params.asInstanceOf[endpoint.Params])
          .provide(env)
    }
}

object HttpController {

  type AnyEndpoint = Endpoint[AnyF, _, _, _]
  type AnyHandler  = (_, _) => URIO[_, _]
  type AnyHandlers = Handlers[AnyF, _, _]
  type HandlersMap = Map[AnyEndpoint, AnyHandler]

  private def handlersMap(acc: HandlersMap, value: AnyHandlers): HandlersMap = value match {
    case Handlers.Empty => acc
    case Handlers.Cons(head, tail) =>
      val nextAcc = acc.updated(head.endpoint, head.handler)
      handlersMap(nextAcc, tail)
  }

  private[http] def make[M[_], R, Ids](handlers: Handlers[M, R, Ids], env: R): UManaged[HttpController[R]] = {
    type Actual   = Handlers[M, R, Ids]
    type Expected = Handlers[AnyF, R, Any]

    def cast(value: Actual): Expected = value.asInstanceOf[Expected]

    ZManaged.succeed(new HttpController[R](handlersMap(Map.empty, cast(handlers)), env))
  }
}
