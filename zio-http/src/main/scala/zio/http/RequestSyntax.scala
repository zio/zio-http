package zio.http

import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.model.Method

private[zio] trait RequestSyntax {
  object -> {
    def unapply(request: Request): Option[(Method, Path)] =
      Option(request.method -> request.path)
  }
}
