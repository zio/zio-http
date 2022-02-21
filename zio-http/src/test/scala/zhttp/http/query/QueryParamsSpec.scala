package zhttp.http.query

import zhttp.http.QueryParameters
import zhttp.http.query.QueryParams.QueryParametersWrapper
import zio.schema.{DeriveSchema, Schema}
import zio.test.Assertion._
import zio.test._

object QueryParamsSpec extends DefaultRunnableSpec {

  sealed trait Sorting
  case object Ascending  extends Sorting
  case object Descending extends Sorting

  case class UserQuery(fields: List[String], perPage: Option[Int], page: Option[Int], sort: Sorting)
  object UserQuery {
    implicit val schema: Schema[UserQuery] = DeriveSchema.gen[UserQuery]
  }

  case class CaseClass1Query(username: String)
  object CaseClass1Query {
    implicit val schema = DeriveSchema.gen[CaseClass1Query]
  }

  case class CaseClass1IntQuery(counter: Int)
  object CaseClass1IntQuery {
    implicit val schema = DeriveSchema.gen[CaseClass1IntQuery]
  }

  case class CaseClass1OptionQuery(username: Option[String])
  object CaseClass1OptionQuery {
    implicit val schema = DeriveSchema.gen[CaseClass1OptionQuery]
  }

  case class CaseClass1SeqQuery(username: List[String])
  object CaseClass1SeqQuery {
    implicit val schema = DeriveSchema.gen[CaseClass1SeqQuery]
  }

  case class CaseClass1SumQuery(sorting: Sorting)
  object CaseClass1SumQuery {
    implicit val schema = DeriveSchema.gen[CaseClass1SumQuery]
  }

  case class CaseClass1VectorQuery(username: List[Int])
  object CaseClass1VectorQuery {
    implicit val schema = DeriveSchema.gen[CaseClass1VectorQuery]
  }

  val decoder = suite("Decoder spec") {
    test("should be able to decode simple 1 param case class") {
      import CaseClass1Query._
      val probe = QueryParameters(
        Map(
          "username" -> List("Test"),
        ),
      )

      val result = probe.decode[CaseClass1Query]
      assert(result)(isRight) && assertTrue(result.toOption.get == CaseClass1Query(username = "Test"))

    } +
      test("should be able to decode simple 1 option param case class") {
        import CaseClass1OptionQuery._
        val probe = QueryParameters(
          Map(
            "username" -> List("Test"),
          ),
        )

        val result = probe.decode[CaseClass1OptionQuery]
        assert(result)(isRight) && assertTrue(result.toOption.get == CaseClass1OptionQuery(username = Some("Test")))

      } +
      test("should be able to decode simple 1 list param case class") {
        import CaseClass1SeqQuery._
        val probe = QueryParameters(
          Map(
            "username" -> List("Test"),
          ),
        )

        val result = probe.decode[CaseClass1SeqQuery]
        assert(result)(isRight) && assertTrue(result.toOption.get == CaseClass1SeqQuery(username = List("Test")))

      } +
      test("should be able to decode simple 1 sum  param case class") {
        import CaseClass1SumQuery._
        val probe = QueryParameters(
          Map(
            "sorting" -> List("Ascending"),
          ),
        )

        val result = probe.decode[CaseClass1SumQuery]
        assert(result)(isRight) && assertTrue(result.toOption.get == CaseClass1SumQuery(sorting = Ascending))

      } +
      test("should be able to decode simple 1 int  param case class") {
        import CaseClass1IntQuery._
        val probe = QueryParameters(
          Map(
            "counter" -> List("1"),
          ),
        )

        val result = probe.decode[CaseClass1IntQuery]
        assert(result)(isRight) && assertTrue(result.toOption.get == CaseClass1IntQuery(counter = 1))

      } +
      test("should be able to decode simple 1 list of int param case class") {
        import CaseClass1VectorQuery._
        val probe = QueryParameters(
          Map(
            "username" -> List("1"),
          ),
        )

        val result = probe.decode[CaseClass1VectorQuery]
        assert(result)(isRight) && assertTrue(result.toOption.get == CaseClass1VectorQuery(username = List(1)))

      } +
      test("should be able to decode case class with 4 params") {
        import UserQuery._
        val probe = QueryParameters(
          Map(
            "fields"  -> List("a", "b", "c"),
            "perPage" -> List("1"),
            "page"    -> List("1"),
            "sort"    -> List("Ascending"),
          ),
        )

        val result = probe.decode[UserQuery]
        assert(result)(isRight) && assertTrue(
          result.toOption.get == UserQuery(
            fields = List("a", "b", "c"),
            perPage = Some(1),
            page = Some(1),
            sort = Ascending,
          ),
        )

      }

  }

  override def spec = suite("Query Params Decoder spec") {
    decoder
  }
}
