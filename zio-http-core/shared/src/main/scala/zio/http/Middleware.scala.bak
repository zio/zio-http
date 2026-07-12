package zio.http

import scala.collection.immutable.Map
import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.Scope

trait Middleware[UpperCtx, Ctx] { self =>
  def apply(routes: Routes[Ctx]): Routes[UpperCtx]
  def andThen[UpperCtx2](that: Middleware[UpperCtx2, UpperCtx]): Middleware[UpperCtx2, Ctx] =
    new Middleware[UpperCtx2, Ctx] {
      def apply(routes: Routes[Ctx]): Routes[UpperCtx2] = that(self(routes))
    }
}

object Middleware {
  def identity[Ctx]: Middleware[Ctx, Ctx] = new Middleware[Ctx, Ctx] {
    def apply(routes: Routes[Ctx]): Routes[Ctx] = routes
  }

  inline def custom[F](inline f: F): Middleware[?, ?] = ${ MiddlewareMacro.customImpl[F]('f) }
  inline def intercept[F](inline f: F): Middleware[?, ?] = custom(f)
  inline def whenContext[F](inline f: F): Middleware[?, ?] = custom(f)

  // ═══════════════════════════════════════════════════════════════════
  // AUTH
  // ═══════════════════════════════════════════════════════════════════

  def basicAuth(realm: String = "Access to the resource", validate: (String, String) => Boolean): Middleware[Any, Any] = {
    val wwwAuth = Header.WWWAuthenticate("Basic", Map("realm" -> realm))
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler
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
          val next = route.handler
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

  def customAuth[A](validate: Request => Option[A], realm: String = "Access to the resource")(
    implicit ev: IsNominalType[A],
  ): Middleware[A, Any] = {
    val wwwAuth = Header.WWWAuthenticate("Bearer", Map("realm" -> realm))
    new Middleware[A, Any] {
      def apply(routes: Routes[Any]): Routes[A] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler.asInstanceOf[Handler[A, Any]]
          val wrapped = Handler.extracted[A, Any] { (req, ctx, vars, scope) =>
            validate(req) match {
              case Some(a) => next.handle(req, ctx.asInstanceOf[Context[Any]].add(a)(using ev).asInstanceOf[Context[A]], vars, scope)
              case None => Halt(Response.unauthorized.addHeader(wwwAuth))
            }
          }
          Route(route.pattern, wrapped)
        })
    }
  }

  def customAuthProviding[A](provide: Request => A)(implicit ev: IsNominalType[A]): Middleware[A, Any] =
    new Middleware[A, Any] {
      def apply(routes: Routes[Any]): Routes[A] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler.asInstanceOf[Handler[A, Any]]
          val wrapped = Handler.extracted[A, Any] { (req, ctx, vars, scope) =>
            val a = provide(req)
            next.handle(req, ctx.asInstanceOf[Context[Any]].add(a)(using ev).asInstanceOf[Context[A]], vars, scope)
          }
          Route(route.pattern, wrapped)
        })
    }

  def customAuthProvidingWith[A](provide: Request => A)(implicit ev: IsNominalType[A]): Middleware[A, Any] =
    customAuthProviding(provide)(using ev)

  // ═══════════════════════════════════════════════════════════════════
  // INTERCEPT
  // ═══════════════════════════════════════════════════════════════════

  def interceptHandler(interceptor: Request => Option[Response | Halt]): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            interceptor(req) match {
              case Some(result) => result
              case None         => next.handle(req, ctx, vars, scope)
            }
          }
          Route(route.pattern, wrapped)
        })
    }

  def interceptPatch(interceptor: Request => Option[Response | Halt], patcher: Response => Response = Predef.identity): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            interceptor(req) match {
              case Some(result) => result
              case None =>
                next.handle(req, ctx, vars, scope) match {
                  case r: Response => patcher(r)
                  case h: Halt     => h
                }
            }
          }
          Route(route.pattern, wrapped)
        })
    }

  def debug(logger: String => Unit = println): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            logger(s"> ${req.method} ${req.url}")
            val result = next.handle(req, ctx, vars, scope)
            result match {
              case r: Response => logger(s"< ${r.status} ${req.url}")
              case h: Halt     => logger(s"! HALT ${req.url}")
            }
            result
          }
          Route(route.pattern, wrapped)
        })
    }

  def timing(reporter: (Method, String, Long) => Unit): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val start = System.nanoTime()
            val result = next.handle(req, ctx, vars, scope)
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
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            if (predicate(req)) {
              val single = Routes(Route(route.pattern, Handler.extracted[Any, Any] { (_, _, _, _) => next.handle(req, ctx, vars, scope) }))
              middleware(single).routes.toList.head.handler.handle(req, ctx, vars, scope)
            } else next.handle(req, ctx, vars, scope)
          }
          Route(route.pattern, wrapped)
        })
    }

  def ifRequestThenElse(predicate: Request => Boolean, onTrue: Middleware[Any, Any], onFalse: Middleware[Any, Any]): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val selected = if (predicate(req)) onTrue else onFalse
            val single = Routes(Route(route.pattern, Handler.extracted[Any, Any] { (_, _, _, _) => next.handle(req, ctx, vars, scope) }))
            selected(single).routes.toList.head.handler.handle(req, ctx, vars, scope)
          }
          Route(route.pattern, wrapped)
        })
    }

  def ifRequestThen(predicate: Request => Boolean, onTrue: Middleware[Any, Any]): Middleware[Any, Any] =
    ifRequestThenElse(predicate, onTrue, Middleware.identity[Any])

  def methods(mapping: (Method, Middleware[Any, Any])*): Middleware[Any, Any] = {
    val default: Middleware[Any, Any] = Middleware.identity[Any]
    val map = mapping.toMap
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val mw = map.getOrElse(req.method, default)
            val single = Routes(Route(route.pattern, Handler.extracted[Any, Any] { (_, _, _, _) => next.handle(req, ctx, vars, scope) }))
            mw(single).routes.toList.head.handler.handle(req, ctx, vars, scope)
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
    allowedMethods: Set[zio.http.Method] = Set(Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.PATCH, Method.OPTIONS),
    allowedHeaders: Set[String] = Set("Content-Type", "Authorization", "X-Requested-With"),
    exposedHeaders: Set[String] = Set.empty,
    allowCredentials: Boolean = true,
    maxAge: java.time.Duration = java.time.Duration.ofHours(1),
  )

  def cors(config: CorsConfig = CorsConfig()): Middleware[Any, Any] = {
    val methodsVal = zio.blocks.chunk.Chunk.fromIterable(config.allowedMethods)
    val headersVal = zio.blocks.chunk.Chunk.fromIterable(config.allowedHeaders)
    val exposeHeadersVal = zio.blocks.chunk.Chunk.fromIterable(config.exposedHeaders)
    val maxAgeSeconds = config.maxAge.getSeconds

    def originAllowed(origin: String): Boolean =
      config.allowedOrigins.contains("*") || config.allowedOrigins.contains(origin)

    def corsPreflightHeaders(originStr: String): List[Header] = {
      val originHdr: Header = if (config.allowedOrigins.contains("*"))
        Header.AccessControlAllowOrigin.All
      else
        Header.AccessControlAllowOrigin.Specific(originStr)
      List(
        originHdr,
        Header.AccessControlAllowMethods(methodsVal),
        Header.AccessControlAllowHeaders(headersVal),
        Header.AccessControlAllowCredentials(config.allowCredentials),
        Header.AccessControlMaxAge(maxAgeSeconds),
      ) ++ (if (config.exposedHeaders.nonEmpty)
              List(Header.AccessControlExposeHeaders(exposeHeadersVal))
            else Nil)
    }

    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            req.header(Header.Origin) match {
              case Some(origin: Header.Origin) =>
                val originStr = origin.renderedValue
                if (!originAllowed(originStr)) {
                  Halt(Response.forbidden)
                } else if (req.method == Method.OPTIONS) {
                  // Preflight
                  val hdrs = corsPreflightHeaders(originStr)
                  val resp = hdrs.foldLeft(Response(Status.NoContent))((acc, h) => acc.addHeader(h))
                  resp
                } else {
                  val hdrs = corsPreflightHeaders(originStr)
                  next.handle(req, ctx, vars, scope) match {
                    case r: Response =>
                      hdrs.foldLeft(r)((acc, h) => acc.addHeader(h))
                    case h: Halt => h
                  }
                }
              case None => next.handle(req, ctx, vars, scope)
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
    def get(key: String): Option[String] = values.get(key)
    def +(kv: (String, String)): FlashMap = copy(values = values + kv)
    def isEmpty: Boolean = values.isEmpty
  }

  object FlashMap {
    val empty: FlashMap = FlashMap(Map.empty[String, String])
    def apply(values: (String, String)*): FlashMap = FlashMap(Map(values*))
    def fromMap(m: Map[String, String]): FlashMap = FlashMap(m)
  }

  def flashScope()(implicit ev: IsNominalType[FlashMap]): Middleware[FlashMap, Any] =
    new Middleware[FlashMap, Any] {
      def apply(routes: Routes[Any]): Routes[FlashMap] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler.asInstanceOf[Handler[FlashMap, Any]]
          val wrapped = Handler.extracted[FlashMap, Any] { (req, ctx, vars, scope) =>
            val incomingFlash: Map[String, String] = req.cookies.iterator
              .find(_.name == "flash")
              .map { c =>
                c.value.split("&").flatMap { pair =>
                  pair.split("=", 2) match {
                    case Array(k, v) => Some(java.net.URLDecoder.decode(k, "UTF-8") -> java.net.URLDecoder.decode(v, "UTF-8"))
                    case _ => None
                  }
                }.toMap
              }
              .getOrElse(Map.empty)
            val flash = FlashMap.fromMap(incomingFlash)
            val result = next.handle(req, ctx.asInstanceOf[Context[Any]].add(flash)(using ev).asInstanceOf[Context[FlashMap]], vars, scope)
            result match {
              case r: Response => r
              case h: Halt     => h
            }
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
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val task = new java.util.concurrent.Callable[Response | Halt] {
              def call(): Response | Halt = next.handle(req, ctx, vars, scope)
            }
            val future = java.util.concurrent.Executors.newSingleThreadExecutor().submit(task)
            try {
              future.get(millis, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch {
              case _: java.util.concurrent.TimeoutException =>
                future.cancel(true)
                Halt(Response(Status.ServiceUnavailable))
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
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val file = new java.io.File(directoryPath, req.path.toString.stripPrefix("/"))
            if (file.exists && !file.isDirectory) {
              val bytes = java.nio.file.Files.readAllBytes(file.toPath)
              Response(Status.Ok, Headers.empty, Body.fromArray(bytes))
            } else {
              next.handle(req, ctx, vars, scope)
            }
          }
          Route(route.pattern, wrapped)
        })
    }

  def serveResources(basePath: String = "public"): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val resourcePath = if (basePath.isEmpty) req.path.toString.stripPrefix("/") else s"$basePath/${req.path.toString.stripPrefix("/")}"
            val stream = getClass.getClassLoader.getResourceAsStream(resourcePath)
            if (stream != null) {
              val bytes = stream.readAllBytes()
              stream.close()
              Response(Status.Ok, Headers.empty, Body.fromArray(bytes))
            } else {
              next.handle(req, ctx, vars, scope)
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
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            next.handle(req, ctx, vars, scope) match {
              case r: Response => r.addHeader(header)
              case h: Halt     => h
            }
          }
          Route(route.pattern, wrapped)
        })
    }

  /** Adds a header by name and value to every response. */
  def addHeader(name: String, value: String): Middleware[Any, Any] =
    addHeader(Header.Custom(name, value))

  /**
   * Updates response headers using a transformation function.
   * `f` receives the current headers and returns modified headers.
   */
  def updateHeaders(f: Headers => Headers): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            next.handle(req, ctx, vars, scope) match {
              case r: Response => Response(r.status, f(r.headers), r.body, r.version)
              case h: Halt     => h
            }
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
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val oldPath = req.url.path
            val newSegments = oldPath.segments :+ segment
            val newPath = Path(newSegments, oldPath.hasLeadingSlash, oldPath.trailingSlash)
            val newUrl = URL(Option.empty, Option.empty, Option.empty, newPath, req.url.queryParams, Option.empty)
            val newReq = Request(req.method, newUrl, req.headers, req.body, req.version)
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
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val oldPath = req.url.path
            val newSegments = zio.blocks.chunk.Chunk(segment) ++ oldPath.segments
            val newPath = Path(newSegments, oldPath.hasLeadingSlash, oldPath.trailingSlash)
            val newUrl = URL(Option.empty, Option.empty, Option.empty, newPath, req.url.queryParams, Option.empty)
            val newReq = Request(req.method, newUrl, req.headers, req.body, req.version)
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
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val path = req.url.path
            val segments = path.segments
            val prefixSegs = prefix.stripPrefix("/").stripSuffix("/").split("/").filter(_.nonEmpty)
            if (segments.take(prefixSegs.length).toList.map(_.toString) == prefixSegs.toSeq) {
              val newSegments = segments.drop(prefixSegs.length)
              val newPath = Path(newSegments, hasLeadingSlash = true, trailingSlash = path.trailingSlash)
              val newUrl = URL(Option.empty, Option.empty, Option.empty, newPath, req.url.queryParams, Option.empty)
              val newReq = Request(req.method, newUrl, req.headers, req.body, req.version)
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
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val p = req.url.path
            if (p.trailingSlash && p.segments.nonEmpty) {
              val newPath = Path(p.segments, p.hasLeadingSlash, trailingSlash = false)
              val newUrl = URL(Option.empty, Option.empty, Option.empty, newPath, req.url.queryParams, Option.empty)
              val newReq = Request(req.method, newUrl, req.headers, req.body, req.version)
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
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val p = req.url.path
            if (!p.trailingSlash && p.segments.nonEmpty) {
              val newPath = Path(p.segments, p.hasLeadingSlash, trailingSlash = true)
              val newUrl = URL(Option.empty, Option.empty, Option.empty, newPath, req.url.queryParams, Option.empty)
              val newReq = Request(req.method, newUrl, req.headers, req.body, req.version)
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
   * Runs `effect` before every handler. If the effect returns a
   * `Response` or `Halt`, the handler is short-circuited.
   * If it returns `None`, the handler proceeds normally.
   */
  def runBefore(effect: Request => Option[Response | Halt]): Middleware[Any, Any] =
    this.interceptHandler((req: Request) => effect(req))

  /**
   * Runs `effect` after every handler. The effect receives the request
   * and the result from the downstream handler.
   */
  def runAfter(effect: (Request, Response | Halt) => Response | Halt): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler
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
  def redirectTemporary(location: String): Middleware[Any, Any] =
    redirect(Status.fromInt(302), location)

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
   * @param secret  The shared secret key for HMAC signing
   */
  def signCookies(secret: String): Middleware[Any, Any] = {
    val hmacKey = new javax.crypto.spec.SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256")
    def sign(value: String): String = {
      val mac = javax.crypto.Mac.getInstance("HmacSHA256")
      mac.init(hmacKey)
      val sig = java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(mac.doFinal(value.getBytes("UTF-8")))
      s"$value.$sig"
    }
    def verify(signed: String): Option[String] = {
      val dot = signed.lastIndexOf('.')
      if (dot < 0) None else {
        val value = signed.substring(0, dot)
        val expected = sign(value)
        if (signed == expected) Some(value) else None
      }
    }

    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            // Verify incoming cookies
            val verifiedReq = {
              val cookies = req.cookies
              val verified = cookies.flatMap { c =>
                verify(c.value) match {
                  case Some(orig) => Some(RequestCookie(c.name, orig))
                  case None => None
                }
              }
              // Reconstruct request with verified cookies
              if (verified.size == cookies.size) req
              else {
                // Add header with verified cookies
                val cookieHeader = verified.map(c => s"${c.name}=${c.value}").mkString("; ")
                req.addHeader("Cookie", cookieHeader)
              }
            }
            val result = next.handle(verifiedReq, ctx, vars, scope)
            // Sign outgoing set-cookie headers
            result match {
              case r: Response =>
                val signedCookies = r.cookies.map { c =>
                  ResponseCookie(c.name, sign(c.value), c.expires, c.domain, c.path, c.maxAge, c.isSecure, c.isHttpOnly, c.sameSite, c.isPartitioned, c.priority)
                }
                var resp = r
                signedCookies.foreach { c =>
                  resp = resp.addHeader(Header.Custom("Set-Cookie", c.name + "=" + c.value))
                }
                resp
              case h: Halt => h
            }
          }
          Route(route.pattern, wrapped)
        })
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // STATUS / CONDITIONAL RESPONSE
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Middleware that intercepts responses whose status code matches
   * `predicate` and replaces them via `handler`.
   *
   * Example: `Middleware.status(_ == 404, _ => Response.text("Not Found"))`
   */
  def status(predicate: Status => Boolean, handler: Response => Response | Halt): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply(routes: Routes[Any]): Routes[Any] =
        Routes.fromIterable(routes.routes.toList.map { route =>
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            next.handle(req, ctx, vars, scope) match {
              case r: Response if predicate(r.status.asInstanceOf[Status]) => handler(r)
              case other => other
            }
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
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            logger(s"── Request ──\n  method: ${req.method}\n  path:   ${req.url}\n  headers:\n${req.headers.toList.map { case (k, v) => s"    $k: $v" }.mkString("\n")}\n  body:   ${req.body}")
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
          val next = route.handler
          val wrapped = Handler.extracted[Any, Any] { (req, ctx, vars, scope) =>
            val result = next.handle(req, ctx, vars, scope)
            result match {
              case r: Response =>
                logger(s"── Response ──\n  status: ${r.status}\n  headers:\n${r.headers.toList.map { case (k, v) => s"    $k: $v" }.mkString("\n")}\n  body:   ${r.body}")
              case h: Halt =>
                logger(s"── Halt ──")
            }
            result
          }
          Route(route.pattern, wrapped)
        })
    }
}
