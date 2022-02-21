package zhttp.http.query

import zio.Chunk
import zio.schema.ast.SchemaAst
import zio.schema.{Schema, StandardType}

import java.time.{
  DayOfWeek,
  Duration,
  Instant,
  LocalDate,
  LocalDateTime,
  LocalTime,
  Month,
  OffsetDateTime,
  OffsetTime,
  Year,
  ZoneId,
  ZoneOffset,
  ZonedDateTime,
}
import java.util.UUID
import scala.util.Try

object QueryParams2 {

  final case class Decoder[+A](run: Map[String, List[String]] => Either[String, A]) { self =>

    def map[B](f: A => B): Decoder[B] =
      Decoder { value =>
        self.run(value).map(f)
      }

    def flatMap[B](f: A => Decoder[B]): Decoder[B] =
      Decoder { value =>
        self.run(value).flatMap { a =>
          f(a).run(value)
        }
      }

  }

  object Decoder {
    def fail(failure: String): Decoder[Nothing] = Decoder(_ => Left(failure))

    def decode[A](schema: Schema[A], raw: Map[String, List[String]]): Either[String, A] =
      decoder(schema)
        .run(raw)

    import scala.language.implicitConversions

    implicit def toEither[A0](op: Option[A0]): Either[String, A0] = op match {
      case Some(value) => Right(value)
      case None        => Left("The provided payload do not contains the expected value.")
    }

    private[query] def primitiveDecoder[A](schema: Schema[A], value: String): Decoder[A] =
      schema match {
        case lzy @ Schema.Lazy(_)              => primitiveDecoder(lzy.schema, value)
        case Schema.Optional(codec, _)         =>
          Decoder { _ =>
            codec match {
              case Schema.Primitive(standardType, _) =>
                LowPriorityDecoder.decode(standardType, value) match {
                  case Left(value)  => Left(value.getMessage)
                  case Right(value) => Right(Option(value))
                }
              case _                                 => Left("Too many levels.")
            }

          }
        case Schema.Primitive(standardType, _) =>
          Decoder { _ =>
            LowPriorityDecoder.decode(standardType, value) match {
              case Left(value)  => Left(value.getMessage)
              case Right(value) => Right(value)
            }
          }

        case Schema.Sequence(elementSchema, _, _, _) =>
          primitiveDecoder(elementSchema, value).map(_.asInstanceOf[A])

        case _ => fail("Failed to decode")
      }

    private[query] def decoder[A](schema: Schema[A]): Decoder[A] = {
      schema match {
        // case Schema.GenericRecord(structure, _) => recordDecoder(structure.toChunk)
        // case Schema.Sequence(elementSchema, fromChunk, _, _)                          => ???
        case Schema.Transform(codec, f, _, _)                                         => transformDecoder(codec, f)
        case Schema.Primitive(standardType, _)                                        => primitiveDecoder2(standardType)
        // case Schema.Tuple(left, right, _)                                             => tupleDecoder(left, right)
        case Schema.Optional(codec, _)                                                => optionalDecoder(codec)
        case Schema.Fail(message, _)                                                  => fail(message)
        case Schema.EitherSchema(left, right, _)                                      => eitherDecoder(left, right)
        case lzy @ Schema.Lazy(_)                                                     => decoder(lzy.schema)
        case Schema.Meta(_, _)                                                        => astDecoder
        case s: Schema.CaseClass1[_, A]                                               => caseClass1Decoder(s)
        case s: Schema.CaseClass2[_, _, A]                                            => caseClass2Decoder(s)
        case s: Schema.CaseClass3[_, _, _, A]                                         => caseClass3Decoder(s)
        case s: Schema.CaseClass4[_, _, _, _, A]                                      => caseClass4Decoder(s)
        case s: Schema.CaseClass5[_, _, _, _, _, A]                                   => caseClass5Decoder(s)
        case s: Schema.CaseClass6[_, _, _, _, _, _, A]                                => caseClass6Decoder(s)
        case s: Schema.CaseClass7[_, _, _, _, _, _, _, A]                             => caseClass7Decoder(s)
        case s: Schema.CaseClass8[_, _, _, _, _, _, _, _, A]                          => caseClass8Decoder(s)
        case s: Schema.CaseClass9[_, _, _, _, _, _, _, _, _, A]                       => caseClass9Decoder(s)
        case s: Schema.CaseClass10[_, _, _, _, _, _, _, _, _, _, A]                   => caseClass10Decoder(s)
        case s: Schema.CaseClass11[_, _, _, _, _, _, _, _, _, _, _, A]                => caseClass11Decoder(s)
        case s: Schema.CaseClass12[_, _, _, _, _, _, _, _, _, _, _, _, A]             => caseClass12Decoder(s)
        case s: Schema.CaseClass13[_, _, _, _, _, _, _, _, _, _, _, _, _, A]          => caseClass13Decoder(s)
        case s: Schema.CaseClass14[_, _, _, _, _, _, _, _, _, _, _, _, _, _, A]       => caseClass14Decoder(s)
        case s: Schema.CaseClass15[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A]    => caseClass15Decoder(s)
        case s: Schema.CaseClass16[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A] => caseClass16Decoder(s)
        case s: Schema.CaseClass17[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A]       => caseClass17Decoder(s)
        case s: Schema.CaseClass18[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A]    => caseClass18Decoder(s)
        case s: Schema.CaseClass19[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A] => caseClass19Decoder(s)
        case s: Schema.CaseClass20[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A]       =>
          caseClass20Decoder(s)
        case s: Schema.CaseClass21[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A]    =>
          caseClass21Decoder(s)
        case s: Schema.CaseClass22[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, A] =>
          caseClass22Decoder(s)

        case Schema.Enum1(c, _)                                  => enumDecoder(c)
        case Schema.Enum2(c1, c2, _)                             => enumDecoder(c1, c2)
        case Schema.Enum3(c1, c2, c3, _)                         => enumDecoder(c1, c2, c3)
        case Schema.Enum4(c1, c2, c3, c4, _)                     => enumDecoder(c1, c2, c3, c4)
        case Schema.Enum5(c1, c2, c3, c4, c5, _)                 => enumDecoder(c1, c2, c3, c4, c5)
        case Schema.Enum6(c1, c2, c3, c4, c5, c6, _)             => enumDecoder(c1, c2, c3, c4, c5, c6)
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
        case err                 => fail(s"$err type is not supported.")
      }

    }

    private def extractField(
      label: String,
      map: Map[String, List[String]],
    ): Option[List[String]] =
      map.get(label)

    private val astDecoder: Decoder[Schema[_]] =
      decoder(Schema[SchemaAst]).map(_.toSchema)

//    private def optionalDecoder[A](decoder: Decoder[A]): Decoder[Option[A]] = { (el: List[String]) =>
//      if (el.isEmpty) Right(None)
//      else
//        decoder(el.headOption)
//    }

    private def transformDecoder[A, B](schema: Schema[B], f: B => Either[String, A]): Decoder[A] =
      schema match {
        case Schema.Primitive(typ, _) if typ == StandardType.UnitType =>
          Decoder { _ =>
            f(().asInstanceOf[B]) match {
              case Left(err) => Left(err)
              case Right(b)  => Right(b)
            }
          }
        case _ => decoder(schema).flatMap(a => Decoder(_ => f(a)))
      }

    private def primitiveDecoder2[A](standardType: StandardType[A]): Decoder[A] = ???

    private def optionalDecoder[A](schema: Schema[A]): Decoder[Option[A]] = decoder(schema).map(Some(_))

    private def eitherDecoder[A, B](left: Schema[A], right: Schema[B]): Decoder[Either[A, B]] = ???

    private def enumDecoder[Z](cases: Schema.Case[_, Z]*): Decoder[Z] = fail(
      s"Not implemented $cases",
    )

    private[query] def caseClass1Decoder[A, Z](schema: Schema.CaseClass1[A, Z]): Decoder[Z] = {
      val field = schema.field
      if (isPrimitiveSchema(field.schema)) {
        if (field.schema.ast.dimensions == 1) {
          // decoder(field.schema).asInstanceOf[Decoder[Z]]
          Decoder { value =>
            val data = toEither(extractField(field.label, value))
            data.flatMap { d =>
              val result: List[Either[String, A]] = d.map { item =>
                primitiveDecoder(field.schema, item).run(Map.empty)
              }
              val acc = result.foldLeft(List.empty[A])((acc, e) => if (e.isLeft) acc else acc :+ e.toOption.get)
              Right(acc)
            }.flatMap { e =>
              Right(schema.construct(e.asInstanceOf[A]))
            }
          }
        } else {
          Decoder { value =>
            val data = toEither(extractField(field.label, value))
            data.flatMap { d =>
              val ex = primitiveDecoder(field.schema, d.head).run(Map.empty)
              ex.flatMap { e =>
                Right(schema.construct(e))
              }
            }
          }

        }
      } else {
        decoder(field.schema).asInstanceOf[Decoder[Z]]
      }
    }

    private def isPrimitiveSchema[A0](schema: Schema[A0]): Boolean = schema match {
      case Schema.Primitive(_, _) => true
      case Schema.Lazy(_)         => true
      case Schema.Optional(_, _)  => true
      case _                      => false
    }

    private[query] def caseClass2Decoder[A1, A2, Z](schema: Schema.CaseClass2[A1, A2, Z]): Decoder[Z] = ???

    private[query] def caseClass3Decoder[A1, A2, A3, Z](schema: Schema.CaseClass3[A1, A2, A3, Z]): Decoder[Z] = ???

    private[query] def caseClass4Decoder[A1, A2, A3, A4, Z](schema: Schema.CaseClass4[A1, A2, A3, A4, Z]): Decoder[Z] =
      ???

    private[query] def caseClass5Decoder[A1, A2, A3, A4, A5, Z](
      schema: Schema.CaseClass5[A1, A2, A3, A4, A5, Z],
    ): Decoder[Z] = ???

    private[query] def caseClass6Decoder[A1, A2, A3, A4, A5, A6, Z](
      schema: Schema.CaseClass6[A1, A2, A3, A4, A5, A6, Z],
    ): Decoder[Z] = ???

    private[query] def caseClass7Decoder[A1, A2, A3, A4, A5, A6, A7, Z](
      schema: Schema.CaseClass7[A1, A2, A3, A4, A5, A6, A7, Z],
    ): Decoder[Z] = ???

    private[query] def caseClass8Decoder[A1, A2, A3, A4, A5, A6, A7, A8, Z](
      schema: Schema.CaseClass8[A1, A2, A3, A4, A5, A6, A7, A8, Z],
    ): Decoder[Z] = ???

    private[query] def caseClass9Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, Z](
      schema: Schema.CaseClass9[A1, A2, A3, A4, A5, A6, A7, A8, A9, Z],
    ): Decoder[Z] = ???

    private[query] def caseClass10Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, Z](
      schema: Schema.CaseClass10[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, Z],
    ): Decoder[Z] = ???

    private[query] def caseClass11Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, Z](
      schema: Schema.CaseClass11[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, Z],
    ): Decoder[Z] = ???

    private[query] def caseClass12Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, Z](
      schema: Schema.CaseClass12[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, Z],
    ): Decoder[Z] = ???

    private[query] def caseClass13Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, Z](
      schema: Schema.CaseClass13[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, Z],
    ): Decoder[Z] = ???

    private[query] def caseClass14Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, Z](
      schema: Schema.CaseClass14[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, Z],
    ): Decoder[Z] = ???

    private[query] def caseClass15Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, Z](
      schema: Schema.CaseClass15[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, Z],
    ): Decoder[Z] = ???

    private[query] def caseClass16Decoder[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, Z](
      schema: Schema.CaseClass16[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, Z],
    ): Decoder[Z] = ???

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
    ): Decoder[Z] = ???

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
    ): Decoder[Z] = ???

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
    ): Decoder[Z] = ???

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
    ): Decoder[Z] = ???

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
    ): Decoder[Z] = ???

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
    ): Decoder[Z] = ???

  }

  final case class LowPriorityDecoder[+A](run: String => Either[Throwable, A]) {
    self =>

    def map[B](f: A => B): LowPriorityDecoder[B] =
      LowPriorityDecoder { value =>
        self.run(value).map(f)
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

    def decode[A](standardType: StandardType[A], value: String): Either[Throwable, A] =
      primitiveDecoder(standardType).run(value)

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

}
