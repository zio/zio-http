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
import zio.blocks.endpoint.RoutePattern
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
  private lazy val timeoutExecutor: java.util.concurrent.ExecutorService =
    java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()

  private def wrap(
    routes: Routes[Any],
  )(f: (Request, Context[Any], Any, Scope, Handler[Any, Any]) => Response | Halt): Routes[Any] =
    Routes.fromIterable(routes.routes.toList.map { route =>
      val next    = route.handler
      val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) => f(req, ctx, vars, scope, next) }
      Route(route.pattern, wrapped)
    })

  def identity[Ctx]: Middleware[Ctx, Ctx] = new Middleware[Ctx, Ctx] {
    def apply(routes: Routes[Ctx]): Routes[Ctx] = routes
  }

  // ═══════════════════════════════════════════════════════════════════
  // AUTH
  // ═══════════════════════════════════════════════════════════════════

  /**
   * @param validate
   *   A function that inspects the request and returns one of two outcomes:
   *   - `Halt` (via `haltAsOutcome(halt)`) — rejection; the middleware returns
   *     it as-is.
   *   - `S` (via `valueAsOutcome(value)`) — success; the value is injected into
   *     the handler context.
   *
   * MUST use constant-time comparison (e.g.
   * java.security.MessageDigest.isEqual) to avoid timing side-channels.
   *
   * {{{
   *   Middleware.customAuth[User] { req =>
   *     req.header(Header.Authorization) match {
   *       case Some(Header.Authorization.Bearer(token)) =>
   *         validateToken(token) match {
   *           case Some(user) => valueAsOutcome(user)
   *           case None       => haltAsOutcome(Halt(Response.unauthorized))
   *         }
   *       case _ => haltAsOutcome(Halt(Response.unauthorized))
   *     }
   *   }
   * }}}
   */
  def customAuth[S](
    validate: Request => Halt | S,
    realm: String = "Access to the resource",
  )(implicit ev: IsNominalType[S]): Middleware[Any, S] =
    new Middleware[Any, S] {
      def apply(routes: Routes[S]): Routes[Any]       =
        Routes.fromIterable(routes.routes.map(secure))
      private def secure(route: Route[S]): Route[Any] = {
        val wrapped = Handler.extracted[Any, Any] { (request, context, vars, scope) =>
          foldOutcome(validate(request))(
            onHalt = h => haltAsResult(h),
            onValue = value => route.handler.handle(request, context.add[S](value), vars, scope),
          )
        }
        Route(route.pattern, wrapped)
      }
    }

  /**
   * @param validate
   *   MUST use constant-time comparison (e.g.
   *   java.security.MessageDigest.isEqual) to avoid timing side-channels.
   */
  def basicAuth[S](
    validate: Header.Authorization.Basic => Either[Response, S],
  )(implicit ev: IsNominalType[S]): Middleware[Any, S] =
    customAuth { request =>
      request.header(Header.Authorization) match {
        case Some(basic: Header.Authorization.Basic) =>
          validate(basic) match {
            case Right(session) => valueAsOutcome(session)
            case Left(response) => haltAsOutcome(Halt(response))
          }
        case _                                       => haltAsOutcome(Halt(Response.unauthorized))
      }
    }

  /**
   * @param validate
   *   MUST use constant-time comparison (e.g.
   *   java.security.MessageDigest.isEqual) to avoid timing side-channels.
   */
  def bearerAuth[S](
    validate: Header.Authorization.Bearer => Either[Response, S],
  )(implicit ev: IsNominalType[S]): Middleware[Any, S] =
    customAuth { request =>
      request.header(Header.Authorization) match {
        case Some(bearer: Header.Authorization.Bearer) =>
          validate(bearer) match {
            case Right(session) => valueAsOutcome(session)
            case Left(response) => haltAsOutcome(Halt(response))
          }
        case _                                         => haltAsOutcome(Halt(Response.unauthorized))
      }
    }

  // ═══════════════════════════════════════════════════════════════════
  // INTERCEPT
  // ═══════════════════════════════════════════════════════════════════

  def interceptHandler(interceptor: Request => Option[Response | Halt]): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
          interceptor(req) match {
            case Some(result) => result
            case None         => next.handle(req, ctx, vars, scope)
          }
        }
    }

  def interceptPatch(
    interceptor: Request => Option[Response | Halt],
    patcher: Response => Response = Predef.identity,
  ): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
          interceptor(req) match {
            case Some(result) => result
            case None         =>
              foldResult(next.handle(req, ctx, vars, scope))(r => patcher(r), h => h)
          }
        }
    }

  def debug(logger: String => Unit = println): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
          logger(s"> ${req.method} ${req.url}")
          val result = next.handle(req, ctx, vars, scope)
          foldResult(result)(r => logger(s"< ${r.status} ${req.url}"), h => logger(s"! HALT ${req.url}"))
          result
        }
    }

  def timing(reporter: (Method, String, Long) => Unit): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
          val start   = System.nanoTime()
          val result  = next.handle(req, ctx, vars, scope)
          val elapsed = System.nanoTime() - start
          reporter(req.method, req.url.toString, elapsed)
          result
        }
    }

  // ═══════════════════════════════════════════════════════════════════
  // CONDITIONAL
  // ═══════════════════════════════════════════════════════════════════

  def when(predicate: Request => Boolean, middleware: Middleware[Any, Any]): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] = {
        // Apply middleware once per route at build time, not per request.
        val appliedByNext: Map[Handler[Any, Any], Handler[Any, Any]] =
          routes.routes.toList.map { route =>
            val next        = route.handler
            val passthrough = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
              next.handle(req, ctx, vars, scope)
            }
            val applied     =
              middleware(Routes(Route(RoutePattern.any, passthrough))).routes.toList.headOption
                .map(_.handler)
                .getOrElse(passthrough)
            next -> applied
          }.toMap
        wrap(routes) { (req, ctx, vars, scope, next) =>
          if (predicate(req)) appliedByNext.getOrElse(next, next).handle(req, ctx, vars, scope)
          else next.handle(req, ctx, vars, scope)
        }
      }
    }

  def ifRequestThenElse(
    predicate: Request => Boolean,
    onTrue: Middleware[Any, Any],
    onFalse: Middleware[Any, Any],
  ): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] = {
        // Apply both branches once per route at build time, not per request.
        val appliedByNext: Map[Handler[Any, Any], (Handler[Any, Any], Handler[Any, Any])] =
          routes.routes.toList.map { route =>
            val next         = route.handler
            val passthrough  = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
              next.handle(req, ctx, vars, scope)
            }
            val mwRoute      = Route(RoutePattern.any, passthrough)
            val trueApplied  = onTrue(Routes(mwRoute)).routes.toList.headOption.map(_.handler).getOrElse(passthrough)
            val falseApplied = onFalse(Routes(mwRoute)).routes.toList.headOption.map(_.handler).getOrElse(passthrough)
            next -> ((trueApplied, falseApplied))
          }.toMap
        wrap(routes) { (req, ctx, vars, scope, next) =>
          val (trueApplied, falseApplied) = appliedByNext.getOrElse(next, (next, next))
          if (predicate(req)) trueApplied.handle(req, ctx, vars, scope)
          else falseApplied.handle(req, ctx, vars, scope)
        }
      }
    }

  def ifRequestThen(predicate: Request => Boolean, onTrue: Middleware[Any, Any]): Middleware[Any, Any] =
    ifRequestThenElse(predicate, onTrue, Middleware.identity[Any])

  def methods(mapping: (Method, Middleware[Any, Any])*): Middleware[Any, Any] = {
    val default: Middleware[Any, Any] = Middleware.identity[Any]
    val map                           = mapping.toMap
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] = {
        // Apply middleware once per route at build time, not per request.
        val perMethodByNext: Map[Handler[Any, Any], Map[Method, Handler[Any, Any]]] =
          routes.routes.toList.map { route =>
            val next        = route.handler
            val passthrough = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
              next.handle(req, ctx, vars, scope)
            }
            val mwRoute     = Route(RoutePattern.any, passthrough)
            val perMethod   =
              map.map { case (method, mw) =>
                method -> mw(Routes(mwRoute)).routes.toList.headOption.map(_.handler).getOrElse(passthrough)
              }
            next -> perMethod
          }.toMap
        wrap(routes) { (req, ctx, vars, scope, next) =>
          perMethodByNext.getOrElse(next, Map.empty).getOrElse(req.method, next).handle(req, ctx, vars, scope)
        }
      }
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
        wrap(routes) { (req, ctx, vars, scope, next) =>
          req.header(Header.Origin) match {
            case Some(origin: Header.Origin) =>
              val originStr = origin.renderedValue
              if (!originAllowed(originStr)) {
                responseAsResult(Response.forbidden)
              } else if (req.method == Method.OPTIONS && req.header(Header.AccessControlRequestMethod).isDefined) {
                // Validate requested method and headers against allowlists
                val requestedMethod  = req.header(Header.AccessControlRequestMethod).get.method
                val methodAllowed    = config.allowedMethods.contains(requestedMethod)
                val requestedHeaders = req.headers.toList.collectFirst {
                  case (k, v) if k.equalsIgnoreCase("Access-Control-Request-Headers") => v
                } match {
                  case Some(h) => h.split(",").map(_.trim).toSet
                  case None    => Set.empty[String]
                }
                val headersAllowed   = requestedHeaders.forall(h => config.allowedHeaders.exists(_.equalsIgnoreCase(h)))
                if (methodAllowed && headersAllowed) {
                  val hdrs = corsPreflightHeaders(originStr)
                  val resp = hdrs.foldLeft(Response(Status.NoContent))((acc, h) => acc.addHeader(h))
                  resp
                } else {
                  next.handle(req, ctx, vars, scope)
                }
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

  def flashScope()(implicit ev: IsNominalType[FlashMap]): Middleware[Any, FlashMap] =
    new Middleware[Any, FlashMap] {
      def apply(routes: Routes[FlashMap]): Routes[Any] =
        Routes.fromIterable(
          routes.routes.toList.map { route =>
            val next    = route.handler
            val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
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
                ctx.add(flash),
                vars,
                scope,
              )
              foldResult(result)(
                r => r.addHeader(Header.Custom("Set-Cookie", "flash=; Max-Age=0; Path=/")),
                h => h,
              )
            }
            Route(route.pattern, wrapped)
          },
        )
    }

  // ═══════════════════════════════════════════════════════════════════
  // TIMEOUT
  // ═══════════════════════════════════════════════════════════════════

  def timeout(
    duration: zio.Duration,
    logger: Throwable => Unit = t => Console.err.println(s"Middleware.timeout: handler threw ${t.getCause}"),
  ): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
          val millis = duration.toMillis
          val future = java.util.concurrent.CompletableFuture.supplyAsync(
            () => next.handle(req, ctx, vars, scope),
            timeoutExecutor,
          )
          try {
            future.get(millis, java.util.concurrent.TimeUnit.MILLISECONDS)
          } catch {
            case _: java.util.concurrent.TimeoutException   =>
              future.cancel(true)
              haltAsResult(Halt(Response(Status.RequestTimeout)))
            case _: InterruptedException                    =>
              future.cancel(true)
              Thread.currentThread().interrupt()
              haltAsResult(Halt(Response(Status.RequestTimeout)))
            case e: java.util.concurrent.ExecutionException =>
              logger(e)
              haltAsResult(Halt(Response(Status.InternalServerError)))
          }
        }
    }

  // ═══════════════════════════════════════════════════════════════════
  // STATIC FILE SERVING
  // ═══════════════════════════════════════════════════════════════════

  def serveDirectory(docRoot: java.io.File): Middleware[Any, Any] = {
    val baseDir = docRoot.getCanonicalFile
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
          try {
            val requested = new java.io.File(baseDir, req.path.toString.stripPrefix("/")).getCanonicalFile
            val path      = requested.toPath
            if (path.startsWith(baseDir.toPath) && java.nio.file.Files.isRegularFile(path)) {
              val mediaType = requested.getName match {
                case n if n.endsWith(".html")                       => "text/html"
                case n if n.endsWith(".css")                        => "text/css"
                case n if n.endsWith(".js")                         => "application/javascript"
                case n if n.endsWith(".png")                        => "image/png"
                case n if n.endsWith(".jpg") || n.endsWith(".jpeg") => "image/jpeg"
                case n if n.endsWith(".svg")                        => "image/svg+xml"
                case _                                              => "application/octet-stream"
              }
              val fis       = new java.io.FileInputStream(requested)
              val body      = Body.fromStream(
                zio.blocks.streams.Stream
                  .fromInputStream(fis)
                  .catchAll(t => zio.blocks.streams.Stream.die(t)),
              )
              Response(Status.Ok, Headers(("Content-Type", mediaType)), body)
            } else next.handle(req, ctx, vars, scope)
          } catch { case _: java.io.IOException => next.handle(req, ctx, vars, scope) }
        }
    }
  }

  def serveResources(basePath: zio.http.Path): Middleware[Any, Any] = {
    val prefix = basePath.toString.stripPrefix("/")
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
          val resourcePath = req.path.toString.stripPrefix("/")
          val segments     = resourcePath.split("/").toList
          val decoded      = segments.map { s =>
            try { java.net.URLDecoder.decode(s, "UTF-8") }
            catch { case _: IllegalArgumentException => s }
          }
          if (decoded.contains("..") || decoded.contains(".")) {
            next.handle(req, ctx, vars, scope)
          } else {
            val fullPath = if (prefix.isEmpty) resourcePath else s"$prefix/$resourcePath"
            val stream   = getClass.getClassLoader.getResourceAsStream(fullPath)
            if (stream != null) {
              try {
                val mediaType = fullPath match {
                  case n if n.endsWith(".html")                       => "text/html"
                  case n if n.endsWith(".css")                        => "text/css"
                  case n if n.endsWith(".js")                         => "application/javascript"
                  case n if n.endsWith(".png")                        => "image/png"
                  case n if n.endsWith(".jpg") || n.endsWith(".jpeg") => "image/jpeg"
                  case n if n.endsWith(".svg")                        => "image/svg+xml"
                  case _                                              => "application/octet-stream"
                }
                val body      = Body.fromStream(
                  zio.blocks.streams.Stream
                    .fromInputStream(stream)
                    .catchAll(t => zio.blocks.streams.Stream.die(t)),
                )
                Response(Status.Ok, Headers(("Content-Type", mediaType)), body)
              } catch {
                case _: java.io.IOException => next.handle(req, ctx, vars, scope)
              }
            } else {
              next.handle(req, ctx, vars, scope)
            }
          }
        }
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // HEADER OPERATIONS
  // ═══════════════════════════════════════════════════════════════════

  /** Adds a header to every response. */
  def addHeader(header: Header): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
          foldResult(next.handle(req, ctx, vars, scope))(r => r.addHeader(header), h => h)
        }
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
        wrap(routes) { (req, ctx, vars, scope, next) =>
          foldResult(next.handle(req, ctx, vars, scope))(
            r => Response(r.status, f(r.headers), r.body, r.version),
            h => h,
          )
        }
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
        wrap(routes) { (req, ctx, vars, scope, next) =>
          val oldPath     = req.url.path
          val newSegments = oldPath.segments :+ segment
          val newPath     = Path(newSegments, oldPath.hasLeadingSlash, oldPath.trailingSlash)
          val newUrl      = req.url.copy(path = newPath)
          val newReq      = Request(req.method, newUrl, req.headers, req.body, req.version)
          next.handle(newReq, ctx, vars, scope)
        }
    }

  /** Prepends a segment to the request path. */
  def prependPath(segment: String): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
          val oldPath     = req.url.path
          val newSegments = zio.blocks.chunk.Chunk(segment) ++ oldPath.segments
          val newPath     = Path(newSegments, oldPath.hasLeadingSlash, oldPath.trailingSlash)
          val newUrl      = req.url.copy(path = newPath)
          val newReq      = Request(req.method, newUrl, req.headers, req.body, req.version)
          next.handle(newReq, ctx, vars, scope)
        }
    }

  /** Strips a prefix from the request path. */
  def stripPathPrefix(prefix: String): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
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
    }

  /** Removes the trailing slash from the request path. */
  val dropTrailingSlash: Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
          val p = req.url.path
          if (p.trailingSlash && p.segments.nonEmpty) {
            val newPath = Path(p.segments, p.hasLeadingSlash, trailingSlash = false)
            val newUrl  = req.url.copy(path = newPath)
            val newReq  = Request(req.method, newUrl, req.headers, req.body, req.version)
            next.handle(newReq, ctx, vars, scope)
          } else next.handle(req, ctx, vars, scope)
        }
    }

  /** Adds a trailing slash to the request path if absent. */
  val addTrailingSlash: Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
          val p = req.url.path
          if (!p.trailingSlash && p.segments.nonEmpty) {
            val newPath = Path(p.segments, p.hasLeadingSlash, trailingSlash = true)
            val newUrl  = req.url.copy(path = newPath)
            val newReq  = Request(req.method, newUrl, req.headers, req.body, req.version)
            next.handle(newReq, ctx, vars, scope)
          } else next.handle(req, ctx, vars, scope)
        }
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
        wrap(routes) { (req, ctx, vars, scope, next) =>
          effect(req, next.handle(req, ctx, vars, scope))
        }
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
        wrap(routes) { (req, ctx, vars, scope, next) =>
          Halt(Response(status, Headers.empty.add("Location", location), Body.empty, req.version))
        }
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
   * Incoming request cookies are verified; any cookie without a valid signature
   * (including unsigned cookies) is removed from the request.
   *
   * @param secret
   *   The shared secret key for HMAC signing. Must be at least 32 characters
   *   long to provide sufficient entropy for HMAC-SHA256.
   */
  def signCookies(secret: String): Middleware[Any, Any] = {
    require(secret != null && secret.length >= 32, "signCookies requires a secret of at least 32 characters")
    val hmacKey                                              =
      new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256")
    def sign(name: String, value: String): String            = {
      val mac      = javax.crypto.Mac.getInstance("HmacSHA256")
      mac.init(hmacKey)
      // Bind the signature to the cookie name to prevent cookie-swapping.
      val macInput = s"$name=$value"
      val sig      = java.util.Base64.getUrlEncoder.withoutPadding
        .encodeToString(mac.doFinal(macInput.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
      s"$value.$sig"
    }
    def verify(name: String, signed: String): Option[String] = {
      val dot = signed.lastIndexOf('.')
      if (dot < 0) None
      else {
        val value    = signed.substring(0, dot)
        val expected = sign(name, value)
        if (
          java.security.MessageDigest.isEqual(
            signed.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
          )
        ) Some(value)
        else None
      }
    }

    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
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
        wrap(routes) { (req, ctx, vars, scope, next) =>
          foldResult(next.handle(req, ctx, vars, scope))(
            r => if (predicate(r.status)) handler(r) else r,
            h => h,
          )
        }
    }

  // ═══════════════════════════════════════════════════════════════════
  // DUMP REQUEST / RESPONSE
  // ═══════════════════════════════════════════════════════════════════

  private def redactHeader(name: String, value: String): String = {
    val sensitive = Set(
      "authorization",
      "cookie",
      "set-cookie",
      "proxy-authorization",
      "x-api-key",
      "x-auth-token",
      "proxy-authenticate",
    )
    if (sensitive.contains(name.toLowerCase)) s"[REDACTED (len=${value.length})]" else value
  }

  private val MaxLoggedStreamedBytes = 4096

  /**
   * Wraps a streaming body so that up to [[MaxLoggedStreamedBytes]] bytes are
   * buffered while the stream is consumed; the buffered prefix is emitted via
   * `logger` on stream completion. Every byte still passes through unchanged.
   */
  private def tapLogged(body: zio.http.Body, logger: String => Unit): zio.http.Body = {
    val buf              = new java.io.ByteArrayOutputStream(MaxLoggedStreamedBytes)
    @volatile var capped = false
    val charset          = body.contentType.charset
      .map(_.toJava)
      .getOrElse(java.nio.charset.StandardCharsets.UTF_8)
    implicit val infer: zio.blocks.streams.JvmType.Infer[Object] =
      zio.blocks.streams.JvmType.Infer.anyRef
    val tapped                                                   = body.stream.tapEach { (chunk: Any) =>
      if (!capped) {
        val bytes: Array[Byte] = chunk match {
          case a: Array[Byte]                             => a
          case c: zio.blocks.chunk.Chunk[Byte @unchecked] =>
            c.toArray[Byte]
          case b: Byte                                    => Array(b)
          case other                                      =>
            String.valueOf(other).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        }
        val take               = math.min(bytes.length, MaxLoggedStreamedBytes - buf.size())
        if (take > 0) buf.write(bytes, 0, take)
        if (buf.size() >= MaxLoggedStreamedBytes) capped = true
      }
    }
    val ensured                                                  = tapped.ensuring {
      val text = new String(buf.toByteArray, charset)
      val msg  =
        if (capped) text + s"...[truncated at $MaxLoggedStreamedBytes bytes]"
        else text
      logger(msg)
    }
    zio.http.Body.fromStream(ensured)
  }

  private def renderBody(body: zio.http.Body, includeBody: Boolean): String = {
    if (!includeBody) "[body omitted — pass includeBody = true to log]"
    else
      body.length match {
        case Some(n) if n <= 8192L =>
          val s = body.text
          if (s.length > 2048) s.take(2048) + "...[truncated]" else s
        case Some(n)               => s"[materialized body, $n bytes]"
        case None                  => "[streaming body — content follows on completion]"
      }
  }

  /**
   * Logs a full dump of the incoming request. WARNING: Only wire to sanitized
   * loggers in production. Sensitive headers are redacted; body logging is
   * disabled by default.
   */
  def requestDump(logger: String => Unit = println, includeBody: Boolean = false): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
          val headersStr = req.headers.toList.map { case (k, v) => s"    $k: ${redactHeader(k, v)}" }.mkString("\n")
          val bodyStr    = renderBody(req.body, includeBody)
          logger(
            s"── Request ──\n  method: ${req.method}\n  path:   ${req.url}\n  headers:\n$headersStr\n  body:   $bodyStr",
          )
          val outReq     =
            if (includeBody && req.body.length.isEmpty) req.body(tapLogged(req.body, logger))
            else req
          next.handle(outReq, ctx, vars, scope)
        }
    }

  /**
   * Logs a full dump of the outgoing response. WARNING: Only wire to sanitized
   * loggers in production. Sensitive headers are redacted; body logging is
   * disabled by default.
   */
  def responseDump(logger: String => Unit = println, includeBody: Boolean = false): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        wrap(routes) { (req, ctx, vars, scope, next) =>
          val result                                 = next.handle(req, ctx, vars, scope)
          val logAndTap: Response => Response | Halt = { r =>
            val headersStr = r.headers.toList.map { case (k, v) => s"    $k: ${redactHeader(k, v)}" }.mkString("\n")
            val bodyStr    = renderBody(r.body, includeBody)
            logger(s"── Response ──\n  status: ${r.status}\n  headers:\n$headersStr\n  body:   $bodyStr")
            if (includeBody && r.body.length.isEmpty) r.body(tapLogged(r.body, logger))
            else r
          }
          foldResult(result)(logAndTap, h => { logger(s"── Halt ──"); h })
        }
    }
}
