/*
 * Copyright 2026 the ZIO HTTP contributors.
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

import scala.collection.immutable.Map
import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.Scope
import zio.http.ResultType._

trait Middleware[UpperCtx, Ctx] { self =>
  def apply(routes: Routes[Ctx]): Routes[UpperCtx]
  def andThen[UpperCtx2](that: Middleware[UpperCtx2, UpperCtx]): Middleware[UpperCtx2, Ctx] =
    new Middleware[UpperCtx2, Ctx] {
      def apply(routes: Routes[Ctx]): Routes[UpperCtx2] = that(self(routes))
    }
}

object Middleware {
  private val timeoutExecutor: java.util.concurrent.Executor =
    java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()

  def identity[Ctx]: Middleware[Ctx, Ctx] = new Middleware[Ctx, Ctx] {
    def apply(routes: Routes[Ctx]): Routes[Ctx] = routes
  }

  // ═══════════════════════════════════════════════════════════════════
  // AUTH
  // ═══════════════════════════════════════════════════════════════════

  def customAuth[Session](
    validate: Request => Either[Response, Session],
  )(implicit ev: IsNominalType[Session]): Middleware[Any, Session] =
    new Middleware[Any, Session] {
      def apply(routes: Routes[Session]): Routes[Any]       =
        Routes.fromIterable(routes.routes.map(secure))
      private def secure(route: Route[Session]): Route[Any] = {
        val wrapped = Handler.extracted[Any, Any] { (request, context, vars, scope) =>
          validate(request) match {
            case Left(response) => responseAsResult(response)
            case Right(session) => route.handler.handle(request, context.add[Session](session), vars, scope)
          }
        }
        Route(route.pattern, wrapped)
      }
    }

  def basicAuth[Session](
    validate: Header.Authorization.Basic => Either[Response, Session],
  )(implicit ev: IsNominalType[Session]): Middleware[Any, Session] =
    customAuth { request =>
      request.header(Header.Authorization) match {
        case Some(basic: Header.Authorization.Basic) => validate(basic)
        case _                                       => Left(Response.unauthorized)
      }
    }

  def bearerAuth[Session](
    validate: Header.Authorization.Bearer => Either[Response, Session],
  )(implicit ev: IsNominalType[Session]): Middleware[Any, Session] =
    customAuth { request =>
      request.header(Header.Authorization) match {
        case Some(bearer: Header.Authorization.Bearer) => validate(bearer)
        case _                                         => Left(Response.unauthorized)
      }
    }

  // legacy non-generic overloads kept for backward compat
  def basicAuth(
    realm: String = "Access to the resource",
    validate: (String, String) => Boolean,
  ): Middleware[Any, Any] = {
    val wwwAuth = Header.WWWAuthenticate("Basic", Map("realm" -> realm))
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            req.header(Header.Authorization) match {
              case Some(Header.Authorization.Basic(u, p)) if validate(u, p) => next.handle(req, ctx, vars, scope)
              case _ => Halt(Response.unauthorized.addHeader(wwwAuth))
            }
          }
          Route(route.pattern, wrapped)
        })
    }
  }

  def bearerAuth(realm: String = "Access to the resource", validate: String => Boolean): Middleware[Any, Any] = {
    val wwwAuth = Header.WWWAuthenticate("Bearer", Map("realm" -> realm))
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            req.header(Header.Authorization) match {
              case Some(Header.Authorization.Bearer(token)) if validate(token) => next.handle(req, ctx, vars, scope)
              case _ => Halt(Response.unauthorized.addHeader(wwwAuth))
            }
          }
          Route(route.pattern, wrapped)
        })
    }
  }

  def customAuth[A](validate: Request => Option[A], realm: String = "Access to the resource")(implicit
    ev: IsNominalType[A],
  ): Middleware[Any, A] = {
    val wwwAuth = Header.WWWAuthenticate("Bearer", Map("realm" -> realm))
    new Middleware[Any, A] {
      def apply(routes: Routes[A]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler.asInstanceOf[Handler[A, Any]]
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            validate(req) match {
              case Some(a) =>
                next.handle(req, ctx.asInstanceOf[Context[Any]].add(a)(using ev).asInstanceOf[Context[A]], vars, scope)
              case None    => Halt(Response.unauthorized.addHeader(wwwAuth))
            }
          }
          Route(route.pattern, wrapped)
        })
    }
  }

  def customAuthProviding[A](provide: Request => A)(implicit ev: IsNominalType[A]): Middleware[Any, A] =
    new Middleware[Any, A] {
      def apply(routes: Routes[A]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler.asInstanceOf[Handler[A, Any]]
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val a = provide(req)
            next.handle(req, ctx.asInstanceOf[Context[Any]].add(a)(using ev).asInstanceOf[Context[A]], vars, scope)
          }
          Route(route.pattern, wrapped)
        })
    }

  // ═══════════════════════════════════════════════════════════════════
  // INTERCEPT
  // ═══════════════════════════════════════════════════════════════════

  def interceptHandler(interceptor: Request => Option[Response | Halt]): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            interceptor(req) match {
              case Some(result) => result
              case None         => next.handle(req, ctx, vars, scope)
            }
          }
          Route(route.pattern, wrapped)
        })
    }

  def interceptPatch(
    interceptor: Request => Option[Response | Halt],
    patcher: Response => Response = Predef.identity,
  ): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            interceptor(req) match {
              case Some(result) => result
              case None         =>
                foldResult(next.handle(req, ctx, vars, scope))(r => patcher(r), h => h)
            }
          }
          Route(route.pattern, wrapped)
        })
    }

  def debug(logger: String => Unit = println): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            logger(s"> ${req.method} ${req.url}")
            val result = next.handle(req, ctx, vars, scope)
            foldResult(result)(r => logger(s"< ${r.status} ${req.url}"), h => logger(s"! HALT ${req.url}"))
            result
          }
          Route(route.pattern, wrapped)
        })
    }

  def timing(reporter: (Method, String, Long) => Unit): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val start   = System.nanoTime()
            val result  = next.handle(req, ctx, vars, scope)
            val elapsed = System.nanoTime() - start
            reporter(req.method, req.url.toString, elapsed)
            result
          }
          Route(route.pattern, wrapped)
        })
    }

  // ═══════════════════════════════════════════════════════════════════
  // CONDITIONAL
  // ═══════════════════════════════════════════════════════════════════

  def when(predicate: Request => Boolean, middleware: Middleware[Any, Any]): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next        = route.handler
          val passthrough = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            next.handle(req, ctx, vars, scope)
          }
          val mwRoute     = Route(route.pattern, passthrough)
          val applied     = middleware(Routes(mwRoute)).routes.toList.head.handler
          val wrapped     = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            if (predicate(req)) applied.handle(req, ctx, vars, scope)
            else next.handle(req, ctx, vars, scope)
          }
          Route(route.pattern, wrapped)
        })
    }

  def ifRequestThenElse(
    predicate: Request => Boolean,
    onTrue: Middleware[Any, Any],
    onFalse: Middleware[Any, Any],
  ): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next         = route.handler
          val passthrough  = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            next.handle(req, ctx, vars, scope)
          }
          val mwRoute      = Route(route.pattern, passthrough)
          val trueApplied  = onTrue(Routes(mwRoute)).routes.toList.head.handler
          val falseApplied = onFalse(Routes(mwRoute)).routes.toList.head.handler
          val wrapped      = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            if (predicate(req)) trueApplied.handle(req, ctx, vars, scope)
            else falseApplied.handle(req, ctx, vars, scope)
          }
          Route(route.pattern, wrapped)
        })
    }

  def ifRequestThen(predicate: Request => Boolean, onTrue: Middleware[Any, Any]): Middleware[Any, Any] =
    ifRequestThenElse(predicate, onTrue, Middleware.identity[Any])

  def methods(mapping: (Method, Middleware[Any, Any])*): Middleware[Any, Any] = {
    val default: Middleware[Any, Any] = Middleware.identity[Any]
    val map                           = mapping.toMap
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next           = route.handler
          val passthrough    = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            next.handle(req, ctx, vars, scope)
          }
          val mwRoute        = Route(route.pattern, passthrough)
          // Apply middleware once per route at build time, not per request.
          val defaultHandler = default(Routes(mwRoute)).routes.toList.head.handler
          val perMethod      =
            map.map { case (method, mw) => method -> mw(Routes(mwRoute)).routes.toList.head.handler }
          val wrapped        = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            perMethod.getOrElse(req.method, defaultHandler).handle(req, ctx, vars, scope)
          }
          Route(route.pattern, wrapped)
        })
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // CORS
  // ═══════════════════════════════════════════════════════════════════

  final case class CorsConfig(
    allowedOrigins: Set[String] = Set("*"),
    allowedMethods: Set[zio.http.Method] =
      Set(Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.PATCH, Method.OPTIONS),
    allowedHeaders: Set[String] = Set("Content-Type", "Authorization", "X-Requested-With"),
    exposedHeaders: Set[String] = Set.empty,
    allowCredentials: Boolean = true,
    maxAge: java.time.Duration = java.time.Duration.ofHours(1),
  )

  def cors(config: CorsConfig = CorsConfig()): Middleware[Any, Any] = {
    val methodsVal       = zio.blocks.chunk.Chunk.fromIterable(config.allowedMethods)
    val headersVal       = zio.blocks.chunk.Chunk.fromIterable(config.allowedHeaders)
    val exposeHeadersVal = zio.blocks.chunk.Chunk.fromIterable(config.exposedHeaders)
    val maxAgeSeconds    = config.maxAge.getSeconds

    def originAllowed(origin: String): Boolean =
      config.allowedOrigins.contains("*") || config.allowedOrigins.contains(origin)

    def corsPreflightHeaders(originStr: String): List[Header] = {
      val originHdr: Header =
        if (config.allowedOrigins.contains("*") && config.allowCredentials)
          Header.AccessControlAllowOrigin.Specific(originStr)
        else if (config.allowedOrigins.contains("*"))
          Header.AccessControlAllowOrigin.All
        else
          Header.AccessControlAllowOrigin.Specific(originStr)
      val headers           = List(
        originHdr,
        Header.AccessControlAllowMethods(methodsVal),
        Header.AccessControlAllowHeaders(headersVal),
        Header.AccessControlAllowCredentials(config.allowCredentials),
        Header.AccessControlMaxAge(maxAgeSeconds),
      ) ++ (if (config.exposedHeaders.nonEmpty)
              List(Header.AccessControlExposeHeaders(exposeHeadersVal))
            else Nil)
      headers :+ Header.Vary("Origin")
    }

    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            req.header(Header.Origin) match {
              case Some(origin: Header.Origin) =>
                val originStr = origin.renderedValue
                if (!originAllowed(originStr)) {
                  responseAsResult(Response.forbidden)
                } else if (req.method == Method.OPTIONS && req.header(Header.AccessControlRequestMethod).isDefined) {
                  val hdrs = corsPreflightHeaders(originStr)
                  val resp = hdrs.foldLeft(Response(Status.NoContent))((acc, h) => acc.addHeader(h))
                  resp
                } else {
                  val hdrs = corsPreflightHeaders(originStr)
                  foldResult(next.handle(req, ctx, vars, scope))(
                    r => hdrs.foldLeft(r)((acc, h) => acc.addHeader(h)),
                    h => h,
                  )
                }
              case None                        => next.handle(req, ctx, vars, scope)
            }
          }
          Route(route.pattern, wrapped)
        })
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // REQUEST LOGGING
  // ═══════════════════════════════════════════════════════════════════

  def requestLogging(logger: String => Unit = println): Middleware[Any, Any] =
    timing((method, path, nanos) => {
      val millis = nanos / 1000000
      logger(s"${method.toString.padTo(7, ' ')} $path — ${millis}ms")
    })

  // ═══════════════════════════════════════════════════════════════════
  // FLASH SCOPE
  // ═══════════════════════════════════════════════════════════════════

  final case class FlashMap private (values: Map[String, String]) {
    def get(key: String): Option[String]  = values.get(key)
    def +(kv: (String, String)): FlashMap = copy(values = values + kv)
    def isEmpty: Boolean                  = values.isEmpty
  }

  object FlashMap {
    val empty: FlashMap                            = FlashMap(Map.empty[String, String])
    def apply(values: (String, String)*): FlashMap = FlashMap(values.toMap)
    def fromMap(m: Map[String, String]): FlashMap  = FlashMap(m)
  }

  def flashScope()(implicit ev: IsNominalType[FlashMap]): Middleware[FlashMap, Any] =
    new Middleware[FlashMap, Any] {
      def apply(routes: Routes[Any]): Routes[FlashMap] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler.asInstanceOf[Handler[FlashMap, Any]]
          val wrapped = Handler.extracted[FlashMap, Any] { (req, ctx, vars, scope) =>
            val incomingFlash: Map[String, String] = req.cookies.iterator
              .find(_.name == "flash")
              .map { c =>
                c.value
                  .split("&")
                  .flatMap { pair =>
                    pair.split("=", 2) match {
                      case Array(k, v) =>
                        try {
                          Some(java.net.URLDecoder.decode(k, "UTF-8") -> java.net.URLDecoder.decode(v, "UTF-8"))
                        } catch {
                          case _: IllegalArgumentException => None
                        }
                      case _           => None
                    }
                  }
                  .toMap
              }
              .getOrElse(Map.empty)
            val flash                              = FlashMap.fromMap(incomingFlash)
            val result                             = next.handle(
              req,
              ctx.asInstanceOf[Context[Any]].add(flash)(using ev).asInstanceOf[Context[FlashMap]],
              vars,
              scope,
            )
            foldResult(result)(
              r => r.addHeader(Header.Custom("Set-Cookie", "flash=; Max-Age=0; Path=/")),
              h => h,
            )
          }
          Route(route.pattern, wrapped)
        })
    }

  // ═══════════════════════════════════════════════════════════════════
  // TIMEOUT
  // ═══════════════════════════════════════════════════════════════════

  def timeout(millis: Long): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val future = java.util.concurrent.CompletableFuture.supplyAsync(
              () => next.handle(req, ctx, vars, scope),
              timeoutExecutor,
            )
            try {
              future.get(millis, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch {
              case _: java.util.concurrent.TimeoutException   =>
                future.cancel(true)
                haltAsResult(Halt(Response(Status.ServiceUnavailable)))
              case _: InterruptedException                    =>
                future.cancel(true)
                Thread.currentThread().interrupt()
                haltAsResult(Halt(Response(Status.ServiceUnavailable)))
              case e: java.util.concurrent.ExecutionException =>
                System.err.println(s"Middleware.timeout: handler threw ${e.getCause}")
                haltAsResult(Halt(Response(Status.InternalServerError)))
            }
          }
          Route(route.pattern, wrapped)
        })
    }

  // ═══════════════════════════════════════════════════════════════════
  // STATIC FILE SERVING
  // ═══════════════════════════════════════════════════════════════════

  def serveDirectory(directoryPath: String): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val baseDir = new java.io.File(directoryPath).getCanonicalFile
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val requested = new java.io.File(baseDir, req.path.toString.stripPrefix("/")).getCanonicalFile
            try {
              val path = requested.toPath
              if (path.startsWith(baseDir.toPath) && java.nio.file.Files.isRegularFile(path)) {
                val bytes     = java.nio.file.Files.readAllBytes(path)
                val mediaType = requested.getName match {
                  case n if n.endsWith(".html")                       => "text/html"
                  case n if n.endsWith(".css")                        => "text/css"
                  case n if n.endsWith(".js")                         => "application/javascript"
                  case n if n.endsWith(".png")                        => "image/png"
                  case n if n.endsWith(".jpg") || n.endsWith(".jpeg") => "image/jpeg"
                  case n if n.endsWith(".svg")                        => "image/svg+xml"
                  case _                                              => "application/octet-stream"
                }
                Response(Status.Ok, Headers(("Content-Type", mediaType)), Body.fromArray(bytes))
              } else next.handle(req, ctx, vars, scope)
            } catch { case _: java.io.IOException => next.handle(req, ctx, vars, scope) }
          }
          Route(route.pattern, wrapped)
        })
    }

  def serveResources(basePath: String = "public"): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val resourcePath = req.path.toString.stripPrefix("/")
            val segments     = resourcePath.split("/").toList
            if (segments.contains("..") || segments.contains(".")) {
              next.handle(req, ctx, vars, scope)
            } else {
              val fullPath = if (basePath.isEmpty) resourcePath else s"$basePath/$resourcePath"
              val stream   = getClass.getClassLoader.getResourceAsStream(fullPath)
              if (stream != null) {
                try {
                  val bytes     = stream.readAllBytes()
                  val mediaType = fullPath match {
                    case n if n.endsWith(".html")                       => "text/html"
                    case n if n.endsWith(".css")                        => "text/css"
                    case n if n.endsWith(".js")                         => "application/javascript"
                    case n if n.endsWith(".png")                        => "image/png"
                    case n if n.endsWith(".jpg") || n.endsWith(".jpeg") => "image/jpeg"
                    case n if n.endsWith(".svg")                        => "image/svg+xml"
                    case _                                              => "application/octet-stream"
                  }
                  Response(Status.Ok, Headers(("Content-Type", mediaType)), Body.fromArray(bytes))
                } catch {
                  case _: java.io.IOException => next.handle(req, ctx, vars, scope)
                } finally {
                  try { stream.close() }
                  catch { case _: java.io.IOException => () }
                }
              } else {
                next.handle(req, ctx, vars, scope)
              }
            }
          }
          Route(route.pattern, wrapped)
        })
    }

  // ═══════════════════════════════════════════════════════════════════
  // HEADER OPERATIONS
  // ═══════════════════════════════════════════════════════════════════

  /** Adds a header to every response. */
  def addHeader(header: Header): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            foldResult(next.handle(req, ctx, vars, scope))(r => r.addHeader(header), h => h)
          }
          Route(route.pattern, wrapped)
        })
    }

  /** Adds a header by name and value to every response. */
  def addHeader(name: String, value: String): Middleware[Any, Any] =
    addHeader(Header.Custom(name, value))

  /**
   * Updates response headers using a transformation function. `f` receives the
   * current headers and returns modified headers.
   */
  def updateHeaders(f: Headers => Headers): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            foldResult(next.handle(req, ctx, vars, scope))(
              r => Response(r.status, f(r.headers), r.body, r.version),
              h => h,
            )
          }
          Route(route.pattern, wrapped)
        })
    }

  /** Removes a response header by name. */
  def removeHeader(name: String): Middleware[Any, Any] =
    updateHeaders(_.remove(name))

  // ═══════════════════════════════════════════════════════════════════
  // PATH OPERATIONS
  // ═══════════════════════════════════════════════════════════════════

  /** Appends a segment to the request path. */
  def appendPath(segment: String): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val oldPath     = req.url.path
            val newSegments = oldPath.segments :+ segment
            val newPath     = Path(newSegments, oldPath.hasLeadingSlash, oldPath.trailingSlash)
            val newUrl      = req.url.copy(path = newPath)
            val newReq      = Request(req.method, newUrl, req.headers, req.body, req.version)
            next.handle(newReq, ctx, vars, scope)
          }
          Route(route.pattern, wrapped)
        })
    }

  /** Prepends a segment to the request path. */
  def prependPath(segment: String): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val oldPath     = req.url.path
            val newSegments = zio.blocks.chunk.Chunk(segment) ++ oldPath.segments
            val newPath     = Path(newSegments, oldPath.hasLeadingSlash, oldPath.trailingSlash)
            val newUrl      = req.url.copy(path = newPath)
            val newReq      = Request(req.method, newUrl, req.headers, req.body, req.version)
            next.handle(newReq, ctx, vars, scope)
          }
          Route(route.pattern, wrapped)
        })
    }

  /** Strips a prefix from the request path. */
  def stripPathPrefix(prefix: String): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val path       = req.url.path
            val segments   = path.segments
            val prefixSegs = prefix.stripPrefix("/").stripSuffix("/").split("/").filter(_.nonEmpty)
            if (segments.take(prefixSegs.length).toList.map(_.toString) == prefixSegs.toSeq) {
              val newSegments = segments.drop(prefixSegs.length)
              val newPath     = Path(newSegments, hasLeadingSlash = true, trailingSlash = path.trailingSlash)
              val newUrl      = req.url.copy(path = newPath)
              val newReq      = Request(req.method, newUrl, req.headers, req.body, req.version)
              next.handle(newReq, ctx, vars, scope)
            } else next.handle(req, ctx, vars, scope)
          }
          Route(route.pattern, wrapped)
        })
    }

  /** Removes the trailing slash from the request path. */
  val dropTrailingSlash: Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val p = req.url.path
            if (p.trailingSlash && p.segments.nonEmpty) {
              val newPath = Path(p.segments, p.hasLeadingSlash, trailingSlash = false)
              val newUrl  = req.url.copy(path = newPath)
              val newReq  = Request(req.method, newUrl, req.headers, req.body, req.version)
              next.handle(newReq, ctx, vars, scope)
            } else next.handle(req, ctx, vars, scope)
          }
          Route(route.pattern, wrapped)
        })
    }

  /** Adds a trailing slash to the request path if absent. */
  val addTrailingSlash: Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val p = req.url.path
            if (!p.trailingSlash && p.segments.nonEmpty) {
              val newPath = Path(p.segments, p.hasLeadingSlash, trailingSlash = true)
              val newUrl  = req.url.copy(path = newPath)
              val newReq  = Request(req.method, newUrl, req.headers, req.body, req.version)
              next.handle(newReq, ctx, vars, scope)
            } else next.handle(req, ctx, vars, scope)
          }
          Route(route.pattern, wrapped)
        })
    }

  // ═══════════════════════════════════════════════════════════════════
  // RUN BEFORE / AFTER
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Runs `effect` before every handler. If the effect returns a `Response` or
   * `Halt`, the handler is short-circuited. If it returns `None`, the handler
   * proceeds normally.
   */
  def runBefore(effect: Request => Option[Response | Halt]): Middleware[Any, Any] =
    this.interceptHandler((req: Request) => effect(req))

  /**
   * Runs `effect` after every handler. The effect receives the request and the
   * result from the downstream handler.
   */
  def runAfter(effect: (Request, Response | Halt) => Response | Halt): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            effect(req, next.handle(req, ctx, vars, scope))
          }
          Route(route.pattern, wrapped)
        })
    }

  // ═══════════════════════════════════════════════════════════════════
  // REDIRECT
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Redirects all requests to a different location with the given status.
   * Common status codes: 301 (Moved Permanently), 302 (Found), 307, 308.
   */
  def redirect(status: Status, location: String): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            Halt(Response(status, Headers.empty.add("Location", location), Body.empty, req.version))
          }
          Route(route.pattern, wrapped)
        })
    }

  /** Redirects all requests with 302 Found. */
  def redirectTemporary(location: String): Middleware[Any, Any] = {
    val status302 = Status.fromInt(302)
    redirect(status302, location)
  }

  /** Redirects all requests with 301 Moved Permanently. */
  def redirectPermanent(location: String): Middleware[Any, Any] =
    redirect(Status.fromInt(301), location)

  // ═══════════════════════════════════════════════════════════════════
  // SIGN COOKIES
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Middleware that signs response Set-Cookie headers using HMAC-SHA256.
   * Incoming request cookies are verified; invalid cookies are removed.
   *
   * @param secret
   *   The shared secret key for HMAC signing
   */
  def signCookies(secret: String): Middleware[Any, Any] = {
    val hmacKey = new javax.crypto.spec.SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256")
    def sign(name: String, value: String): String            = {
      val mac      = javax.crypto.Mac.getInstance("HmacSHA256")
      mac.init(hmacKey)
      // Bind the signature to the cookie name to prevent cookie-swapping.
      val macInput = s"$name=$value"
      val sig = java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(mac.doFinal(macInput.getBytes("UTF-8")))
      s"$value.$sig"
    }
    def verify(name: String, signed: String): Option[String] = {
      val dot = signed.lastIndexOf('.')
      if (dot < 0) None
      else {
        val value    = signed.substring(0, dot)
        val expected = sign(name, value)
        if (java.security.MessageDigest.isEqual(signed.getBytes("UTF-8"), expected.getBytes("UTF-8"))) Some(value)
        else None
      }
    }

    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            // Verify incoming cookies
            val verifiedReq = {
              val cookies  = req.cookies
              val verified = cookies.flatMap { c =>
                verify(c.name, c.value) match {
                  case Some(orig) => Some(RequestCookie(c.name, orig))
                  case None       => None
                }
              }
              // Reconstruct request with verified (signature-stripped) cookies (or remove Cookie header if none valid)
              if (verified.nonEmpty) {
                val cookieHeader = verified.map(c => s"${c.name}=${c.value}").mkString("; ")
                Request(req.method, req.url, req.headers.remove("Cookie"), req.body, req.version)
                  .addHeader("Cookie", cookieHeader)
              } else {
                Request(req.method, req.url, req.headers.remove("Cookie"), req.body, req.version)
              }
            }
            val result      = next.handle(verifiedReq, ctx, vars, scope)
            // Sign outgoing set-cookie headers
            foldResult(result)(
              r => {
                val signedCookies = r.cookies.map { c =>
                  ResponseCookie(
                    c.name,
                    sign(c.name, c.value),
                    c.expires,
                    c.domain,
                    c.path,
                    c.maxAge,
                    c.isSecure,
                    c.isHttpOnly,
                    c.sameSite,
                    c.isPartitioned,
                    c.priority,
                  )
                }
                // Strip original Set-Cookie headers before adding signed ones
                var resp          = Response(r.status, r.headers.remove("Set-Cookie"), r.body, r.version)
                signedCookies.foreach { c =>
                  resp = resp.addHeader(Header.Custom("Set-Cookie", c.toString))
                }
                resp
              },
              h => h,
            )
          }
          Route(route.pattern, wrapped)
        })
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // STATUS / CONDITIONAL RESPONSE
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Middleware that intercepts responses whose status code matches `predicate`
   * and replaces them via `handler`.
   *
   * Example: `Middleware.status(_ == 404, _ => Response.text("Not Found"))`
   */
  def status(predicate: Status => Boolean, handler: Response => Response | Halt): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            foldResult(next.handle(req, ctx, vars, scope))(
              r => if (predicate(r.status)) handler(r) else r,
              h => h,
            )
          }
          Route(route.pattern, wrapped)
        })
    }

  // ═══════════════════════════════════════════════════════════════════
  // DUMP REQUEST / RESPONSE
  // ═══════════════════════════════════════════════════════════════════

  /** Logs a full dump of the incoming request. */
  def requestDump(logger: String => Unit = println): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            logger(
              s"── Request ──\n  method: ${req.method}\n  path:   ${req.url}\n  headers:\n${req.headers.toList.map {
                  case (k, v) => s"    $k: $v"
                }.mkString("\n")}\n  body:   ${req.body}",
            )
            next.handle(req, ctx, vars, scope)
          }
          Route(route.pattern, wrapped)
        })
    }

  /** Logs a full dump of the outgoing response. */
  def responseDump(logger: String => Unit = println): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next    = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val result = next.handle(req, ctx, vars, scope)
            foldResult(result)(
              r =>
                logger(s"── Response ──\n  status: ${r.status}\n  headers:\n${r.headers.toList.map { case (k, v) =>
                    s"    $k: $v"
                  }.mkString("\n")}\n  body:   ${r.body}"),
              h => logger(s"── Halt ──"),
            )
            result
          }
          Route(route.pattern, wrapped)
        })
    }
}
