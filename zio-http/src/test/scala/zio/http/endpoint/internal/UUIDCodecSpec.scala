package zio.http.endpoint.internal

import java.util.UUID

import zio.Scope
import zio.test._

import zio.http.codec.TextCodec.UUIDCodec

object UUIDCodecSpec extends ZIOSpecDefault {
  val correctUUID                                          = UUID.randomUUID().toString
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("UUIDCodec")(
      test("Should correctly validate random UUID") {
        checkN(5)(Gen.uuid) { uuid => assertTrue(UUIDCodec.isDefinedAt(uuid.toString)) }
      },
      test("Should fail to validate empty String") {
        assertTrue(!UUIDCodec.isDefinedAt(""))
      },
      test("Should fail to validate String with trailing hyphen") {
        assertTrue(!UUIDCodec.isDefinedAt(correctUUID + "-"))
      },
      test("Should fail to validate String with leading hyphen") {
        assertTrue(!UUIDCodec.isDefinedAt("-" + correctUUID))
      },
      test("Should work if UUID has only 0s") {
        assertTrue(UUIDCodec.isDefinedAt("00000000-0000-0000-0000-000000000000"))
      },
      test("Should fail if UUID has incorrect character") {
        assertTrue(!UUIDCodec.isDefinedAt("0000000Z-0000-0000-0000-000000000000"))
      },
      test("Should fail if UUID has two hyphens") {
        assertTrue(!UUIDCodec.isDefinedAt("00000000--0000-0000-0000-000000000000"))
      },
    )
}
