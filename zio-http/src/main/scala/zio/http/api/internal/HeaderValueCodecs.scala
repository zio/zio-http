package zio.http.api.internal

import zio.Chunk
import zio.http.model.headers.values.AcceptLanguage
import zio.http.model.headers.values.AcceptLanguage._

object HeaderValueCodecs {

  // accept encoding
  private val gzipCodec     = RichTextCodec.literalCI("gzip")
  private val deflateCodec  = RichTextCodec.literalCI("deflate")
  private val brCodec       = RichTextCodec.literalCI("br")
  private val identityCodec = RichTextCodec.literalCI("identity")
  private val compressCodec = RichTextCodec.literalCI("compress")
  private val starCodec     = RichTextCodec.literalCI("*")

  private val quantifier            = RichTextCodec.literalCI(";q=") ~ RichTextCodec.double
  private val gzipCodecComplete     = ((gzipCodec ~ quantifier) | gzipCodec).transform(f, g)
  private val deflateCodecComplete  = ((deflateCodec ~ quantifier) | deflateCodec).transform(f, g)
  private val brCodecComplete       = ((brCodec ~ quantifier) | brCodec).transform(f, g)
  private val identityCodecComplete =
    ((identityCodec ~ quantifier) | identityCodec).transform(f, g)
  private val compressCodecComplete = ((compressCodec ~ quantifier) | compressCodec).transform(f, g)
  private val starCodecComplete     = ((starCodec ~ quantifier) | starCodec).transform(f, g)

  private val acceptEncodingCodecAlt: RichTextCodec[(String, Option[Double])] =
    (gzipCodecComplete | deflateCodecComplete | brCodecComplete | identityCodecComplete | compressCodecComplete | starCodecComplete)
      .transform(
        {
          case Left(value)          =>
            value match {
              case Left(value)          =>
                value match {
                  case Left(value)          =>
                    value match {
                      case Left(value)          =>
                        value match {
                          case Left((name, value))  => (name, value)
                          case Right((name, value)) => (name, value)
                        }
                      case Right((name, value)) => (name, value)
                    }
                  case Right((name, value)) => (name, value)
                }
              case Right((name, value)) => (name, value)
            }
          case Right((name, value)) => (name, value)
        },
        {
          case (name, Some(value)) =>
            name match {
              case "gzip"     => Left(Left(Left(Left(Left((name, Some(value)))))))
              case "deflate"  => Left(Left(Left(Left(Right((name, Some(value)))))))
              case "br"       => Left(Left(Left(Right((name, Some(value))))))
              case "identity" => Left(Left(Right((name, Some(value)))))
              case "compress" => Left(Right((name, Some(value))))
              case "*"        => Right((name, Some(value)))
              case _          => Right((name, Some(value)))
            }

          case (name, None) =>
            name match {
              case "gzip"     => Left(Left(Left(Left(Left((name, None))))))
              case "deflate"  => Left(Left(Left(Left(Right((name, None))))))
              case "br"       => Left(Left(Left(Right((name, None)))))
              case "identity" => Left(Left(Right((name, None))))
              case "compress" => Left(Right((name, None)))
              case "*"        => Right((name, None))
              case _          => Right((name, None))
            }
        },
      )

  val acceptEncodingCodec: RichTextCodec[Chunk[(String, Option[Double])]] =
    ((acceptEncodingCodecAlt ~ RichTextCodec.semicolon) | acceptEncodingCodecAlt).repeat.transform(
      _.foldLeft(Chunk.empty[(String, Option[Double])]) {
        case (acc, Left(_))      => acc
        case (acc, Right(value)) =>
          acc :+ value
      },
      _.map(Right(_)),
    )

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
    raw => AcceptedLanguages(raw.map { case (name, value) => AcceptedLanguage(name, value) }),
    {
      case AcceptedLanguages(values) =>
        values.map {
          case AcceptedLanguage(name, value) => (name, value)
          case AnyLanguage                   => ("*", None)
          case _                             => ("*", None)

        }
      case _                         => Chunk.empty
    },
  )
}
