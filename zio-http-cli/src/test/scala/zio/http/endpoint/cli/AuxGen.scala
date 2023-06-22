package zio.http.endpoint.cli

import zio.test.Gen
import zio.http.codec._
import zio.http.MediaType
import zio.schema._
import zio._
import zio.schema.StandardType._
import scala.collection.immutable.ListMap

/**
 * Auxiliary generators
 */

object AuxGen {
    lazy val anyTextCodec: Gen[Any, TextCodec[_]] =
        Gen.oneOf(
            Gen.fromIterable(List(TextCodec.boolean, TextCodec.int, TextCodec.string, TextCodec.uuid)),
            Gen.alphaNumericStringBounded(1, 30).map(TextCodec.constant(_)),
        )

    lazy val anyMediaType: Gen[Any, MediaType] = Gen.fromIterable(MediaType.allMediaTypes)

    lazy val anyDoc: Gen[Any, Doc] = Gen.alphaNumericStringBounded(1, 30).map(Doc.p(_))

    case class Pair[A, B](a: A, b: B) {
        def getA = a
        def getB = b

        def setA(aNew: A) = Pair(aNew, b)
        def setB(bNew: B) = Pair(a, bNew)
    }

    def constructRecord[A, B](schemaA: Schema[A], schemaB: Schema[B]) =
        Schema.CaseClass2[A, B, Pair[A, B]](
            TypeId.fromTypeName("record"),
            Schema.Field("a", schemaA,
                get0 = _.getA,
                set0 = ( (pair, a) => pair.setA(a)  )),
            Schema.Field("b", schemaB,
            get0 = _.getB,
            set0 = ( (pair, b) => pair.setB(b)  )),
            (a, b) => Pair(a, b)
        )

    def constructTransform[A](schema: Schema[A]) =
        Schema.Transform[A, A, Unit](schema, x => Right(x), (x: A) => Right(x), Chunk(), (): Unit)

    def constructLazy[A](schema: Schema[A]) =
        Schema.Lazy[A](() => schema)

    lazy val anySchema: Gen[Any, Schema[_]] =
        Gen.oneOf(
            anyStandardType,
            anyStandardType.map(Schema.list(_)),
            anyStandardType.map(_.optional),
            anyStandardType.map(Schema.list(_)),
            anyStandardType.zip(anyStandardType).map { case (l, r) => Schema.either(l, r) },
            anyStandardType.zip(anyStandardType).map { case (l, r) => Schema.tuple2(l, r) },
            Gen.const(Schema.Dynamic()),
            anyStandardType.zip(anyStandardType).map {
                case (st1, st2) => constructRecord(st1, st2)
            },
            anyEnumeration(anyStandardType),
            Gen.const(Schema.Fail("")),
            anyStandardType.map(constructTransform(_)),
            Gen.const(Schema.Dynamic(Chunk())),
            anyStandardType.map(constructLazy(_)),
        )

    val anyLabel: Gen[Any, String] = Gen.alphaNumericStringBounded(1, 15)
    
    def anyEnumeration(schemaGen: Gen[Any, Schema[_]]): Gen[Any, Schema[_]] =
        Gen
        .setOfBounded(1, 3)(
            anyLabel.zip(schemaGen)
        )
        .map(ListMap.empty ++ _)
        .map(toCaseSet)
        .zip(Gen.string(Gen.alphaChar).map(TypeId.parse))
        .map {
            case (caseSet, id) => Schema.enumeration[Any, CaseSet.Aux[Any]](id, caseSet)
        }

  def toCaseSet(cases: ListMap[String, Schema[_]]): CaseSet.Aux[Any] =
    cases.foldRight[CaseSet.Aux[Any]](CaseSet.Empty[Any]()) {
      case ((id, codec), acc) =>
        val _case = Schema.Case[Any, Any](
          id,
          codec.asInstanceOf[Schema[Any]],
          _.asInstanceOf[Any],
          _.asInstanceOf[Any],
          _.isInstanceOf[Any],
          Chunk.empty
        )
        CaseSet.Cons(_case, acc)
    }

    val anyStandardType: Gen[Any, Schema[_]] = Gen.fromIterable(
        List(
            (StandardType.StringType),
            (StandardType.BoolType),
            (StandardType.ShortType),
            (StandardType.IntType),
            (StandardType.LongType),
            (StandardType.FloatType),
            (StandardType.DoubleType),
            (StandardType.BinaryType),
            (StandardType.BigDecimalType),
            (StandardType.BigIntegerType),
            (StandardType.CharType),
            (StandardType.UUIDType),
            (StandardType.DayOfWeekType),
            (StandardType.DurationType),
            (StandardType.InstantType),
            (StandardType.LocalDateType),
            (StandardType.LocalDateTimeType),
            (StandardType.LocalTimeType),
            (StandardType.MonthType),
            (StandardType.MonthDayType),
            (StandardType.OffsetDateTimeType),
            (StandardType.OffsetTimeType),
            (StandardType.PeriodType),
            (StandardType.YearType),
            (StandardType.YearMonthType),
            (StandardType.ZonedDateTimeType),
            (StandardType.ZoneIdType)
        )
    ).map(Schema.Primitive(_))

}
