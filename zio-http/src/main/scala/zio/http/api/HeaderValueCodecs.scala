package zio.http.api

import zio.Chunk
import zio.http.api.internal.RichTextCodec
import zio.http.model.headers.values.AcceptLanguage

object HeaderValueCodecs {

  // accept encoding
  private val gzipCodec     = RichTextCodec.literalCI("gzip")
  private val deflateCodec  = RichTextCodec.literalCI("deflate")
  private val brCodec       = RichTextCodec.literalCI("br")
  private val identityCodec = RichTextCodec.literalCI("identity")
  private val compressCodec = RichTextCodec.literalCI("compress")
  private val starCodec     = RichTextCodec.literalCI("*")

  private val quantifier = RichTextCodec.literalCI(";q=") ~ RichTextCodec.double
  private val gzipCodecComplete: RichTextCodec[(String, Option[Double])] =
    ((gzipCodec ~ quantifier) | gzipCodec).transform(f, g)
  private val deflateCodecComplete  = ((deflateCodec ~ quantifier) | deflateCodec).transform(f, g)
  private val brCodecComplete       = ((brCodec ~ quantifier) | brCodec).transform(f, g)
  private val identityCodecComplete =
    ((identityCodec ~ quantifier) | identityCodec).transform(f, g)
  private val compressCodecComplete = ((compressCodec ~ quantifier) | compressCodec).transform(f, g)
  private val starCodecComplete     = ((starCodec ~ quantifier) | starCodec).transform(f, g)

  private val acceptEncodingCodecAlt: RichTextCodec[(String, Option[Double])] =
    RichTextCodec.enumeration(
      gzipCodecComplete,
      deflateCodecComplete,
      brCodecComplete,
      identityCodecComplete,
      compressCodecComplete,
      starCodecComplete,
    )

  val acceptEncodingCodec: RichTextCodec[Chunk[(String, Option[Double])]] =
    acceptEncodingCodecAlt.replsep(RichTextCodec.comma.unit(','))

  private def f(e: Either[(String, (String, Double)), String]): (String, Option[Double]) =
    e match {
      case Left((name, (_, value))) => (name, Some(value))
      case Right(name)              => (name, None)
    }

  private def g(e: (String, Option[Double])): Either[(String, (String, Double)), String] =
    e match {
      case (name, Some(value)) => Left((name, (";q=", value)))
      case (name, None)        => Right(name)
    }

  // accept language
  private val lang           =
    RichTextCodec.filter(c => c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '-' || c == '*').repeat
  private val quantifierLang = RichTextCodec.literalCI(";q=") ~ RichTextCodec.double
  private val langComplete: RichTextCodec[(String, Option[Double])] = ((lang ~ quantifierLang) | lang).transform(
    {
      case Left((name, (_, value))) => (name.mkString, Some(value))
      case Right(name)              => (name.mkString, None)
    },
    {
      case (name, Some(value)) => Left((Chunk.fromArray(name.toArray), (";q=", value)))
      case (name, None)        => Right(Chunk.fromArray(name.toArray))
    },
  )

  private val rawAcceptLanguageCodec: RichTextCodec[Chunk[(String, Option[Double])]] =
    ((langComplete ~ RichTextCodec.comma) | langComplete).repeat.transform(
      _.foldLeft(Chunk.empty[(String, Option[Double])]) {
        case (acc, Left(_))      => acc
        case (acc, Right(value)) =>
          acc :+ value
      },
      _.map(Right(_)),
    )

  val acceptLanguageCodec: RichTextCodec[AcceptLanguage] = rawAcceptLanguageCodec.transform(
    raw =>
      AcceptLanguage.AcceptedLanguages(raw.map { case (name, value) => AcceptLanguage.AcceptedLanguage(name, value) }),
    {
      case AcceptLanguage.AcceptedLanguages(values) =>
        values.map {
          case AcceptLanguage.AcceptedLanguage(name, value) => (name, value)
          case AcceptLanguage.AnyLanguage                   => ("*", None)
          case _                                            => ("*", None)

        }
      case _                                        => Chunk.empty
    },
  )

  // accept

  private val mediaTypeCodec: RichTextCodec[String] =
    RichTextCodec
      .filter(c => c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '/' || c == '*')
      .repeat
      .transform(
        _.mkString,
        s => Chunk.fromArray(s.toArray),
      )

  private val mediaTypeCodecWithQualifier: RichTextCodec[(String, Option[Double])] =
    (mediaTypeCodec ~ RichTextCodec
      .literalCI(";q=")
      .unit("") ~ RichTextCodec.double).transform(
      { case (name, value) =>
        (name.mkString, Some(value))
      },
      {
        case (name, Some(value)) => (name, value)
        case (name, None)        => (name, 0.0)

      },
    )

  val mediaTypeCodecAlternative: RichTextCodec[(String, Option[Double])] =
    (mediaTypeCodecWithQualifier | mediaTypeCodec)
      .transform(
        {
          case Left(value) => value
          case Right(name) => (name.mkString, None)
        },
        {
          case (name, Some(value)) => Left((name, Some(value)))
          case (name, None)        => Right(name)
        },
      )
  val acceptCodec = mediaTypeCodecAlternative.replsep(RichTextCodec.comma.unit(','))

}
