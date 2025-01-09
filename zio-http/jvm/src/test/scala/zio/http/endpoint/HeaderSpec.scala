package zio.http.endpoint

import zio.test._
import zio.{NonEmptyChunk, Scope}

import zio.schema.Schema
import zio.schema.annotation.fieldName

import zio.http._
import zio.http.codec.HttpCodec
import zio.http.endpoint.EndpointSpec.testEndpointWithHeaders

object HeaderSpec extends ZIOHttpSpec {
  case class MyHeaders(age: String, @fieldName("content-type") cType: String = "application", xApiKey: Option[String])

  object MyHeaders {
    implicit val schema: Schema[MyHeaders] = zio.schema.DeriveSchema.gen[MyHeaders]
  }

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("HeaderCodec")(
      test("Headers from case class") {
        check(
          Gen.alphaNumericStringBounded(1, 10),
          Gen.alphaNumericStringBounded(1, 10),
          Gen.alphaNumericStringBounded(1, 10),
        ) { (age, cType, apiKey) =>
          val testRoutes = testEndpointWithHeaders(
            Routes(
              Endpoint(Method.GET / "users")
                .header(HttpCodec.headers[MyHeaders])
                .out[String]
                .implementPurely(_.toString),
            ),
          ) _

          testRoutes(
            s"/users",
            List(
              "age"          -> age,
              "content-type" -> cType,
              "x-api-key"    -> apiKey,
            ),
            MyHeaders(age, cType, Some(apiKey)).toString,
          ) &&
          testRoutes(
            s"/users",
            List(
              "age"          -> age,
              "content-type" -> cType,
              "x-api-key"    -> "",
            ),
            MyHeaders(age, cType, Some("")).toString,
          ) &&
          testRoutes(
            s"/users",
            List(
              "age" -> age,
            ),
            MyHeaders(age, "application", None).toString,
          )
        }
      },
      test("Optional Headers from case class") {
        check(
          Gen.alphaNumericStringBounded(1, 10),
          Gen.alphaNumericStringBounded(1, 10),
          Gen.alphaNumericStringBounded(1, 10),
        ) { (age, cType, apiKey) =>
          val testRoutes = testEndpointWithHeaders(
            Routes(
              Endpoint(Method.GET / "users")
                .header(HttpCodec.headers[MyHeaders].optional)
                .out[String]
                .implementPurely(_.toString),
            ),
          ) _

          testRoutes(
            s"/users",
            List(
              "content-type" -> cType,
            ),
            None.toString,
          ) &&
          testRoutes(
            s"/users",
            List(
              "age"          -> age,
              "content-type" -> cType,
              "x-api-key"    -> apiKey,
            ),
            Some(MyHeaders(age, cType, Some(apiKey))).toString,
          ) && testRoutes(
            s"/users",
            List(
              "age" -> age,
            ),
            Some(MyHeaders(age, "application", None)).toString,
          )
        }
      },
      test("Multiple Header values") {
        check(
          Gen.alphaNumericStringBounded(1, 10),
          Gen.alphaNumericStringBounded(1, 10),
          Gen.alphaNumericStringBounded(1, 10),
        ) { (age, age2, age3) =>
          val testRoutes = testEndpointWithHeaders(
            Routes(
              Endpoint(Method.GET / "users")
                .header(HttpCodec.headerAs[List[String]]("age"))
                .out[String]
                .implementPurely(_.toString),
            ),
          ) _

          testRoutes(
            s"/users",
            List(
              "age" -> age,
            ),
            List(age).toString,
          ) && testRoutes(
            s"/users",
            List(
              "age" -> age,
              "age" -> age2,
            ),
            List(age, age2).toString,
          ) && testRoutes(
            s"/users",
            List(
              "age" -> age,
              "age" -> age2,
              "age" -> age3,
            ),
            List(age, age2, age3).toString,
          )
        }
      },
      test("Multiple Header values non empty") {
        check(
          Gen.alphaNumericStringBounded(1, 10),
          Gen.alphaNumericStringBounded(1, 10),
          Gen.alphaNumericStringBounded(1, 10),
        ) { (age, age2, age3) =>
          val testRoutes = testEndpointWithHeaders(
            Routes(
              Endpoint(Method.GET / "users")
                .header(HttpCodec.headerAs[NonEmptyChunk[String]]("age"))
                .out[String]
                .implementPurely(_.toString),
            ),
          ) _

          testRoutes(
            s"/users",
            List(
              "age" -> age,
            ),
            NonEmptyChunk(age).toString,
          ) && testRoutes(
            s"/users",
            List(
              "age" -> age,
              "age" -> age2,
            ),
            NonEmptyChunk(age, age2).toString,
          ) && testRoutes(
            s"/users",
            List(
              "age" -> age,
              "age" -> age2,
              "age" -> age3,
            ),
            NonEmptyChunk(age, age2, age3).toString,
          )
        }
      },
      test("Header from transformed schema") {
        case class Wrapper(age: Int)
        implicit val schema: Schema[Wrapper] = zio.schema.Schema[Int].transform[Wrapper](Wrapper(_), _.age)
        check(Gen.int) { age =>
          val testRoutes = testEndpointWithHeaders(
            Routes(
              Endpoint(Method.GET / "users")
                .header(HttpCodec.headerAs[Wrapper]("age"))
                .out[String]
                .implementPurely(_.toString),
            ),
          ) _

          testRoutes(
            s"/users",
            List(
              "age" -> age.toString,
            ),
            Wrapper(age).toString,
          )
        }
      },
    )
}
