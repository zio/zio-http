package zio.http.model.headers.values

import zio.Scope
import zio.test.{ZIOSpecDefault, _}

import zio.http.internal.HttpGen
import zio.http.{URL => Zurl}

object LocationSpec extends ZIOSpecDefault {

  override def spec = suite("Location header suite")(
    test("Location with Empty Value") {
      assertTrue(Location.toLocation("") == Location.EmptyLocationValue) &&
      assertTrue(Location.fromLocation(Location.EmptyLocationValue) == "")
    },
    test("parsing of valid Location values") {
      check(HttpGen.request) { genRequest =>
        val toLocation    = Location.toLocation(genRequest.location.fold("")(_.toString))
        val locationValue =
          genRequest.location.fold[Location](Location.EmptyLocationValue)(x => Location.toLocation(x.toString))

        assertTrue(toLocation == locationValue)
      }

    },
  )

}
