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

import zio.Scope
import zio.test._

import zio.http.model.headers.values.ETag.{InvalidETagValue, StrongETagValue, WeakETagValue}

object ETagSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ETag header suite")(
    test("parse ETag header") {
      assertTrue(ETag.toETag("""W/"testEtag"""") == WeakETagValue("testEtag"))
      assertTrue(ETag.toETag("""w/"testEtag"""") == WeakETagValue("testEtag"))
      assertTrue(ETag.toETag(""""testEtag"""") == StrongETagValue("testEtag"))
      assertTrue(ETag.toETag("W/Etag") == InvalidETagValue)
      assertTrue(ETag.toETag("Etag") == InvalidETagValue)
      assertTrue(ETag.toETag("""W/""""") == WeakETagValue(""))
      assertTrue(ETag.toETag("""""""") == StrongETagValue(""))
    },
    test("encode ETag header to String") {
      assertTrue(ETag.fromETag(StrongETagValue("TestEtag")) == """"TestEtag"""")
      assertTrue(ETag.fromETag(WeakETagValue("TestEtag")) == """W/"TestEtag"""")
      assertTrue(ETag.fromETag(InvalidETagValue) == "")
    },
    test("parsing and encoding are symmetrical") {
      assertTrue(ETag.fromETag(ETag.toETag("""w/"testEtag"""")) == """W/"testEtag"""")
      assertTrue(ETag.fromETag(ETag.toETag("""W/"testEtag"""")) == """W/"testEtag"""")
      assertTrue(ETag.fromETag(ETag.toETag(""""testEtag"""")) == """"testEtag"""")

    },
  )
}
