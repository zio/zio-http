package zio.http

import zio.http.middleware.{HttpRouteMiddlewares, RequestHandlerMiddlewares}

object Middleware extends RequestHandlerMiddlewares with HttpRouteMiddlewares
