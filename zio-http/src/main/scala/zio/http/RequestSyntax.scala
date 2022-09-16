package zio.http

import zio.http.model.{Method, Path, Request}

private[zio] trait RequestSyntax {
  object -> {
    def unapply(request: Request): Option[(Method, Path)] =
      Option(request.method -> request.path)
  }
}
