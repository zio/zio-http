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

import java.io.File

import zio._

import zio.http.Mode.Dev
import zio.http.Routes.ApplyContextAspect
import zio.http.codec.PathCodec

/**
 * An HTTP application is a collection of routes, all of whose errors have been
 * handled through conversion into HTTP responses.
 *
 * HTTP applications can be installed into a [[zio.http.Server]], which is
 * capable of using them to serve requests.
 */
final case class Routes[-Env, +Err](routes: Chunk[zio.http.Route[Env, Err]]) { self =>
  private var _tree: Routes.Tree[_] =
    null.asInstanceOf[Routes.Tree[_]]

  private var _handler: Handler[Any, Nothing, Request, Response] =
    null.asInstanceOf[Handler[Any, Nothing, Request, Response]]

  var notFound: Handler[Any, Nothing, Request, Response] =
    Handler.notFound(self)

  def @@[Env1 <: Env](aspect: Middleware[Env1]): Routes[Env1, Err] =
    aspect(self)

  def @@[Env0](aspect: HandlerAspect[Env0, Unit]): Routes[Env with Env0, Err] =
    aspect(self)

  def @@[Env0, Ctx <: Env](
    aspect: HandlerAspect[Env0, Ctx],
  )(implicit tag: Tag[Ctx]): Routes[Env0, Err] =
    self.transform(_ @@ aspect)

  def @@[Env0]: ApplyContextAspect[Env, Err, Env0] =
    new ApplyContextAspect[Env, Err, Env0](self)

  /**
   * Combines this Routes with the specified Routes. In case of route conflicts,
   * the new Routes take precedence over the current Routes.
   */
  def ++[Env1 <: Env, Err1 >: Err](that: Routes[Env1, Err1]): Routes[Env1, Err1] =
    withRoutes(routes = routes ++ that.routes)

  /**
   * Prepend the specified route.
   */
  def +:[Env1 <: Env, Err1 >: Err](route: zio.http.Route[Env1, Err1]): Routes[Env1, Err1] =
    withRoutes(routes = route +: routes)

  /**
   * Appends the specified route.
   */
  def :+[Env1 <: Env, Err1 >: Err](route: zio.http.Route[Env1, Err1]): Routes[Env1, Err1] =
    withRoutes(routes = routes :+ route)

  /**
   * Executes the HTTP application with the specified request input, returning
   * an effect that will either succeed or fail with a Response.
   */
  def apply(request: Request)(implicit ev: Err <:< Response): ZIO[Scope & Env, Response, Response] =
    runZIO(request)

  /**
   * Handles all typed errors in the routes by converting them into responses.
   * This method can be used to convert routes that do not handle their errors
   * into ones that do handle their errors.
   */
  def handleError(f: Err => Response)(implicit trace: Trace): Routes[Env, Nothing] =
    withRoutes(routes.map(_.handleError(f)))

  def handleErrorZIO[Env1 <: Env](f: Err => ZIO[Env1, Nothing, Response])(implicit
    trace: Trace,
  ): Routes[Env1, Nothing] =
    withRoutes(routes.map(_.handleErrorZIO(f)))

  /**
   * Handles all typed errors, as well as all non-recoverable errors, by
   * converting them into responses. This method can be used to convert routes
   * that do not handle their errors into ones that do handle their errors.
   */
  def handleErrorCause(f: Cause[Err] => Response)(implicit trace: Trace): Routes[Env, Nothing] =
    withRoutes(routes.map(_.handleErrorCause(f)))

  /**
   * Handles all typed errors, as well as all non-recoverable errors, by
   * converting them into a ZIO effect that produces the response. This method
   * can be used to convert routes that do not handle their errors into ones
   * that do handle their errors.
   */
  def handleErrorCauseZIO(f: Cause[Err] => ZIO[Any, Nothing, Response])(implicit trace: Trace): Routes[Env, Nothing] =
    withRoutes(routes.map(_.handleErrorCauseZIO(f)))

  /**
   * Effectfully peeks at the unhandled failure of this Routes.
   */
  def tapErrorZIO[Err1 >: Err](f: Err => ZIO[Any, Err1, Any])(implicit trace: Trace): Routes[Env, Err1] =
    withRoutes(routes.map(_.tapErrorZIO(f)))

  /**
   * Effectfully peeks at the unhandled failure cause of this Routes.
   */
  def tapErrorCauseZIO[Err1 >: Err](f: Cause[Err] => ZIO[Any, Err1, Any])(implicit trace: Trace): Routes[Env, Err1] =
    withRoutes(routes.map(_.tapErrorCauseZIO(f)))

  /**
   * Allows the transformation of the Err type through an Effectful program
   * allowing one to build up Routes in Stages delegates to the Route.
   */
  def mapErrorZIO[Err1](fxn: Err => ZIO[Any, Err1, Response])(implicit trace: Trace): Routes[Env, Err1] =
    withRoutes(routes.map(_.mapErrorZIO(fxn)))

  /**
   * Allows the transformation of the Err type through a function allowing one
   * to build up Routes in Stages delegates to the Route.
   */
  def mapError[Err1](fxn: Err => Err1): Routes[Env, Err1] =
    withRoutes(routes.map(_.mapError(fxn)))

  def nest(prefix: PathCodec[Unit])(implicit trace: Trace): Routes[Env, Err] =
    withRoutes(self.routes.map(_.nest(prefix)))

  /**
   * Handles all typed errors in the routes by converting them into responses,
   * taking into account the request that caused the error. This method can be
   * used to convert routes that do not handle their errors into ones that do
   * handle their errors.
   */
  def handleErrorRequest(f: (Err, Request) => Response)(implicit trace: Trace): Routes[Env, Nothing] =
    withRoutes(routes.map(_.handleErrorRequest(f)))

  /**
   * Handles all typed errors in the routes by converting them into responses,
   * taking into account the request that caused the error. This method can be
   * used to convert routes that do not handle their errors into ones that do
   * handle their errors.
   */
  def handleErrorRequestCause(f: (Request, Cause[Err]) => Response)(implicit trace: Trace): Routes[Env, Nothing] =
    withRoutes(routes.map(_.handleErrorRequestCause(f)))

  /**
   * Handles all typed errors, as well as all non-recoverable errors, by
   * converting them into a ZIO effect that produces the response, taking into
   * account the request that caused the error. This method can be used to
   * convert routes that do not handle their errors into ones that do handle
   * their errors.
   */
  def handleErrorRequestCauseZIO(f: (Request, Cause[Err]) => ZIO[Any, Nothing, Response])(implicit
    trace: Trace,
  ): Routes[Env, Nothing] =
    withRoutes(routes.map(_.handleErrorRequestCauseZIO(f)))

  /**
   * Checks to see if the HTTP application may be defined at the specified
   * request input. Note that it is still possible for an HTTP application to
   * return a 404 Not Found response, which cannot be detected by this method.
   * This method only checks for the presence of a handler that handles the
   * method and path of the specified request.
   */
  def isDefinedAt(request: Request)(implicit ev: Err <:< Response): Boolean = {
    implicit val trace: Trace = Trace.empty
    tree().get(request.method, request.path) != null
  }

  def provide[Env1 <: Env](env: Env1)(implicit tag: Tag[Env1]): Routes[Any, Err] =
    provideEnvironment(ZEnvironment(env))

  /**
   * Provides the specified environment to the HTTP application, returning a new
   * HTTP application that has no environmental requirements.
   */
  def provideEnvironment(env: ZEnvironment[Env]): Routes[Any, Err] =
    withRoutes(routes = routes.map(_.provideEnvironment(env)))

  def run(request: Request)(implicit trace: Trace): ZIO[Scope & Env, Either[Err, Response], Response] = {

    class RouteFailure[+Err0](val err: Cause[Err0]) extends Throwable(null, null, true, false) {
      override def getMessage: String = err.unified.headOption.fold("<unknown>")(_.message)

      override def getStackTrace(): Array[StackTraceElement] =
        err.unified.headOption.fold[Chunk[StackTraceElement]](Chunk.empty)(_.trace).toArray

      override def getCause(): Throwable =
        err.find { case Cause.Die(throwable, _) => throwable }
          .orElse(err.find { case Cause.Fail(value: Throwable, _) => value })
          .orNull

      override def toString =
        err.prettyPrint
    }
    var routeFailure: RouteFailure[Err] = null

    handleErrorCauseZIO { cause =>
      routeFailure = new RouteFailure(cause)
      ZIO.refailCause(Cause.die(routeFailure))
    }
      .apply(request)
      .mapErrorCause {
        case Cause.Die(value: RouteFailure[_], _) if value == routeFailure => routeFailure.err.map(Left(_))
        case cause                                                         => cause.map(Right(_))
      }
  }

  private[http] def routesNoAny: Chunk[Route[Env, Err]] =
    routes.filterNot(_.routePattern == RoutePattern.any)

  def run(
    method: Method = Method.GET,
    path: Path = Path.root,
    headers: Headers = Headers.empty,
    body: Body = Body.empty,
  )(implicit ev: Err <:< Response): ZIO[Scope & Env, Nothing, Response] =
    runZIO(Request(method = method, url = URL.root.path(path), headers = headers, body = body))

  /**
   * An alias for `apply`.
   */
  def runZIO(request: Request)(implicit ev: Err <:< Response): ZIO[Scope & Env, Nothing, Response] =
    toHandler(ev)(request)

  /**
   * Returns new routes that automatically translate all failures into
   * responses, using best-effort heuristics to determine the appropriate HTTP
   * status code, and attaching error details using the HTTP header `Warning`.
   */
  def sandbox(implicit trace: Trace): Routes[Env, Nothing] =
    withRoutes(routes.map(_.sandbox))

  /**
   * A shortcut for `Server.install(routes) *> ZIO.never`
   */
  def serve[Env1 <: Env](implicit
    ev: Err <:< Response,
    trace: Trace,
    tag: EnvironmentTag[Env1],
  ): URIO[Env1 with Server, Nothing] = {
    Server.serveRoutes[Env1](self.handleError(_.asInstanceOf[Response]))
  }

  /**
   * Returns a new HTTP application whose requests will be timed out after the
   * specified duration elapses.
   */
  def timeout(duration: Duration)(implicit trace: Trace): Routes[Env, Err] =
    self @@ Middleware.timeout(duration)

  /**
   * Converts the HTTP application into a request handler.
   */
  def toHandler(implicit ev: Err <:< Response): Handler[Env, Nothing, Request, Response] =
    toHandler(false)

  /**
   * Converts the HTTP application into a request handler with optional
   * auto-generation of HEAD routes.
   */
  def toHandler(
    autoGenerateHeadRoutes: Boolean,
  )(implicit ev: Err <:< Response): Handler[Env, Nothing, Request, Response] = {
    if (_handler eq null) {
      _handler = {
        implicit val trace: Trace = Trace.empty
        val (unique, duplicates)  = uniqueRoutes
        val tree                  = Routes(unique).tree[Env](autoGenerateHeadRoutes)
        val h                     = Handler
          .fromFunctionZIO[Request] { req =>
            val handler = tree.get(req.method, req.path)
            if (handler eq null) {
              notFound(req)
            } else {
              // For auto-generated HEAD routes, we need to call the handler with a GET request
              // because the handler was originally designed for GET
              val requestForHandler = if (req.method == Method.HEAD && autoGenerateHeadRoutes) {
                req.copy(method = Method.GET)
              } else {
                req
              }

              handler(requestForHandler).foldZIO(
                error => ZIO.succeed(error), // Error channel contains Response
                response => {
                  // ALWAYS strip body for HEAD requests (HTTP/1.1 spec requirement)
                  // This applies to ALL routes including ANY routes
                  val finalResponse = if (req.method == Method.HEAD) {
                    val contentLength = response.body.knownContentLength
                    val emptyResponse = response.copy(body = Body.empty)
                    contentLength match {
                      case Some(length) if !response.headers.contains(Header.ContentLength.name) =>
                        emptyResponse.addHeader(Header.ContentLength(length))
                      case _                                                                     => emptyResponse
                    }
                  } else if (autoGenerateHeadRoutes && req.method == Method.GET) {
                    // Add Content-Length to GET responses when autoGenerateHeadRoutes is enabled
                    // so that GET and HEAD have matching headers
                    response.body.knownContentLength match {
                      case Some(length) if !response.headers.contains(Header.ContentLength.name) =>
                        response.addHeader(Header.ContentLength(length))
                      case _                                                                     => response
                    }
                  } else {
                    response
                  }
                  ZIO.succeed(finalResponse)
                },
              )
            }
          }
          .asInstanceOf[Handler[Any, Nothing, Request, Response]]
        if (duplicates.nonEmpty) {
          val duplicateRoutes       = duplicates.map(_.routePattern)
          val duplicateRoutesString = duplicateRoutes.mkString("\n")
          var message               =
            s"Duplicate routes detected:\n$duplicateRoutesString\nThe last route of each path will be used."
          Handler
            .fromZIO(
              if (message != null)
                ZIO.suspendSucceed {
                  val msg = message
                  message = null

                  ZIO.logWarning(msg).as(h)
                }
              else Exit.succeed(h),
            )
            .flatten
        } else {
          h
        }
      }.asInstanceOf[Handler[Any, Nothing, Request, Response]]
    }
    _handler.asInstanceOf[Handler[Env, Nothing, Request, Response]]

  }

  /**
   * Returns new Routes whose handlers are transformed by the specified
   * function.
   */
  def transform[Env1](
    f: Handler[Env, Response, Request, Response] => Handler[Env1, Response, Request, Response],
  ): Routes[Env1, Err] = {
    val routes0 = new Routes(routes.map(_.transform(f)))
    routes0.notFound = f(notFound).asInstanceOf[Handler[Any, Nothing, Request, Response]]
    routes0
  }

  /**
   * Accesses the underlying tree that provides fast dispatch to handlers.
   */
  def tree[Env1 <: Env](
    autoGenerateHeadRoutes: Boolean = false,
  )(implicit trace: Trace, ev: Err <:< Response): Routes.Tree[Env1] = {
    if (_tree eq null) {
      val baseTree = Routes.Tree.fromRoutes(routes.asInstanceOf[Chunk[Route[Env1, Response]]])
      _tree = if (autoGenerateHeadRoutes) baseTree.withAutoGeneratedHeadRoutes else baseTree
    }
    _tree.asInstanceOf[Routes.Tree[Env1]]
  }

  private[http] def uniqueRoutes: (Chunk[Route[Env, Err]], Chunk[Route[Env, Err]]) = {
    val (unique, duplicates) = routes.reverse.foldLeft((Chunk.empty[Route[Env, Err]], Chunk.empty[Route[Env, Err]])) {
      case ((unique, duplicates), route) =>
        if (unique.exists(_.routePattern.structureEquals(route.routePattern))) (unique, duplicates :+ route)
        else (unique :+ route, duplicates)
    }
    (unique, duplicates)
  }

  private[http] def withRoutes[Env1, Err1](routes: Chunk[Route[Env1, Err1]]): Routes[Env1, Err1] = {
    val routes0 = copy(routes = routes)
    routes0.notFound = notFound
    routes0
  }
}

object Routes extends RoutesCompanionVersionSpecific {

  /**
   * An HTTP application that does not handle any routes.
   */
  val empty: Routes[Any, Nothing] = Routes(Chunk.empty)

  def apply[Env, Err](route: Route[Env, Err], routes: Route[Env, Err]*): Routes[Env, Err] =
    Routes(Chunk.fromIterable(route +: routes))

  def fromIterable[Env, Err](routes: Iterable[Route[Env, Err]]): Routes[Env, Err] =
    Routes(Chunk.fromIterable(routes))

  def singleton[Env, Err](h: Handler[Env, Err, (Path, Request), Response])(implicit trace: Trace): Routes[Env, Err] =
    Routes(Route.route(RoutePattern.any)(h))

  /**
   * Creates routes for serving static files from the directory `docRoot` at the
   * url path `path`.
   *
   * Example: `Routes.serveDirectory(Path.empty / "assets", new
   * File("/some/local/path"))`
   *
   * With this routes in place, a request to
   * `https://www.domain.com/assets/folder/file1.jpg` would serve the local file
   * `/some/local/path/folder/file1.jpg`.
   */
  def serveDirectory(path: Path, docRoot: File)(implicit trace: Trace): Routes[Any, Nothing] =
    empty @@ Middleware.serveDirectory(path, docRoot)

  /**
   * Creates routes for serving static files at URL path `path` from resources
   * with the given `resourcePrefix`.
   *
   * Example: `Routes.serveResources(Path.empty / "assets", "webapp")`
   *
   * With this routes in place, a request to
   * `https://www.domain.com/assets/folder/file1.jpg` would serve the file
   * `src/main/resources/webapp/folder/file1.jpg`. Note how the URL path is
   * removed and the resourcePrefix prepended.
   *
   * Most build systems support resources in the `src/main/resources` directory.
   * In the above example, the file `src/main/resources/webapp/folder/file1.jpg`
   * would be served.
   *
   * The `resourcePrefix` defaults to `"public"`. To prevent insecure sharing of
   * resource files, `resourcePrefix` must start with a `/` followed by at least
   * 1
   * [[java.lang.Character.isJavaIdentifierStart(x\$1:Char)* valid java identifier character]].
   * The `/` will be prepended if it is not present.
   */
  def serveResources(path: Path, resourcePrefix: String = "public")(implicit trace: Trace): Routes[Any, Nothing] =
    empty @@ Middleware.serveResources(path, resourcePrefix)

  private[http] final case class Tree[Env](tree: RoutePattern.Tree[Env]) { self =>
    final def ++[Env1 <: Env](that: Tree[Env1]): Tree[Env1] =
      Tree(self.tree.asInstanceOf[RoutePattern.Tree[Env1]] ++ that.tree)

    final def add[Env1 <: Env](route: Route[Env1, Response])(implicit trace: Trace): Tree[Env1] =
      Tree(
        self.tree
          .asInstanceOf[RoutePattern.Tree[Env1]]
          .addAll(route.routePattern.alternatives.map(alt => (alt, route.toHandler))),
      )

    final def addAll[Env1 <: Env](routes: Iterable[Route[Env1, Response]])(implicit trace: Trace): Tree[Env1] =
      // only change to flatMap when Scala 2.12 is dropped
      Tree(
        self.tree
          .asInstanceOf[RoutePattern.Tree[Env1]]
          .addAll(routes.map(r => r.routePattern.alternatives.map(alt => (alt, r.toHandler))).flatten),
      )

    final def get(method: Method, path: Path): RequestHandler[Env, Response] =
      tree.get(method, path)

    final def withAutoGeneratedHeadRoutes: Tree[Env] =
      Tree(tree.withAutoGeneratedHeadRoutes)
  }
  private[http] object Tree                                              {
    val empty: Tree[Any] = Tree(RoutePattern.Tree.empty)

    def fromRoutes[Env](routes: Chunk[zio.http.Route[Env, Response]])(implicit trace: Trace): Tree[Env] =
      empty.addAll(routes)
  }
}
