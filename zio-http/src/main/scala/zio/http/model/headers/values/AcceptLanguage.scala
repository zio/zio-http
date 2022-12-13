package zio.http.model.headers.values

import zio.Chunk

/**
 * The Accept-Language request HTTP header indicates the natural language and
 * locale that the client prefers.
 */
sealed trait AcceptLanguage

object AcceptLanguage {

  case class AcceptedLanguage(language: String, weight: Option[Double]) extends AcceptLanguage

  case class AcceptedLanguages(languages: Chunk[AcceptLanguage]) extends AcceptLanguage

  case object AnyLanguage extends AcceptLanguage

  case object InvalidAcceptLanguageValue extends AcceptLanguage
}
