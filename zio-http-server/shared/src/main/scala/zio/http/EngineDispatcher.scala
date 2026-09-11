package zio.http

import scala.annotation.experimental
import scala.collection.immutable.ListMap

import zio.blocks.context.Context
import zio.blocks.endpoint.{RouteTree, SegmentSubtree}
import zio.blocks.scope.Scope

/**
 * One shared application dispatcher for all protocol engines.
 *
 * Engines own their wire semantics (frames, flow control, connections) and hand
 * fully-decoded [[Request]] values to [[dispatch]]; routes never see frames.
 * The H1 engine (Todo 7) uses this directly; the H2 transport adopts it in Todo
 * 6.
 */
@experimental
final class EngineDispatcher[Ctx](
  routes: Routes[Ctx],
  context: Context[Ctx],
  defectHandler: DefectHandler,
) {
  private val routeTree: RouteTree[Route[Ctx]] =
    EngineDispatcher.buildRouteTree(routes)

  /** Dispatch one decoded request to the matching route. */
  def dispatch(request: Request): Response =
    routeTree.get(request.method, request.path) match {
      case Some(route) =>
        route.pattern.decode(request.method, request.path) match {
          case Right(vars) =>
            val openScope = Scope.global.open()
            try {
              toResponse(invokeHandler(route, request, vars, openScope.scope), request)
            } finally {
              openScope.close().orThrow()
            }
          case Left(_)     => Response.notFound
        }
      case None        => Response.notFound
    }

  private def invokeHandler(route: Route[Ctx], request: Request, vars: Any, scope: Scope): Any =
    try route.handler.handle(request, context, vars, scope)
    catch {
      case throwable: Throwable =>
        try defectHandler.handleDefect(request, throwable)
        catch {
          case _: Throwable => Response.internalServerError
        }
    }

  private def toResponse(result: Any, request: Request): Response =
    result match {
      case response: Response       => response
      case halt: Halt               => halt.response
      case Left(response: Response) => response
      case Right(halt: Halt)        => halt.response
      case other                    =>
        try {
          toResponse(
            defectHandler.handleDefect(request, new IllegalStateException("Unexpected handler result: " + other)),
            request,
          )
        } catch {
          case _: Throwable => Response.internalServerError
        }
    }
}

@experimental
object EngineDispatcher {
  private def buildRouteTree[Ctx](routes: Routes[Ctx]): RouteTree[Route[Ctx]] =
    routes.routes.foldLeft(RouteTree.empty[Route[Ctx]]) { (tree, route) =>
      val alternatives = route.pattern.alternatives
      if (alternatives.nonEmpty) tree.add(route.pattern, route)
      else tree.merge(rootRouteTree(route))
    }

  private def rootRouteTree[Ctx](route: Route[Ctx]): RouteTree[Route[Ctx]] = {
    val rootSubtree = SegmentSubtree[Route[Ctx]](Map.empty, ListMap.empty, Some(route))
    RouteTree(Map(route.pattern.method -> rootSubtree))
  }
}
