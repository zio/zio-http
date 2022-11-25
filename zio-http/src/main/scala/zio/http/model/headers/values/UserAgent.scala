package zio.http.model.headers.values

sealed trait UserAgent

/**
 * The "User-Agent" header field contains information about the user agent
 * originating the request, which is often used by servers to help identify the
 * scope of reported interoperability problems, to work around or tailor
 * responses to avoid particular user agent limitations, and for analytics
 * regarding browser or operating system use
 */
object UserAgent {
  final case class CompleteUserAgent(product: Product, comment: Option[Comment]) extends UserAgent
  final case class Product(name: String, version: Option[String])                extends UserAgent
  final case class Comment(comment: String)                                      extends UserAgent
  object InvalidUserAgent                                                        extends UserAgent

  def toUserAgent(userAgent: String): UserAgent = {
    val productRegex  = """(?i)([a-z0-9]+)(?:/([a-z0-9.]+))?""".r
    val commentRegex  = """(?i)\((.*)$""".r
    val completeRegex = s"""^(?i)([a-z0-9]+)(?:/([a-z0-9.]+))(.*)$$""".r

    userAgent match {
      case productRegex(name, version)           => Product(name, Option(version))
      case commentRegex(comment)                 => Comment(comment)
      case completeRegex(name, version, comment) =>
        CompleteUserAgent(Product(name, Option(version)), Option(Comment(comment)))
      case _                                     => InvalidUserAgent
    }
  }

  def fromUserAgent(userAgent: UserAgent): String = userAgent match {
    case CompleteUserAgent(product, comment) =>
      s"""${fromUserAgent(product)}${fromUserAgent(comment.getOrElse(Comment("")))}"""
    case Product(name, version)              => s"""$name${version.map("/" + _).getOrElse("")}"""
    case Comment(comment)                    => s" ($comment)"
    case InvalidUserAgent                    => ""
  }

}
