package zio.http.model.headers.values

import zio.Chunk

import scala.util.Try

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

  def fromAcceptLanguage(acceptLanguage: AcceptLanguage): Chunk[(String, Option[Double])] = acceptLanguage match {
    case AcceptedLanguage(language, weight) => Chunk.single((language, weight))
    case AcceptedLanguages(languages)       => languages.flatMap(fromAcceptLanguage)
    case AnyLanguage                        => Chunk.single(("*", None))
    case InvalidAcceptLanguageValue         => Chunk.empty
  }

  def toAcceptLanguage(value: Chunk[(String, Option[Double])]): AcceptLanguage = {
    val languages = value.map { case (language, weight) =>
      if (language.isEmpty) InvalidAcceptLanguageValue else AcceptedLanguage(language, weight)
    }
    if (languages.nonEmpty) AcceptedLanguages(languages) else InvalidAcceptLanguageValue
  }

  private def parseAcceptedLanguage(value: String): AcceptLanguage = {
    val weightIndex = value.indexOf(";q=")
    if (weightIndex != -1) {
      val language = value.substring(0, weightIndex)
      val weight   = value.substring(weightIndex + 3)
      AcceptedLanguage(
        language,
        Try(weight.toDouble).toOption
          .filter(w => w >= 0.0 && w <= 1.0),
      )
    } else AcceptedLanguage(value, None)
  }
}
