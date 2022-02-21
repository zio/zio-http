package zhttp.http.query

import zhttp.http.QueryParameters
import zio.Chunk
import zio.schema.ast.SchemaAst
import zio.schema.{Schema, StandardType}

import java.time._
import java.util.UUID
import scala.annotation.tailrec
import scala.util.Try

object QueryParams {

  implicit class QueryParametersWrapper(queryParams: QueryParameters) {
    def decode[A](implicit schema: Schema[A]): Either[String, A] =
      CaseClassDecoder.decode(schema, queryParams.raw)
  }

  /**
   * Decodes a map of query params into a corresponding Case Class
   * @param run
   *   - execute the decoder
   * @tparam A
   */
  final case class CaseClassDecoder[+A](run: Map[String, List[String]] => Either[String, A]) { self =>

    def map[B](f: A => B): CaseClassDecoder[B] =
      CaseClassDecoder { value =>
        self.run(value).map(f)
      }

    def flatMap[B](f: A => CaseClassDecoder[B]): CaseClassDecoder[B] =
      CaseClassDecoder { value =>
        self.run(value).flatMap { a =>
          f(a).run(value)
        }
      }

  }

  /**
   * Decodes a pair of fieldName and value
   * @param run
   * @tparam A
   */
  final case class PrimitiveWrapperDecoder[+A](run: (String, List[String]) => Either[String, A]) { self =>
    def map[B](f: A => B): PrimitiveWrapperDecoder[B] =
      PrimitiveWrapperDecoder { (fieldName, value) =>
        self.run(fieldName, value).map(f)
      }

  }

  /**
   * Decodes a primitive
   * @param run
   * @tparam A
   */
  final case class LowPriorityDecoder[+A](run: String => Either[Throwable, A]) {
    self =>

    def map[B](f: A => B): LowPriorityDecoder[B] =
      LowPriorityDecoder { value =>
        self.run(value).map(f)
      }

  }

  object CaseClassDecoder {
    def fail(failure: String): CaseClassDecoder[Nothing] = CaseClassDecoder(_ => Left(failure))

    def decode[A](schema: Schema[A], raw: Map[String, List[String]]): Either[String, A] =
      decoder(schema)
        .run(raw)

    private[query] def decoder[A](schema: Schema[A]): CaseClassDecoder[A] = {
      schema match {
        case Schema.Fail(message, _)                                                           => fail(message)
        case lzy @ Schema.Lazy(_)                                                              => decoder(lzy.schema)
        case Schema.Meta(_, _)                                                                 => astDecoder
        case s: Schema.CaseClass1[_, A]                                                        => caseClass1Decoder(s)
        case s: Schema.CaseClass2[_, _, A]                                                     => caseClass2Decoder(s)
        case s: Schema.CaseClass3[_, _, _, A]                                                  => caseClass3Decoder(s)
        case s: Schema.CaseClass4[_, _, _, _, A]                                               => caseClass4Decoder(s)
        case s: Schema.CaseClass5[_, _, _, _, _, A]                                            => caseClass5Decoder(s)
        case s: Schema.CaseClass6[_, _, _, _, _, _, A]                                         => caseClass6Decoder(s)
        case s: Schema.CaseClass7[_, _, _, _, _, _, _, A]                                      => caseClass7Decoder(s)
        case s: Schema.CaseClass8[_, _, _, _, _, _, _, _, A]                                   => caseClass8Decoder(s)
        case s: Schema.CaseClass9[_, _, _, _, _, _, _, _, _, A]                                => caseClass9Decoder(s)
        case s: Schema.CaseClass10[_, _, _, _, _, _, _, _, _, _, A]                            => caseClass10Decoder(s)
        case s: Schema.CaseClass11[_, _, _, _, _, _, _, _, _, _, _, A]                         => caseClass11Decoder(s)
        case s: Schema.CaseClass12[_, _, _, _, _, _, _, _, _, _, _, _, A]                      => caseClass12Decoder(s)
        case s: Schema.CaseClass13[_, _, _, _, _, _, _, _, _, _, _, _, _, A]                   => caseClass13Decoder(s)
        case s: Schema.CaseClass14[_, _, _, _, _, _, _, _, _, _, _, _, _, _, A]                => caseClass14Decoder(s)
        case s: Schema.CaseClass15[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A]             => caseClass15Decoder(s)
        case s: Schema.CaseClass16[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A]          => caseClass16Decoder(s)
        case s: Schema.CaseClass17[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A]       => caseClass17Decoder(s)
        case s: Schema.CaseClass18[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A]    => caseClass18Decoder(s)
        case s: Schema.CaseClass19[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A] => caseClass19Decoder(s)
        case s: Schema.CaseClass20[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A]       =>
          caseClass20Decoder(s)
        case s: Schema.CaseClass21[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A]    =>
          caseClass21Decoder(s)
        case s: Schema.CaseClass22[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A] =>
          caseClass22Decoder(s)

        case err => fail(s"$err type is not supported.")
      }
    }

    private val astDecoder: CaseClassDecoder[Schema[_]] =
      decoder(Schema[SchemaAst]).map(_.toSchema)

    import scala.language.implicitConversions

    implicit def toEither[A0](op: Option[A0]): Either[String, A0] = op match {
      case Some(value) => Right(value)
      case None        => Left("The provided payload do not contains the expected value.")
    }

    private def extractField(
      label: String,
      map: Map[String, List[String]],
    ): Option[List[String]] =
      map.get(label)

    private[query] def caseClass1Decoder[A, Z](schema: Schema.CaseClass1[A, Z]): CaseClassDecoder[Z] = {
      val field = schema.field
      CaseClassDecoder { raw =>
        val data = toEither(extractField(field.label, raw))

        data match {
          case Left(value)  => Left(value)
          case Right(value) =>
            PrimitiveWrapperDecoder
              .decode(field.schema)
              .run(field.label, value)
              .map(e => schema.construct(e))
        }

      }

    }

    private[query] def caseClass2Decoder[A1, A2, Z](schema: Schema.CaseClass2[A1, A2, Z]): CaseClassDecoder[Z] = {
      val field1 = schema.field1
      val field2 = schema.field2
      CaseClassDecoder { raw =>
        for {
          f1 <- toEither(extractField(field1.label, raw))
          f2 <- toEither(extractField(field2.label, raw))
          d1 <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2 <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
        } yield {
          schema.construct(d1, d2)
        }
      }
    }

    private[query] def caseClass3Decoder[A1, A2, A3, Z](
      schema: Schema.CaseClass3[A1, A2, A3, Z],
    ): CaseClassDecoder[Z] = {
      val field1 = schema.field1
      val field2 = schema.field2
      val field3 = schema.field3
      CaseClassDecoder { raw =>
        for {
          f1 <- toEither(extractField(field1.label, raw))
          f2 <- toEither(extractField(field2.label, raw))
          f3 <- toEither(extractField(field3.label, raw))
          d1 <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2 <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3 <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
        } yield {
          schema.construct(d1, d2, d3)
        }
      }

    }

    private[query] def caseClass4Decoder[A1, A2, A3, A4, Z](
      schema: Schema.CaseClass4[A1, A2, A3, A4, Z],
    ): CaseClassDecoder[Z] = {
      val field1 = schema.field1
      val field2 = schema.field2
      val field3 = schema.field3
      val field4 = schema.field4
      CaseClassDecoder { raw =>
        for {
          f1 <- toEither(extractField(field1.label, raw))
          f2 <- toEither(extractField(field2.label, raw))
          f3 <- toEither(extractField(field3.label, raw))
          f4 <- toEither(extractField(field4.label, raw))
          d1 <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2 <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3 <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4 <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
        } yield {
          schema.construct(d1, d2, d3, d4)
        }
      }
    }

    private[query] def caseClass5Decoder[A1, A2, A3, A4, A5, Z](
      schema: Schema.CaseClass5[A1, A2, A3, A4, A5, Z],
    ): CaseClassDecoder[Z] = {
      val field1 = schema.field1
      val field2 = schema.field2
      val field3 = schema.field3
      val field4 = schema.field4
      val field5 = schema.field5
      CaseClassDecoder { raw =>
        for {
          f1 <- toEither(extractField(field1.label, raw))
          f2 <- toEither(extractField(field2.label, raw))
          f3 <- toEither(extractField(field3.label, raw))
          f4 <- toEither(extractField(field4.label, raw))
          f5 <- toEither(extractField(field5.label, raw))
          d1 <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2 <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3 <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4 <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5 <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
        } yield {
          schema.construct(d1, d2, d3, d4, d5)
        }
      }
    }

    private[query] def caseClass6Decoder[A1, A2, A3, A4, A5, A6, Z](
      schema: Schema.CaseClass6[A1, A2, A3, A4, A5, A6, Z],
    ): CaseClassDecoder[Z] = {
      val field1 = schema.field1
      val field2 = schema.field2
      val field3 = schema.field3
      val field4 = schema.field4
      val field5 = schema.field5
      val field6 = schema.field6
      CaseClassDecoder { raw =>
        for {
          f1 <- toEither(extractField(field1.label, raw))
          f2 <- toEither(extractField(field2.label, raw))
          f3 <- toEither(extractField(field3.label, raw))
          f4 <- toEither(extractField(field4.label, raw))
          f5 <- toEither(extractField(field5.label, raw))
          f6 <- toEither(extractField(field6.label, raw))
          d1 <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2 <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3 <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4 <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5 <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6 <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6)
        }
      }
    }

    private[query] def caseClass7Decoder[A1, A2, A3, A4, A5, A6, A7, Z](
      schema: Schema.CaseClass7[A1, A2, A3, A4, A5, A6, A7, Z],
    ): CaseClassDecoder[Z] = {
      val field1 = schema.field1
      val field2 = schema.field2
      val field3 = schema.field3
      val field4 = schema.field4
      val field5 = schema.field5
      val field6 = schema.field6
      val field7 = schema.field7
      CaseClassDecoder { raw =>
        for {
          f1 <- toEither(extractField(field1.label, raw))
          f2 <- toEither(extractField(field2.label, raw))
          f3 <- toEither(extractField(field3.label, raw))
          f4 <- toEither(extractField(field4.label, raw))
          f5 <- toEither(extractField(field5.label, raw))
          f6 <- toEither(extractField(field6.label, raw))
          f7 <- toEither(extractField(field7.label, raw))
          d1 <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2 <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3 <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4 <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5 <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6 <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7 <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6, d7)
        }
      }
    }

    private[query] def caseClass8Decoder[A1, A2, A3, A4, A5, A6, A7, A8, Z](
      schema: Schema.CaseClass8[A1, A2, A3, A4, A5, A6, A7, A8, Z],
    ): CaseClassDecoder[Z] = {
      val field1 = schema.field1
      val field2 = schema.field2
      val field3 = schema.field3
      val field4 = schema.field4
      val field5 = schema.field5
      val field6 = schema.field6
      val field7 = schema.field7
      val field8 = schema.field8
      CaseClassDecoder { raw =>
        for {
          f1 <- toEither(extractField(field1.label, raw))
          f2 <- toEither(extractField(field2.label, raw))
          f3 <- toEither(extractField(field3.label, raw))
          f4 <- toEither(extractField(field4.label, raw))
          f5 <- toEither(extractField(field5.label, raw))
          f6 <- toEither(extractField(field6.label, raw))
          f7 <- toEither(extractField(field7.label, raw))
          f8 <- toEither(extractField(field8.label, raw))
          d1 <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2 <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3 <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4 <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5 <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6 <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7 <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8 <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6, d7, d8)
        }
      }
    }

    private[query] def caseClass9Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, Z](
      schema: Schema.CaseClass9[A1, A2, A3, A4, A5, A6, A7, A8, A9, Z],
    ): CaseClassDecoder[Z] = {
      val field1 = schema.field1
      val field2 = schema.field2
      val field3 = schema.field3
      val field4 = schema.field4
      val field5 = schema.field5
      val field6 = schema.field6
      val field7 = schema.field7
      val field8 = schema.field8
      val field9 = schema.field9
      CaseClassDecoder { raw =>
        for {
          f1 <- toEither(extractField(field1.label, raw))
          f2 <- toEither(extractField(field2.label, raw))
          f3 <- toEither(extractField(field3.label, raw))
          f4 <- toEither(extractField(field4.label, raw))
          f5 <- toEither(extractField(field5.label, raw))
          f6 <- toEither(extractField(field6.label, raw))
          f7 <- toEither(extractField(field7.label, raw))
          f8 <- toEither(extractField(field8.label, raw))
          f9 <- toEither(extractField(field9.label, raw))
          d1 <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2 <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3 <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4 <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5 <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6 <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7 <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8 <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
          d9 <- PrimitiveWrapperDecoder.decode(field9.schema).run(field9.label, f9)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6, d7, d8, d9)
        }
      }
    }

    private[query] def caseClass10Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, Z](
      schema: Schema.CaseClass10[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, Z],
    ): CaseClassDecoder[Z] = {
      val field1  = schema.field1
      val field2  = schema.field2
      val field3  = schema.field3
      val field4  = schema.field4
      val field5  = schema.field5
      val field6  = schema.field6
      val field7  = schema.field7
      val field8  = schema.field8
      val field9  = schema.field9
      val field10 = schema.field10
      CaseClassDecoder { raw =>
        for {
          f1  <- toEither(extractField(field1.label, raw))
          f2  <- toEither(extractField(field2.label, raw))
          f3  <- toEither(extractField(field3.label, raw))
          f4  <- toEither(extractField(field4.label, raw))
          f5  <- toEither(extractField(field5.label, raw))
          f6  <- toEither(extractField(field6.label, raw))
          f7  <- toEither(extractField(field7.label, raw))
          f8  <- toEither(extractField(field8.label, raw))
          f9  <- toEither(extractField(field9.label, raw))
          f10 <- toEither(extractField(field10.label, raw))
          d1  <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2  <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3  <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4  <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5  <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6  <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7  <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8  <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
          d9  <- PrimitiveWrapperDecoder.decode(field9.schema).run(field9.label, f9)
          d10 <- PrimitiveWrapperDecoder.decode(field10.schema).run(field10.label, f10)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6, d7, d8, d9, d10)
        }
      }
    }

    private[query] def caseClass11Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, Z](
      schema: Schema.CaseClass11[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, Z],
    ): CaseClassDecoder[Z] = {
      val field1  = schema.field1
      val field2  = schema.field2
      val field3  = schema.field3
      val field4  = schema.field4
      val field5  = schema.field5
      val field6  = schema.field6
      val field7  = schema.field7
      val field8  = schema.field8
      val field9  = schema.field9
      val field10 = schema.field10
      val field11 = schema.field11
      CaseClassDecoder { raw =>
        for {
          f1  <- toEither(extractField(field1.label, raw))
          f2  <- toEither(extractField(field2.label, raw))
          f3  <- toEither(extractField(field3.label, raw))
          f4  <- toEither(extractField(field4.label, raw))
          f5  <- toEither(extractField(field5.label, raw))
          f6  <- toEither(extractField(field6.label, raw))
          f7  <- toEither(extractField(field7.label, raw))
          f8  <- toEither(extractField(field8.label, raw))
          f9  <- toEither(extractField(field9.label, raw))
          f10 <- toEither(extractField(field10.label, raw))
          f11 <- toEither(extractField(field11.label, raw))
          d1  <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2  <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3  <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4  <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5  <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6  <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7  <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8  <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
          d9  <- PrimitiveWrapperDecoder.decode(field9.schema).run(field9.label, f9)
          d10 <- PrimitiveWrapperDecoder.decode(field10.schema).run(field10.label, f10)
          d11 <- PrimitiveWrapperDecoder.decode(field11.schema).run(field11.label, f11)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11)
        }
      }
    }

    private[query] def caseClass12Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, Z](
      schema: Schema.CaseClass12[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, Z],
    ): CaseClassDecoder[Z] = {
      val field1  = schema.field1
      val field2  = schema.field2
      val field3  = schema.field3
      val field4  = schema.field4
      val field5  = schema.field5
      val field6  = schema.field6
      val field7  = schema.field7
      val field8  = schema.field8
      val field9  = schema.field9
      val field10 = schema.field10
      val field11 = schema.field11
      val field12 = schema.field12
      CaseClassDecoder { raw =>
        for {
          f1  <- toEither(extractField(field1.label, raw))
          f2  <- toEither(extractField(field2.label, raw))
          f3  <- toEither(extractField(field3.label, raw))
          f4  <- toEither(extractField(field4.label, raw))
          f5  <- toEither(extractField(field5.label, raw))
          f6  <- toEither(extractField(field6.label, raw))
          f7  <- toEither(extractField(field7.label, raw))
          f8  <- toEither(extractField(field8.label, raw))
          f9  <- toEither(extractField(field9.label, raw))
          f10 <- toEither(extractField(field10.label, raw))
          f11 <- toEither(extractField(field11.label, raw))
          f12 <- toEither(extractField(field12.label, raw))
          d1  <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2  <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3  <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4  <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5  <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6  <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7  <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8  <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
          d9  <- PrimitiveWrapperDecoder.decode(field9.schema).run(field9.label, f9)
          d10 <- PrimitiveWrapperDecoder.decode(field10.schema).run(field10.label, f10)
          d11 <- PrimitiveWrapperDecoder.decode(field11.schema).run(field11.label, f11)
          d12 <- PrimitiveWrapperDecoder.decode(field12.schema).run(field12.label, f12)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12)
        }
      }
    }

    private[query] def caseClass13Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, Z](
      schema: Schema.CaseClass13[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, Z],
    ): CaseClassDecoder[Z] = {
      val field1  = schema.field1
      val field2  = schema.field2
      val field3  = schema.field3
      val field4  = schema.field4
      val field5  = schema.field5
      val field6  = schema.field6
      val field7  = schema.field7
      val field8  = schema.field8
      val field9  = schema.field9
      val field10 = schema.field10
      val field11 = schema.field11
      val field12 = schema.field12
      val field13 = schema.field13
      CaseClassDecoder { raw =>
        for {
          f1  <- toEither(extractField(field1.label, raw))
          f2  <- toEither(extractField(field2.label, raw))
          f3  <- toEither(extractField(field3.label, raw))
          f4  <- toEither(extractField(field4.label, raw))
          f5  <- toEither(extractField(field5.label, raw))
          f6  <- toEither(extractField(field6.label, raw))
          f7  <- toEither(extractField(field7.label, raw))
          f8  <- toEither(extractField(field8.label, raw))
          f9  <- toEither(extractField(field9.label, raw))
          f10 <- toEither(extractField(field10.label, raw))
          f11 <- toEither(extractField(field11.label, raw))
          f12 <- toEither(extractField(field12.label, raw))
          f13 <- toEither(extractField(field13.label, raw))
          d1  <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2  <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3  <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4  <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5  <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6  <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7  <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8  <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
          d9  <- PrimitiveWrapperDecoder.decode(field9.schema).run(field9.label, f9)
          d10 <- PrimitiveWrapperDecoder.decode(field10.schema).run(field10.label, f10)
          d11 <- PrimitiveWrapperDecoder.decode(field11.schema).run(field11.label, f11)
          d12 <- PrimitiveWrapperDecoder.decode(field12.schema).run(field12.label, f12)
          d13 <- PrimitiveWrapperDecoder.decode(field13.schema).run(field13.label, f13)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13)
        }
      }
    }

    private[query] def caseClass14Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, Z](
      schema: Schema.CaseClass14[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, Z],
    ): CaseClassDecoder[Z] = {
      val field1  = schema.field1
      val field2  = schema.field2
      val field3  = schema.field3
      val field4  = schema.field4
      val field5  = schema.field5
      val field6  = schema.field6
      val field7  = schema.field7
      val field8  = schema.field8
      val field9  = schema.field9
      val field10 = schema.field10
      val field11 = schema.field11
      val field12 = schema.field12
      val field13 = schema.field13
      val field14 = schema.field14
      CaseClassDecoder { raw =>
        for {
          f1  <- toEither(extractField(field1.label, raw))
          f2  <- toEither(extractField(field2.label, raw))
          f3  <- toEither(extractField(field3.label, raw))
          f4  <- toEither(extractField(field4.label, raw))
          f5  <- toEither(extractField(field5.label, raw))
          f6  <- toEither(extractField(field6.label, raw))
          f7  <- toEither(extractField(field7.label, raw))
          f8  <- toEither(extractField(field8.label, raw))
          f9  <- toEither(extractField(field9.label, raw))
          f10 <- toEither(extractField(field10.label, raw))
          f11 <- toEither(extractField(field11.label, raw))
          f12 <- toEither(extractField(field12.label, raw))
          f13 <- toEither(extractField(field13.label, raw))
          f14 <- toEither(extractField(field14.label, raw))
          d1  <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2  <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3  <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4  <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5  <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6  <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7  <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8  <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
          d9  <- PrimitiveWrapperDecoder.decode(field9.schema).run(field9.label, f9)
          d10 <- PrimitiveWrapperDecoder.decode(field10.schema).run(field10.label, f10)
          d11 <- PrimitiveWrapperDecoder.decode(field11.schema).run(field11.label, f11)
          d12 <- PrimitiveWrapperDecoder.decode(field12.schema).run(field12.label, f12)
          d13 <- PrimitiveWrapperDecoder.decode(field13.schema).run(field13.label, f13)
          d14 <- PrimitiveWrapperDecoder.decode(field14.schema).run(field14.label, f14)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13, d14)
        }
      }
    }

    private[query] def caseClass15Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, Z](
      schema: Schema.CaseClass15[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, Z],
    ): CaseClassDecoder[Z] = {
      val field1  = schema.field1
      val field2  = schema.field2
      val field3  = schema.field3
      val field4  = schema.field4
      val field5  = schema.field5
      val field6  = schema.field6
      val field7  = schema.field7
      val field8  = schema.field8
      val field9  = schema.field9
      val field10 = schema.field10
      val field11 = schema.field11
      val field12 = schema.field12
      val field13 = schema.field13
      val field14 = schema.field14
      val field15 = schema.field15
      CaseClassDecoder { raw =>
        for {
          f1  <- toEither(extractField(field1.label, raw))
          f2  <- toEither(extractField(field2.label, raw))
          f3  <- toEither(extractField(field3.label, raw))
          f4  <- toEither(extractField(field4.label, raw))
          f5  <- toEither(extractField(field5.label, raw))
          f6  <- toEither(extractField(field6.label, raw))
          f7  <- toEither(extractField(field7.label, raw))
          f8  <- toEither(extractField(field8.label, raw))
          f9  <- toEither(extractField(field9.label, raw))
          f10 <- toEither(extractField(field10.label, raw))
          f11 <- toEither(extractField(field11.label, raw))
          f12 <- toEither(extractField(field12.label, raw))
          f13 <- toEither(extractField(field13.label, raw))
          f14 <- toEither(extractField(field14.label, raw))
          f15 <- toEither(extractField(field15.label, raw))
          d1  <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2  <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3  <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4  <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5  <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6  <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7  <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8  <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
          d9  <- PrimitiveWrapperDecoder.decode(field9.schema).run(field9.label, f9)
          d10 <- PrimitiveWrapperDecoder.decode(field10.schema).run(field10.label, f10)
          d11 <- PrimitiveWrapperDecoder.decode(field11.schema).run(field11.label, f11)
          d12 <- PrimitiveWrapperDecoder.decode(field12.schema).run(field12.label, f12)
          d13 <- PrimitiveWrapperDecoder.decode(field13.schema).run(field13.label, f13)
          d14 <- PrimitiveWrapperDecoder.decode(field14.schema).run(field14.label, f14)
          d15 <- PrimitiveWrapperDecoder.decode(field15.schema).run(field15.label, f15)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13, d14, d15)
        }
      }
    }

    private[query] def caseClass16Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, Z](
      schema: Schema.CaseClass16[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, Z],
    ) = {
      val field1  = schema.field1
      val field2  = schema.field2
      val field3  = schema.field3
      val field4  = schema.field4
      val field5  = schema.field5
      val field6  = schema.field6
      val field7  = schema.field7
      val field8  = schema.field8
      val field9  = schema.field9
      val field10 = schema.field10
      val field11 = schema.field11
      val field12 = schema.field12
      val field13 = schema.field13
      val field14 = schema.field14
      val field15 = schema.field15
      val field16 = schema.field16
      CaseClassDecoder { raw =>
        for {
          f1  <- toEither(extractField(field1.label, raw))
          f2  <- toEither(extractField(field2.label, raw))
          f3  <- toEither(extractField(field3.label, raw))
          f4  <- toEither(extractField(field4.label, raw))
          f5  <- toEither(extractField(field5.label, raw))
          f6  <- toEither(extractField(field6.label, raw))
          f7  <- toEither(extractField(field7.label, raw))
          f8  <- toEither(extractField(field8.label, raw))
          f9  <- toEither(extractField(field9.label, raw))
          f10 <- toEither(extractField(field10.label, raw))
          f11 <- toEither(extractField(field11.label, raw))
          f12 <- toEither(extractField(field12.label, raw))
          f13 <- toEither(extractField(field13.label, raw))
          f14 <- toEither(extractField(field14.label, raw))
          f15 <- toEither(extractField(field15.label, raw))
          f16 <- toEither(extractField(field16.label, raw))
          d1  <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2  <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3  <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4  <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5  <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6  <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7  <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8  <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
          d9  <- PrimitiveWrapperDecoder.decode(field9.schema).run(field9.label, f9)
          d10 <- PrimitiveWrapperDecoder.decode(field10.schema).run(field10.label, f10)
          d11 <- PrimitiveWrapperDecoder.decode(field11.schema).run(field11.label, f11)
          d12 <- PrimitiveWrapperDecoder.decode(field12.schema).run(field12.label, f12)
          d13 <- PrimitiveWrapperDecoder.decode(field13.schema).run(field13.label, f13)
          d14 <- PrimitiveWrapperDecoder.decode(field14.schema).run(field14.label, f14)
          d15 <- PrimitiveWrapperDecoder.decode(field15.schema).run(field15.label, f15)
          d16 <- PrimitiveWrapperDecoder.decode(field16.schema).run(field16.label, f16)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13, d14, d15, d16)
        }
      }
    }

    private[query] def caseClass17Decoder[
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      Z,
    ](
      schema: Schema.CaseClass17[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, Z],
    ): CaseClassDecoder[Z] = {
      val field1  = schema.field1
      val field2  = schema.field2
      val field3  = schema.field3
      val field4  = schema.field4
      val field5  = schema.field5
      val field6  = schema.field6
      val field7  = schema.field7
      val field8  = schema.field8
      val field9  = schema.field9
      val field10 = schema.field10
      val field11 = schema.field11
      val field12 = schema.field12
      val field13 = schema.field13
      val field14 = schema.field14
      val field15 = schema.field15
      val field16 = schema.field16
      val field17 = schema.field17
      CaseClassDecoder { raw =>
        for {
          f1  <- toEither(extractField(field1.label, raw))
          f2  <- toEither(extractField(field2.label, raw))
          f3  <- toEither(extractField(field3.label, raw))
          f4  <- toEither(extractField(field4.label, raw))
          f5  <- toEither(extractField(field5.label, raw))
          f6  <- toEither(extractField(field6.label, raw))
          f7  <- toEither(extractField(field7.label, raw))
          f8  <- toEither(extractField(field8.label, raw))
          f9  <- toEither(extractField(field9.label, raw))
          f10 <- toEither(extractField(field10.label, raw))
          f11 <- toEither(extractField(field11.label, raw))
          f12 <- toEither(extractField(field12.label, raw))
          f13 <- toEither(extractField(field13.label, raw))
          f14 <- toEither(extractField(field14.label, raw))
          f15 <- toEither(extractField(field15.label, raw))
          f16 <- toEither(extractField(field16.label, raw))
          f17 <- toEither(extractField(field17.label, raw))
          d1  <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2  <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3  <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4  <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5  <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6  <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7  <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8  <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
          d9  <- PrimitiveWrapperDecoder.decode(field9.schema).run(field9.label, f9)
          d10 <- PrimitiveWrapperDecoder.decode(field10.schema).run(field10.label, f10)
          d11 <- PrimitiveWrapperDecoder.decode(field11.schema).run(field11.label, f11)
          d12 <- PrimitiveWrapperDecoder.decode(field12.schema).run(field12.label, f12)
          d13 <- PrimitiveWrapperDecoder.decode(field13.schema).run(field13.label, f13)
          d14 <- PrimitiveWrapperDecoder.decode(field14.schema).run(field14.label, f14)
          d15 <- PrimitiveWrapperDecoder.decode(field15.schema).run(field15.label, f15)
          d16 <- PrimitiveWrapperDecoder.decode(field16.schema).run(field16.label, f16)
          d17 <- PrimitiveWrapperDecoder.decode(field17.schema).run(field17.label, f17)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13, d14, d15, d16, d17)
        }
      }
    }

    private[query] def caseClass18Decoder[
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18,
      Z,
    ](
      schema: Schema.CaseClass18[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, Z],
    ): CaseClassDecoder[Z] = {
      val field1  = schema.field1
      val field2  = schema.field2
      val field3  = schema.field3
      val field4  = schema.field4
      val field5  = schema.field5
      val field6  = schema.field6
      val field7  = schema.field7
      val field8  = schema.field8
      val field9  = schema.field9
      val field10 = schema.field10
      val field11 = schema.field11
      val field12 = schema.field12
      val field13 = schema.field13
      val field14 = schema.field14
      val field15 = schema.field15
      val field16 = schema.field16
      val field17 = schema.field17
      val field18 = schema.field18
      CaseClassDecoder { raw =>
        for {
          f1  <- toEither(extractField(field1.label, raw))
          f2  <- toEither(extractField(field2.label, raw))
          f3  <- toEither(extractField(field3.label, raw))
          f4  <- toEither(extractField(field4.label, raw))
          f5  <- toEither(extractField(field5.label, raw))
          f6  <- toEither(extractField(field6.label, raw))
          f7  <- toEither(extractField(field7.label, raw))
          f8  <- toEither(extractField(field8.label, raw))
          f9  <- toEither(extractField(field9.label, raw))
          f10 <- toEither(extractField(field10.label, raw))
          f11 <- toEither(extractField(field11.label, raw))
          f12 <- toEither(extractField(field12.label, raw))
          f13 <- toEither(extractField(field13.label, raw))
          f14 <- toEither(extractField(field14.label, raw))
          f15 <- toEither(extractField(field15.label, raw))
          f16 <- toEither(extractField(field16.label, raw))
          f17 <- toEither(extractField(field17.label, raw))
          f18 <- toEither(extractField(field18.label, raw))
          d1  <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2  <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3  <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4  <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5  <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6  <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7  <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8  <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
          d9  <- PrimitiveWrapperDecoder.decode(field9.schema).run(field9.label, f9)
          d10 <- PrimitiveWrapperDecoder.decode(field10.schema).run(field10.label, f10)
          d11 <- PrimitiveWrapperDecoder.decode(field11.schema).run(field11.label, f11)
          d12 <- PrimitiveWrapperDecoder.decode(field12.schema).run(field12.label, f12)
          d13 <- PrimitiveWrapperDecoder.decode(field13.schema).run(field13.label, f13)
          d14 <- PrimitiveWrapperDecoder.decode(field14.schema).run(field14.label, f14)
          d15 <- PrimitiveWrapperDecoder.decode(field15.schema).run(field15.label, f15)
          d16 <- PrimitiveWrapperDecoder.decode(field16.schema).run(field16.label, f16)
          d17 <- PrimitiveWrapperDecoder.decode(field17.schema).run(field17.label, f17)
          d18 <- PrimitiveWrapperDecoder.decode(field18.schema).run(field18.label, f18)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13, d14, d15, d16, d17, d18)
        }
      }
    }

    private[query] def caseClass19Decoder[
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18,
      A19,
      Z,
    ](
      schema: Schema.CaseClass19[
        A1,
        A2,
        A3,
        A4,
        A5,
        A6,
        A7,
        A8,
        A9,
        A10,
        A11,
        A12,
        A13,
        A14,
        A15,
        A16,
        A17,
        A18,
        A19,
        Z,
      ],
    ): CaseClassDecoder[Z] = {
      val field1  = schema.field1
      val field2  = schema.field2
      val field3  = schema.field3
      val field4  = schema.field4
      val field5  = schema.field5
      val field6  = schema.field6
      val field7  = schema.field7
      val field8  = schema.field8
      val field9  = schema.field9
      val field10 = schema.field10
      val field11 = schema.field11
      val field12 = schema.field12
      val field13 = schema.field13
      val field14 = schema.field14
      val field15 = schema.field15
      val field16 = schema.field16
      val field17 = schema.field17
      val field18 = schema.field18
      val field19 = schema.field19
      CaseClassDecoder { raw =>
        for {
          f1  <- toEither(extractField(field1.label, raw))
          f2  <- toEither(extractField(field2.label, raw))
          f3  <- toEither(extractField(field3.label, raw))
          f4  <- toEither(extractField(field4.label, raw))
          f5  <- toEither(extractField(field5.label, raw))
          f6  <- toEither(extractField(field6.label, raw))
          f7  <- toEither(extractField(field7.label, raw))
          f8  <- toEither(extractField(field8.label, raw))
          f9  <- toEither(extractField(field9.label, raw))
          f10 <- toEither(extractField(field10.label, raw))
          f11 <- toEither(extractField(field11.label, raw))
          f12 <- toEither(extractField(field12.label, raw))
          f13 <- toEither(extractField(field13.label, raw))
          f14 <- toEither(extractField(field14.label, raw))
          f15 <- toEither(extractField(field15.label, raw))
          f16 <- toEither(extractField(field16.label, raw))
          f17 <- toEither(extractField(field17.label, raw))
          f18 <- toEither(extractField(field18.label, raw))
          f19 <- toEither(extractField(field19.label, raw))
          d1  <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2  <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3  <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4  <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5  <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6  <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7  <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8  <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
          d9  <- PrimitiveWrapperDecoder.decode(field9.schema).run(field9.label, f9)
          d10 <- PrimitiveWrapperDecoder.decode(field10.schema).run(field10.label, f10)
          d11 <- PrimitiveWrapperDecoder.decode(field11.schema).run(field11.label, f11)
          d12 <- PrimitiveWrapperDecoder.decode(field12.schema).run(field12.label, f12)
          d13 <- PrimitiveWrapperDecoder.decode(field13.schema).run(field13.label, f13)
          d14 <- PrimitiveWrapperDecoder.decode(field14.schema).run(field14.label, f14)
          d15 <- PrimitiveWrapperDecoder.decode(field15.schema).run(field15.label, f15)
          d16 <- PrimitiveWrapperDecoder.decode(field16.schema).run(field16.label, f16)
          d17 <- PrimitiveWrapperDecoder.decode(field17.schema).run(field17.label, f17)
          d18 <- PrimitiveWrapperDecoder.decode(field18.schema).run(field18.label, f18)
          d19 <- PrimitiveWrapperDecoder.decode(field19.schema).run(field19.label, f19)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13, d14, d15, d16, d17, d18, d19)
        }
      }
    }

    private[query] def caseClass20Decoder[
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18,
      A19,
      A20,
      Z,
    ](
      schema: Schema.CaseClass20[
        A1,
        A2,
        A3,
        A4,
        A5,
        A6,
        A7,
        A8,
        A9,
        A10,
        A11,
        A12,
        A13,
        A14,
        A15,
        A16,
        A17,
        A18,
        A19,
        A20,
        Z,
      ],
    ): CaseClassDecoder[Z] = {
      val field1  = schema.field1
      val field2  = schema.field2
      val field3  = schema.field3
      val field4  = schema.field4
      val field5  = schema.field5
      val field6  = schema.field6
      val field7  = schema.field7
      val field8  = schema.field8
      val field9  = schema.field9
      val field10 = schema.field10
      val field11 = schema.field11
      val field12 = schema.field12
      val field13 = schema.field13
      val field14 = schema.field14
      val field15 = schema.field15
      val field16 = schema.field16
      val field17 = schema.field17
      val field18 = schema.field18
      val field19 = schema.field19
      val field20 = schema.field20
      CaseClassDecoder { raw =>
        for {
          f1  <- toEither(extractField(field1.label, raw))
          f2  <- toEither(extractField(field2.label, raw))
          f3  <- toEither(extractField(field3.label, raw))
          f4  <- toEither(extractField(field4.label, raw))
          f5  <- toEither(extractField(field5.label, raw))
          f6  <- toEither(extractField(field6.label, raw))
          f7  <- toEither(extractField(field7.label, raw))
          f8  <- toEither(extractField(field8.label, raw))
          f9  <- toEither(extractField(field9.label, raw))
          f10 <- toEither(extractField(field10.label, raw))
          f11 <- toEither(extractField(field11.label, raw))
          f12 <- toEither(extractField(field12.label, raw))
          f13 <- toEither(extractField(field13.label, raw))
          f14 <- toEither(extractField(field14.label, raw))
          f15 <- toEither(extractField(field15.label, raw))
          f16 <- toEither(extractField(field16.label, raw))
          f17 <- toEither(extractField(field17.label, raw))
          f18 <- toEither(extractField(field18.label, raw))
          f19 <- toEither(extractField(field19.label, raw))
          f20 <- toEither(extractField(field20.label, raw))
          d1  <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2  <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3  <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4  <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5  <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6  <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7  <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8  <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
          d9  <- PrimitiveWrapperDecoder.decode(field9.schema).run(field9.label, f9)
          d10 <- PrimitiveWrapperDecoder.decode(field10.schema).run(field10.label, f10)
          d11 <- PrimitiveWrapperDecoder.decode(field11.schema).run(field11.label, f11)
          d12 <- PrimitiveWrapperDecoder.decode(field12.schema).run(field12.label, f12)
          d13 <- PrimitiveWrapperDecoder.decode(field13.schema).run(field13.label, f13)
          d14 <- PrimitiveWrapperDecoder.decode(field14.schema).run(field14.label, f14)
          d15 <- PrimitiveWrapperDecoder.decode(field15.schema).run(field15.label, f15)
          d16 <- PrimitiveWrapperDecoder.decode(field16.schema).run(field16.label, f16)
          d17 <- PrimitiveWrapperDecoder.decode(field17.schema).run(field17.label, f17)
          d18 <- PrimitiveWrapperDecoder.decode(field18.schema).run(field18.label, f18)
          d19 <- PrimitiveWrapperDecoder.decode(field19.schema).run(field19.label, f19)
          d20 <- PrimitiveWrapperDecoder.decode(field20.schema).run(field20.label, f20)
        } yield {
          schema.construct(d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13, d14, d15, d16, d17, d18, d19, d20)
        }
      }
    }

    private[query] def caseClass21Decoder[
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18,
      A19,
      A20,
      A21,
      Z,
    ](
      schema: Schema.CaseClass21[
        A1,
        A2,
        A3,
        A4,
        A5,
        A6,
        A7,
        A8,
        A9,
        A10,
        A11,
        A12,
        A13,
        A14,
        A15,
        A16,
        A17,
        A18,
        A19,
        A20,
        A21,
        Z,
      ],
    ): CaseClassDecoder[Z] = {
      val field1  = schema.field1
      val field2  = schema.field2
      val field3  = schema.field3
      val field4  = schema.field4
      val field5  = schema.field5
      val field6  = schema.field6
      val field7  = schema.field7
      val field8  = schema.field8
      val field9  = schema.field9
      val field10 = schema.field10
      val field11 = schema.field11
      val field12 = schema.field12
      val field13 = schema.field13
      val field14 = schema.field14
      val field15 = schema.field15
      val field16 = schema.field16
      val field17 = schema.field17
      val field18 = schema.field18
      val field19 = schema.field19
      val field20 = schema.field20
      val field21 = schema.field21
      CaseClassDecoder { raw =>
        for {
          f1  <- toEither(extractField(field1.label, raw))
          f2  <- toEither(extractField(field2.label, raw))
          f3  <- toEither(extractField(field3.label, raw))
          f4  <- toEither(extractField(field4.label, raw))
          f5  <- toEither(extractField(field5.label, raw))
          f6  <- toEither(extractField(field6.label, raw))
          f7  <- toEither(extractField(field7.label, raw))
          f8  <- toEither(extractField(field8.label, raw))
          f9  <- toEither(extractField(field9.label, raw))
          f10 <- toEither(extractField(field10.label, raw))
          f11 <- toEither(extractField(field11.label, raw))
          f12 <- toEither(extractField(field12.label, raw))
          f13 <- toEither(extractField(field13.label, raw))
          f14 <- toEither(extractField(field14.label, raw))
          f15 <- toEither(extractField(field15.label, raw))
          f16 <- toEither(extractField(field16.label, raw))
          f17 <- toEither(extractField(field17.label, raw))
          f18 <- toEither(extractField(field18.label, raw))
          f19 <- toEither(extractField(field19.label, raw))
          f20 <- toEither(extractField(field20.label, raw))
          f21 <- toEither(extractField(field21.label, raw))
          d1  <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2  <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3  <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4  <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5  <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6  <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7  <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8  <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
          d9  <- PrimitiveWrapperDecoder.decode(field9.schema).run(field9.label, f9)
          d10 <- PrimitiveWrapperDecoder.decode(field10.schema).run(field10.label, f10)
          d11 <- PrimitiveWrapperDecoder.decode(field11.schema).run(field11.label, f11)
          d12 <- PrimitiveWrapperDecoder.decode(field12.schema).run(field12.label, f12)
          d13 <- PrimitiveWrapperDecoder.decode(field13.schema).run(field13.label, f13)
          d14 <- PrimitiveWrapperDecoder.decode(field14.schema).run(field14.label, f14)
          d15 <- PrimitiveWrapperDecoder.decode(field15.schema).run(field15.label, f15)
          d16 <- PrimitiveWrapperDecoder.decode(field16.schema).run(field16.label, f16)
          d17 <- PrimitiveWrapperDecoder.decode(field17.schema).run(field17.label, f17)
          d18 <- PrimitiveWrapperDecoder.decode(field18.schema).run(field18.label, f18)
          d19 <- PrimitiveWrapperDecoder.decode(field19.schema).run(field19.label, f19)
          d20 <- PrimitiveWrapperDecoder.decode(field20.schema).run(field20.label, f20)
          d21 <- PrimitiveWrapperDecoder.decode(field21.schema).run(field21.label, f21)
        } yield {
          schema.construct(
            d1,
            d2,
            d3,
            d4,
            d5,
            d6,
            d7,
            d8,
            d9,
            d10,
            d11,
            d12,
            d13,
            d14,
            d15,
            d16,
            d17,
            d18,
            d19,
            d20,
            d21,
          )
        }
      }
    }

    private[query] def caseClass22Decoder[
      A1,
      A2,
      A3,
      A4,
      A5,
      A6,
      A7,
      A8,
      A9,
      A10,
      A11,
      A12,
      A13,
      A14,
      A15,
      A16,
      A17,
      A18,
      A19,
      A20,
      A21,
      A22,
      Z,
    ](
      schema: Schema.CaseClass22[
        A1,
        A2,
        A3,
        A4,
        A5,
        A6,
        A7,
        A8,
        A9,
        A10,
        A11,
        A12,
        A13,
        A14,
        A15,
        A16,
        A17,
        A18,
        A19,
        A20,
        A21,
        A22,
        Z,
      ],
    ): CaseClassDecoder[Z] = {
      val field1  = schema.field1
      val field2  = schema.field2
      val field3  = schema.field3
      val field4  = schema.field4
      val field5  = schema.field5
      val field6  = schema.field6
      val field7  = schema.field7
      val field8  = schema.field8
      val field9  = schema.field9
      val field10 = schema.field10
      val field11 = schema.field11
      val field12 = schema.field12
      val field13 = schema.field13
      val field14 = schema.field14
      val field15 = schema.field15
      val field16 = schema.field16
      val field17 = schema.field17
      val field18 = schema.field18
      val field19 = schema.field19
      val field20 = schema.field20
      val field21 = schema.field21
      val field22 = schema.field22
      CaseClassDecoder { raw =>
        for {
          f1  <- toEither(extractField(field1.label, raw))
          f2  <- toEither(extractField(field2.label, raw))
          f3  <- toEither(extractField(field3.label, raw))
          f4  <- toEither(extractField(field4.label, raw))
          f5  <- toEither(extractField(field5.label, raw))
          f6  <- toEither(extractField(field6.label, raw))
          f7  <- toEither(extractField(field7.label, raw))
          f8  <- toEither(extractField(field8.label, raw))
          f9  <- toEither(extractField(field9.label, raw))
          f10 <- toEither(extractField(field10.label, raw))
          f11 <- toEither(extractField(field11.label, raw))
          f12 <- toEither(extractField(field12.label, raw))
          f13 <- toEither(extractField(field13.label, raw))
          f14 <- toEither(extractField(field14.label, raw))
          f15 <- toEither(extractField(field15.label, raw))
          f16 <- toEither(extractField(field16.label, raw))
          f17 <- toEither(extractField(field17.label, raw))
          f18 <- toEither(extractField(field18.label, raw))
          f19 <- toEither(extractField(field19.label, raw))
          f20 <- toEither(extractField(field20.label, raw))
          f21 <- toEither(extractField(field21.label, raw))
          f22 <- toEither(extractField(field22.label, raw))
          d1  <- PrimitiveWrapperDecoder.decode(field1.schema).run(field1.label, f1)
          d2  <- PrimitiveWrapperDecoder.decode(field2.schema).run(field2.label, f2)
          d3  <- PrimitiveWrapperDecoder.decode(field3.schema).run(field3.label, f3)
          d4  <- PrimitiveWrapperDecoder.decode(field4.schema).run(field4.label, f4)
          d5  <- PrimitiveWrapperDecoder.decode(field5.schema).run(field5.label, f5)
          d6  <- PrimitiveWrapperDecoder.decode(field6.schema).run(field6.label, f6)
          d7  <- PrimitiveWrapperDecoder.decode(field7.schema).run(field7.label, f7)
          d8  <- PrimitiveWrapperDecoder.decode(field8.schema).run(field8.label, f8)
          d9  <- PrimitiveWrapperDecoder.decode(field9.schema).run(field9.label, f9)
          d10 <- PrimitiveWrapperDecoder.decode(field10.schema).run(field10.label, f10)
          d11 <- PrimitiveWrapperDecoder.decode(field11.schema).run(field11.label, f11)
          d12 <- PrimitiveWrapperDecoder.decode(field12.schema).run(field12.label, f12)
          d13 <- PrimitiveWrapperDecoder.decode(field13.schema).run(field13.label, f13)
          d14 <- PrimitiveWrapperDecoder.decode(field14.schema).run(field14.label, f14)
          d15 <- PrimitiveWrapperDecoder.decode(field15.schema).run(field15.label, f15)
          d16 <- PrimitiveWrapperDecoder.decode(field16.schema).run(field16.label, f16)
          d17 <- PrimitiveWrapperDecoder.decode(field17.schema).run(field17.label, f17)
          d18 <- PrimitiveWrapperDecoder.decode(field18.schema).run(field18.label, f18)
          d19 <- PrimitiveWrapperDecoder.decode(field19.schema).run(field19.label, f19)
          d20 <- PrimitiveWrapperDecoder.decode(field20.schema).run(field20.label, f20)
          d21 <- PrimitiveWrapperDecoder.decode(field21.schema).run(field21.label, f21)
          d22 <- PrimitiveWrapperDecoder.decode(field22.schema).run(field22.label, f22)
        } yield {
          schema.construct(
            d1,
            d2,
            d3,
            d4,
            d5,
            d6,
            d7,
            d8,
            d9,
            d10,
            d11,
            d12,
            d13,
            d14,
            d15,
            d16,
            d17,
            d18,
            d19,
            d20,
            d21,
            d22,
          )
        }
      }
    }
  }

  object LowPriorityDecoder {

    def fail(failure: String): LowPriorityDecoder[Nothing] = LowPriorityDecoder(_ => Left(new Exception(failure)))

    private[query] val binaryDecoder: LowPriorityDecoder[Chunk[Byte]] =
      LowPriorityDecoder(value => Right(Chunk.fromArray(value.getBytes())))
    private[query] val intDecoder: LowPriorityDecoder[Int]   = LowPriorityDecoder(value => Try(value.toInt).toEither)
    private[query] val longDecoder: LowPriorityDecoder[Long] = LowPriorityDecoder(value => Try(value.toLong).toEither)
    private[query] val stringDecoder: LowPriorityDecoder[String] =
      LowPriorityDecoder(value => Right(value))

    def decode[A](standardType: StandardType[A]): LowPriorityDecoder[A] =
      primitiveDecoder(standardType)

    private def primitiveDecoder[A](standardType: StandardType[A]): LowPriorityDecoder[A] =
      standardType match {
        case StandardType.UnitType   => LowPriorityDecoder(_ => Right(()))
        case StandardType.StringType => stringDecoder
        case StandardType.BoolType   => LowPriorityDecoder(value => Try(value.toBoolean).toEither)
        case StandardType.ShortType  => LowPriorityDecoder(value => Try(value.toShort).toEither)
        case StandardType.IntType    => intDecoder
        case StandardType.LongType   => longDecoder
        case StandardType.FloatType  => LowPriorityDecoder(value => Try(value.toFloat).toEither)
        case StandardType.DoubleType => LowPriorityDecoder(value => Try(value.toDouble).toEither)
        case StandardType.BinaryType => binaryDecoder
        case StandardType.CharType   => LowPriorityDecoder(value => Try(value.charAt(0)).toEither)
        case StandardType.UUIDType   => LowPriorityDecoder(value => Try(UUID.fromString(value)).toEither)

        case StandardType.DayOfWeekType                 => intDecoder.map(DayOfWeek.of)
        case StandardType.MonthType                     => intDecoder.map(Month.of)
        case StandardType.YearType                      => intDecoder.map(Year.of)
        case StandardType.ZoneIdType                    => stringDecoder.map(ZoneId.of)
        case StandardType.ZoneOffsetType                => intDecoder.map(ZoneOffset.ofTotalSeconds)
        case StandardType.InstantType(formatter)        => stringDecoder.map(v => Instant.from(formatter.parse(v)))
        case StandardType.LocalDateType(formatter)      => stringDecoder.map(LocalDate.parse(_, formatter))
        case StandardType.LocalTimeType(formatter)      => stringDecoder.map(LocalTime.parse(_, formatter))
        case StandardType.LocalDateTimeType(formatter)  => stringDecoder.map(LocalDateTime.parse(_, formatter))
        case StandardType.OffsetTimeType(formatter)     => stringDecoder.map(OffsetTime.parse(_, formatter))
        case StandardType.OffsetDateTimeType(formatter) => stringDecoder.map(OffsetDateTime.parse(_, formatter))
        case StandardType.ZonedDateTimeType(formatter)  => stringDecoder.map(ZonedDateTime.parse(_, formatter))
        case StandardType.Duration(_)                   => longDecoder.map(t => Duration.ofMillis(t))
        //        case StandardType.MonthDayType                  => ???
        //        case StandardType.PeriodType                    => ???
        //        case StandardType.YearMonthType                 => ???

        case st => fail(s"Unsupported primitive type $st")
      }
  }

  object PrimitiveWrapperDecoder {
    def fail(failure: String): PrimitiveWrapperDecoder[Nothing] = PrimitiveWrapperDecoder((_, _) => Left(failure))

    def decode[A](schema: Schema[A]): PrimitiveWrapperDecoder[A] =
      primitiveWrapperDecoder(schema)

    private def primitiveWrapperDecoder[A](schema: Schema[A]): PrimitiveWrapperDecoder[A] = {
      schema match {
        case s: Schema.Sequence[col, a] =>
          sequenceDecoder[col, a](lowPriorityDecoder[a](s.schemaA), s.fromChunk)

        case Schema.Transform(codec, f, _, _)        => transformDecoder(codec, f)
        case lzy @ Schema.Lazy(_)                    => primitiveWrapperDecoder(lzy.schema)
        case Schema.Primitive(standardType, _)       => primitiveDecoder(standardType)
        case Schema.Optional(codec, _)               => optionalDecoder(codec).asInstanceOf[PrimitiveWrapperDecoder[A]]
        case Schema.Fail(message, _)                 => fail(message)
        case Schema.Enum1(c, _)                      => enumDecoder(c)
        case Schema.Enum2(c1, c2, _)                 => enumDecoder(c1, c2)
        case Schema.Enum3(c1, c2, c3, _)             => enumDecoder(c1, c2, c3)
        case Schema.Enum4(c1, c2, c3, c4, _)         => enumDecoder(c1, c2, c3, c4)
        case Schema.Enum5(c1, c2, c3, c4, c5, _)     => enumDecoder(c1, c2, c3, c4, c5)
        case Schema.Enum6(c1, c2, c3, c4, c5, c6, _) => enumDecoder(c1, c2, c3, c4, c5, c6)
        case Schema.Enum7(c1, c2, c3, c4, c5, c6, c7, _)         => enumDecoder(c1, c2, c3, c4, c5, c6, c7)
        case Schema.Enum8(c1, c2, c3, c4, c5, c6, c7, c8, _)     => enumDecoder(c1, c2, c3, c4, c5, c6, c7, c8)
        case Schema.Enum9(c1, c2, c3, c4, c5, c6, c7, c8, c9, _) => enumDecoder(c1, c2, c3, c4, c5, c6, c7, c8, c9)
        case Schema.Enum10(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, _)                                              =>
          enumDecoder(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10)
        case Schema.Enum11(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, _)                                         =>
          enumDecoder(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11)
        case Schema.Enum12(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, _)                                    =>
          enumDecoder(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12)
        case Schema.Enum13(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, _)                               =>
          enumDecoder(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13)
        case Schema.Enum14(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, _)                          =>
          enumDecoder(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14)
        case Schema.Enum15(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, _)                     =>
          enumDecoder(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15)
        case Schema.Enum16(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, _)                =>
          enumDecoder(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16)
        case Schema.Enum17(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, _)           =>
          enumDecoder(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17)
        case Schema.Enum18(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, _)      =>
          enumDecoder(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18)
        case Schema.Enum19(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, _) =>
          enumDecoder(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19)
        case Schema.Enum20(
              c1,
              c2,
              c3,
              c4,
              c5,
              c6,
              c7,
              c8,
              c9,
              c10,
              c11,
              c12,
              c13,
              c14,
              c15,
              c16,
              c17,
              c18,
              c19,
              c20,
              _,
            ) =>
          enumDecoder(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20)
        case Schema.Enum21(
              c1,
              c2,
              c3,
              c4,
              c5,
              c6,
              c7,
              c8,
              c9,
              c10,
              c11,
              c12,
              c13,
              c14,
              c15,
              c16,
              c17,
              c18,
              c19,
              c20,
              c21,
              _,
            ) =>
          enumDecoder(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11, c12, c13, c14, c15, c16, c17, c18, c19, c20, c21)
        case Schema.Enum22(
              c1,
              c2,
              c3,
              c4,
              c5,
              c6,
              c7,
              c8,
              c9,
              c10,
              c11,
              c12,
              c13,
              c14,
              c15,
              c16,
              c17,
              c18,
              c19,
              c20,
              c21,
              c22,
              _,
            ) =>
          enumDecoder(
            c1,
            c2,
            c3,
            c4,
            c5,
            c6,
            c7,
            c8,
            c9,
            c10,
            c11,
            c12,
            c13,
            c14,
            c15,
            c16,
            c17,
            c18,
            c19,
            c20,
            c21,
            c22,
          )
        case Schema.EnumN(cs, _) => enumDecoder(cs.toSeq: _*)
        case t                   => fail(s"Primitive Wrapper decoder do not support this type: $t")
      }
    }

    private def enumDecoder[Z](cases: Schema.Case[_, Z]*): PrimitiveWrapperDecoder[Z] = {
      PrimitiveWrapperDecoder { (fieldName, currentValue) =>
        cases.find(enumCase => currentValue.contains(enumCase.id)) match {
          case Some(value) =>
            primitiveWrapperDecoder(value.codec).run(fieldName, currentValue).asInstanceOf[Either[String, Z]]
          case None        => Left("failed to identify the case")
        }

      }
    }

    private def lowPriorityDecoder[A](schema: Schema[A]): LowPriorityDecoder[A] =
      schema match {
        case Schema.Primitive(standardType, _) =>
          LowPriorityDecoder.decode(standardType)
        case _                                 => LowPriorityDecoder.fail(s"Fail to decode schema: $schema")
      }

    private def transformDecoder[A, B](schema: Schema[B], f: B => Either[String, A]): PrimitiveWrapperDecoder[A] =
      schema match {
        case Schema.Primitive(typ, _) if typ == StandardType.UnitType =>
          PrimitiveWrapperDecoder { (_, _) =>
            f(().asInstanceOf[B]) match {
              case Left(err) => Left(err)
              case Right(b)  => Right(b)
            }
          }
        case t => fail(s"Primitive Wrapper decoder do not support  product type in sum type: $t")
      }

    private def primitiveDecoder[A](standardType: StandardType[A]): PrimitiveWrapperDecoder[A] =
      PrimitiveWrapperDecoder { (_, value) =>
        value.headOption match {
          case Some(value) =>
            LowPriorityDecoder.decode(standardType).run(value) match {
              case Left(err)    => Left(err.getMessage)
              case Right(value) => Right(value)
            }
          case None        => Left("Query parameter value not provided.")
        }

      }

    private def optionalDecoder[A](schema: Schema[A]): PrimitiveWrapperDecoder[Option[A]] =
      PrimitiveWrapperDecoder { (_, value) =>
        value.headOption match {
          case Some(value) =>
            schema match {
              case Schema.Primitive(standardType, _) =>
                LowPriorityDecoder.decode(standardType).run(value) match {
                  case Left(err)    => Left(err.getMessage)
                  case Right(value) => Right(Some(value))
                }
              case _                                 => Left(s"Fail to decode schema: $schema")
            }

          case None => Right(None)
        }
      }

    private def sequenceDecoder[Col, A](
      decoder: LowPriorityDecoder[A],
      to: Chunk[A] => Col,
    ): PrimitiveWrapperDecoder[Col] = {
      PrimitiveWrapperDecoder { (_, value) =>
        EitherUtil
          .forEach(value) { a =>
            decoder.run(a) match {
              case Left(err)    => Left(err.getMessage)
              case Right(value) => Right(value)
            }
          }
          .map(xs => to(Chunk.fromIterable(xs)))

      }
    }
  }

  object EitherUtil {
    def forEach[A, B](list: Iterable[A])(f: A => Either[String, B]): Either[String, Iterable[B]] = {
      @tailrec
      def loop[A2, B2](xs: Iterable[A2], acc: List[B2])(f: A2 => Either[String, B2]): Either[String, Iterable[B2]] =
        xs match {
          case head :: tail =>
            f(head) match {
              case Left(e)  => Left(e)
              case Right(a) => loop(tail, a :: acc)(f)
            }
          case Nil          => Right(acc.reverse)
          case _            => Right(acc.reverse)
        }

      loop(list.toList, List.empty)(f)
    }
  }

}
