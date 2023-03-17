/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.model.headers.values

import java.net.URI

import zio.Scope
import zio.test._

import zio.http.model.MimeDB
import zio.http.model.headers.values.Authorization.AuthScheme.Digest

object AuthorizationSpec extends ZIOSpecDefault with MimeDB {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Authorization header suite")(
      test("parsing of invalid Authorization values") {
        assertTrue(Authorization.toAuthorization("").isLeft) &&
        assertTrue(Authorization.toAuthorization("something").isLeft)
      },
      test("parsing and encoding is symmetrical") {
        val value = Authorization(
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
            userhash = true,
          ),
        )
        assertTrue(Authorization.toAuthorization(Authorization.fromAuthorization(value)) == Right(value))
      },
      test("parsing of Authorization header values") {
        val values = Map(
          """Digest username="Mufasa", realm="http-auth@example.org", uri="/dir/index.html", algorithm=SHA-256, """ +
            """nonce="7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v", nc=00000001, cnonce="f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ", """ +
            """qop=auth, response="753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1", """ +
            """opaque="FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS""""                -> Some(
              Authorization(
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
                  userhash = false,
                ),
              ),
            ),
          """Digest username*="Mufasa", realm="http-auth@example.org", uri="/dir/index.html", algorithm=SHA-256, """ +
            """nonce="7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v", nc=00000001, cnonce="f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ", """ +
            """qop=auth, response="753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1", """ +
            """opaque="FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS",userhash=false""" -> Some(
              Authorization(
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
                  userhash = false,
                ),
              ),
            ),
          """Digest username="test",username*="test2", realm="http-auth@example.org", uri="/dir/index.html", algorithm=SHA-256, """ +
            """nonce="7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v", nc=00000001, cnonce="f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ", """ +
            """qop=auth, response="753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1", """ +
            """opaque="FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS",userhash=false""" -> None,
        )
        values.foldLeft(assertTrue(true)) { case (acc, (header, expected)) =>
          acc && assertTrue(Authorization.toAuthorization(header).toOption == expected)
        }
      },
      test("Parsing an invalid basic auth header") {
        val auth = Authorization.toAuthorization("Basic not-base64")
        assertTrue(auth.isLeft)
      },
    )
}
