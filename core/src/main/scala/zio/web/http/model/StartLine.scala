package zio.web.http.model

final case class StartLine(method: Method, uri: Uri, version: Version)
