/*
 * Copyright 2023 the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * An HTTP application is a collection of routes, all of whose errors have been
 * handled through conversion into HTTP responses.
 *
 * HTTP applications can be installed into a [[zio.http.Server]], which is
 * capable of using them to serve requests.
 */
@deprecated("Use Routes instead. Will be removed in the next release.")
final case class HttpApp[-Env](routes: Routes[Env, Response])
    extends PartialFunction[Request, ZIO[Env, Response, Response]] { self =>
  private var _tree: HttpApp.Tree[_] = null.asInstanceOf[HttpApp.Tree[_]]

  /**
   * Applies the specified route aspect to every route in the HTTP application.
   */
  def @@[Env1 <: Env](aspect: Middleware[Env1]): HttpApp[Env1] =
    copy(routes = routes @@ aspect)

  /**
   * Combines this HTTP application with the specified HTTP application. In case
   * of route conflicts, the routes in this HTTP application take precedence
   * over the routes in the specified HTTP application.
   */
  def ++[Env1 <: Env](that: HttpApp[Env1]): HttpApp[Env1] =
    copy(routes = routes ++ that.routes)

  /**
   * Executes the HTTP application with the specified request input, returning
   * an effect that will either succeed or fail with a Response.
   */
  def apply(request: Request): ZIO[Env, Response, Response] = runZIO(request)

  /**
   * Checks to see if the HTTP application may be defined at the specified
   * request input. Note that it is still possible for an HTTP application to
   * return a 404 Not Found response, which cannot be detected by this method.
   * This method only checks for the presence of a handler that handles the
   * method and path of the specified request.
   */
  def isDefinedAt(request: Request): Boolean =
    tree(Trace.empty).get(request.method, request.path).nonEmpty

  /**
   * Provides the specified environment to the HTTP application, returning a new
   * HTTP application that has no environmental requirements.
   */
  def provideEnvironment(env: ZEnvironment[Env]): HttpApp[Any] =
    copy(routes = routes.provideEnvironment(env))

  def run(
    method: Method = Method.GET,
    path: Path = Path.root,
    headers: Headers = Headers.empty,
    body: Body = Body.empty,
  ): ZIO[Env, Nothing, Response] =
    runZIO(Request(method = method, url = URL.root.path(path), headers = headers, body = body))

  /**
   * An alias for `apply`.
   */
  def runZIO(request: Request): ZIO[Env, Nothing, Response] =
    toHandler(request)

  /**
   * Returns a new HTTP application whose requests will be timed out after the
   * specified duration elapses.
   */
  def timeout(duration: Duration)(implicit trace: Trace): HttpApp[Env] =
    self @@ Middleware.timeout(duration)

  /**
   * Converts the HTTP application into a request handler.
   */
  val toHandler: Handler[Env, Nothing, Request, Response] = {
    implicit val trace: Trace = Trace.empty
    Handler
      .fromFunctionHandler[Request] { req =>
        val chunk = tree.get(req.method, req.path)

        if (chunk.length == 0) Handler.notFound
        else if (chunk.length == 1) chunk(0)
        else {
          // TODO: Support precomputed fallback among all chunk elements:
          chunk.tail.foldLeft(chunk.head) { (acc, h) =>
            acc.catchAll { response =>
              if (response.status == Status.NotFound) h
              else Handler.fail(response)
            }
          }
        }
      }
      .merge
  }

  /**
   * Accesses the underlying tree that provides fast dispatch to handlers.
   */
  def tree(implicit trace: Trace): HttpApp.Tree[Env] = {
    if (_tree eq null) {
      _tree = HttpApp.Tree.fromRoutes(routes)
    }

    _tree.asInstanceOf[HttpApp.Tree[Env]]
  }
}
object HttpApp                                                     {

  /**
   * An HTTP application that does not handle any routes.
   */
  @deprecated("Use Routes.empty instead. Will be removed in the next release.")
  val empty: HttpApp[Any] = HttpApp(Routes.empty)

  private[http] final case class Tree[-Env](tree: RoutePattern.Tree[RequestHandler[Env, Response]]) { self =>
    final def ++[Env1 <: Env](that: Tree[Env1]): Tree[Env1] =
      Tree(self.tree ++ that.tree)

    final def add[Env1 <: Env](route: Route[Env1, Response])(implicit trace: Trace): Tree[Env1] =
      Tree(self.tree.add(route.routePattern, route.toHandler))

    final def addAll[Env1 <: Env](routes: Iterable[Route[Env1, Response]])(implicit trace: Trace): Tree[Env1] =
      Tree(self.tree.addAll(routes.map(r => (r.routePattern, r.toHandler))))

    final def get(method: Method, path: Path): Chunk[RequestHandler[Env, Response]] =
      tree.get(method, path)
  }
  private[http] object Tree                                                                         {
    val empty: Tree[Any] = Tree(RoutePattern.Tree.empty)

    def fromRoutes[Env](routes: Routes[Env, Response])(implicit trace: Trace): Tree[Env] =
      empty.addAll(routes.routes)
  }
}
