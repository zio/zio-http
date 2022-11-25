package zio.http.model.headers.values

import zio.http.model.MimeDB
import zio.http.model.headers.values.Authorization.AuthScheme.Digest
import zio.http.model.headers.values.Authorization.{AuthorizationValue, InvalidAuthorizationValue}
import zio.test._

import java.net.URI

object AuthorizationSpec extends ZIOSpecDefault with MimeDB {
  override def spec = suite("Authorization header suite")(
    test("parsing of invalid Authorization values") {
      assertTrue(Authorization.toAuthorization("") == InvalidAuthorizationValue) &&
      assertTrue(Authorization.toAuthorization("something") == InvalidAuthorizationValue)
    },
    test("parsing and encoding is symmetrical") {
      val value = AuthorizationValue(
        Digest(
          "ae66e67d6b427bd3f120414a82e4acff38e8ecd9101d6c861229025f607a79dd",
          "488869477bf257147b804c45308cd62ac4e25eb717b12b298c79e62dcea254ec",
          "api@example.org",
          URI.create("/doe.json"),
          "HRPCssKJSGjCrkzDg8OhwpzCiGPChXYjwrI2QmXDnsOS",
          "SHA-512-256",
          "auth",
          "NTg6RKcb9boFIAS3KrFK9BGeh+iDa/sm6jUMp2wds69v",
          "7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v",
          1,
          true,
        ),
      )
      assertTrue(Authorization.toAuthorization(Authorization.fromAuthorization(value)) == value)
    },
    test("parsing of Authorization header values") {
      val values = Map(
        """Digest username="Mufasa", realm="http-auth@example.org", uri="/dir/index.html", algorithm=SHA-256, """ +
          """nonce="7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v", nc=00000001, cnonce="f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ", """ +
          """qop=auth, response="753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1", """ +
          """opaque="FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS""""                -> AuthorizationValue(
            Digest(
              "753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1",
              "Mufasa",
              "http-auth@example.org",
              URI.create("/dir/index.html"),
              "FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS",
              "SHA-256",
              "auth",
              "f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ",
              "7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v",
              1,
              false,
            ),
          ),
        """Digest username*="Mufasa", realm="http-auth@example.org", uri="/dir/index.html", algorithm=SHA-256, """ +
          """nonce="7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v", nc=00000001, cnonce="f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ", """ +
          """qop=auth, response="753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1", """ +
          """opaque="FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS",userhash=false""" -> AuthorizationValue(
            Digest(
              "753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1",
              "Mufasa",
              "http-auth@example.org",
              URI.create("/dir/index.html"),
              "FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS",
              "SHA-256",
              "auth",
              "f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ",
              "7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v",
              1,
              false,
            ),
          ),
        """Digest username="test",username*="test2", realm="http-auth@example.org", uri="/dir/index.html", algorithm=SHA-256, """ +
          """nonce="7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v", nc=00000001, cnonce="f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ", """ +
          """qop=auth, response="753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1", """ +
          """opaque="FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS",userhash=false""" -> InvalidAuthorizationValue,
      )
      values.foldLeft(assertTrue(true)) { case (acc, (header, expected)) =>
        acc && assertTrue(Authorization.toAuthorization(header) == expected)
      }
    },
  )
}
