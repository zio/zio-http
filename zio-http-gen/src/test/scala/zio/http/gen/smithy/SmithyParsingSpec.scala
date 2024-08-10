package zio.http.gen.smithy

import zio.Scope
import zio.test._

object SmithyParsingSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("SmithyParsingSpec")(
      test("parse service") {
        val smithyString = """$version: "2"
                             |namespace example.weather
                             |
                             |service Weather {
                             |    version: "2006-03-01"
                             |}""".stripMargin
        val parsed = Smithy.parse(smithyString)
        val expected = Smithy(Map("Weather" -> Smithy.Service("2006-03-01", Nil, Nil, Nil, Map.empty)))
        assertCompletes
      },
      test("parse service and resource"){
        val smithyString =  """$version: "2"
                              |namespace example.weather
                              |
                              |/// Provides weather forecasts.
                              |service Weather {
                              |    version: "2006-03-01"
                              |    resources: [
                              |        City
                              |    ]
                              |}
                              |
                              |resource City {
                              |    identifiers: { cityId: CityId }
                              |    read: GetCity
                              |    list: ListCities
                              |}
                              |
                              |// "pattern" is a trait.
                              |@pattern("^[A-Za-z0-9 ]+$")
                              |string CityId""".stripMargin
        val parsed = Smithy.parse(smithyString)
        //val expected = Smithy(Map("Weather" -> Smithy.Service("2006-03-01", Nil, Nil, Nil, Map("City" -> Smithy.Resource(Map("cityId" -> "CityId"), "GetCity", "ListCities")))))
        assertCompletes
      }
    )
}
