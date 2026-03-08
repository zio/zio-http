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

package zio.http.headers

import zio.Scope
import zio.test._

import zio.http.Header.ContentSecurityPolicy._
import zio.http.Header.{ContentSecurityPolicy, ContentSecurityPolicyReportOnly}
import zio.http.ZIOHttpSpec

object ContentSecurityPolicySpec extends ZIOHttpSpec {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ContentSecurityPolicy suite")(
    suite("single directive parse/render symmetry")(
      test("default-src 'none'") {
        val csp = SourcePolicy(SourcePolicyType.`default-src`, Source.none)
        assertTrue(
          ContentSecurityPolicy.render(csp) == "default-src 'none'",
          ContentSecurityPolicy.parse("default-src 'none'") == Right(csp),
        )
      },
      test("script-src 'self'") {
        val csp = SourcePolicy(SourcePolicyType.`script-src`, Source.Self)
        assertTrue(
          ContentSecurityPolicy.render(csp) == "script-src 'self'",
          ContentSecurityPolicy.parse("script-src 'self'") == Right(csp),
        )
      },
      test("upgrade-insecure-requests") {
        assertTrue(
          ContentSecurityPolicy.parse("upgrade-insecure-requests") == Right(UpgradeInsecureRequests),
          ContentSecurityPolicy.render(UpgradeInsecureRequests) == "upgrade-insecure-requests",
        )
      },
      test("block-all-mixed-content") {
        assertTrue(
          ContentSecurityPolicy.parse("block-all-mixed-content") == Right(BlockAllMixedContent),
          ContentSecurityPolicy.render(BlockAllMixedContent) == "block-all-mixed-content",
        )
      },
      test("round-trip for single directive") {
        val csp = SourcePolicy(SourcePolicyType.`img-src`, Source.Self)
        assertTrue(ContentSecurityPolicy.parse(ContentSecurityPolicy.render(csp)) == Right(csp))
      },
    ),
    suite("multi-directive parse/render symmetry")(
      test("two directives") {
        val rendered = "default-src 'none'; script-src 'self'"
        val expected = Combined(
          zio.Chunk(
            SourcePolicy(SourcePolicyType.`default-src`, Source.none),
            SourcePolicy(SourcePolicyType.`script-src`, Source.Self),
          ),
        )
        assertTrue(
          ContentSecurityPolicy.parse(rendered) == Right(expected),
          ContentSecurityPolicy.render(expected) == rendered,
        )
      },
      test("three directives") {
        val rendered = "default-src 'none'; script-src 'self'; style-src 'unsafe-inline'"
        val expected = Combined(
          zio.Chunk(
            SourcePolicy(SourcePolicyType.`default-src`, Source.none),
            SourcePolicy(SourcePolicyType.`script-src`, Source.Self),
            SourcePolicy(SourcePolicyType.`style-src`, Source.UnsafeInline),
          ),
        )
        assertTrue(
          ContentSecurityPolicy.parse(rendered) == Right(expected),
          ContentSecurityPolicy.render(expected) == rendered,
        )
      },
      test("round-trip for multi-directive") {
        val csp = Combined(
          zio.Chunk(
            SourcePolicy(SourcePolicyType.`default-src`, Source.Self),
            SourcePolicy(SourcePolicyType.`img-src`, Source.none),
            UpgradeInsecureRequests,
          ),
        )
        assertTrue(ContentSecurityPolicy.parse(ContentSecurityPolicy.render(csp)) == Right(csp))
      },
    ),
    suite("factory method")(
      test("single directive returns directive directly") {
        val single = SourcePolicy(SourcePolicyType.`default-src`, Source.none)
        assertTrue(ContentSecurityPolicy(single) == single)
      },
      test("multiple directives returns Combined") {
        val d1     = SourcePolicy(SourcePolicyType.`default-src`, Source.none)
        val d2     = SourcePolicy(SourcePolicyType.`script-src`, Source.Self)
        val result = ContentSecurityPolicy(d1, d2)
        assertTrue(result == Combined(zio.Chunk(d1, d2)))
      },
    ),
    suite("backward compatibility")(
      test("existing SourcePolicy creation still works") {
        val csp: ContentSecurityPolicy = SourcePolicy(SourcePolicyType.`default-src`, Source.Self)
        assertTrue(ContentSecurityPolicy.render(csp) == "default-src 'self'")
      },
      test("convenience methods still work") {
        val csp = ContentSecurityPolicy.defaultSrc(Source.Self)
        assertTrue(ContentSecurityPolicy.render(csp) == "default-src 'self'")
      },
    ),
    suite("ContentSecurityPolicyReportOnly")(
      test("parse single directive") {
        val result   = ContentSecurityPolicyReportOnly.parse("default-src 'self'")
        val expected = ContentSecurityPolicyReportOnly(SourcePolicy(SourcePolicyType.`default-src`, Source.Self))
        assertTrue(result == Right(expected))
      },
      test("render single directive") {
        val cspro = ContentSecurityPolicyReportOnly(SourcePolicy(SourcePolicyType.`default-src`, Source.Self))
        assertTrue(ContentSecurityPolicyReportOnly.render(cspro) == "default-src 'self'")
      },
      test("parse multi-directive") {
        val result         = ContentSecurityPolicyReportOnly.parse("default-src 'none'; script-src 'self'")
        val expectedPolicy = Combined(
          zio.Chunk(
            SourcePolicy(SourcePolicyType.`default-src`, Source.none),
            SourcePolicy(SourcePolicyType.`script-src`, Source.Self),
          ),
        )
        assertTrue(result == Right(ContentSecurityPolicyReportOnly(expectedPolicy)))
      },
      test("render multi-directive") {
        val cspro = ContentSecurityPolicyReportOnly(
          Combined(
            zio.Chunk(
              SourcePolicy(SourcePolicyType.`default-src`, Source.none),
              SourcePolicy(SourcePolicyType.`script-src`, Source.Self),
            ),
          ),
        )
        assertTrue(ContentSecurityPolicyReportOnly.render(cspro) == "default-src 'none'; script-src 'self'")
      },
      test("round-trip") {
        val cspro = ContentSecurityPolicyReportOnly(
          Combined(
            zio.Chunk(
              SourcePolicy(SourcePolicyType.`default-src`, Source.Self),
              UpgradeInsecureRequests,
            ),
          ),
        )
        assertTrue(
          ContentSecurityPolicyReportOnly.parse(ContentSecurityPolicyReportOnly.render(cspro)) == Right(cspro),
        )
      },
      test("header name") {
        assertTrue(ContentSecurityPolicyReportOnly.name == "content-security-policy-report-only")
      },
    ),
  )
}
