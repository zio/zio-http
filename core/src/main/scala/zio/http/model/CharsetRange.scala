package zio.http.model

final case class CharsetRange(cs: Charset, q: Option[QValue])
