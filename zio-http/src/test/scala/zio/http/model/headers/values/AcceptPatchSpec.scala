package zio.http.model.headers.values

import zio.Chunk
import zio.http.model.MimeDB
import zio.http.model.headers.values.AcceptPatch._
import zio.test._

object AcceptPatchSpec extends ZIOSpecDefault with MimeDB {
  override def spec = suite("AcceptPatch header suite")(
    test("AcceptPatch header transformation must be symmetrical") {
      assertTrue(
        AcceptPatch.toAcceptPatch(AcceptPatch.fromAcceptPatch(AcceptPatchValue(Chunk(text.`html`))))
          == AcceptPatchValue(Chunk(text.`html`))
      )
    },
    test("invalid values parsing") {
      assertTrue(AcceptPatch.toAcceptPatch("invalidString") == InvalidAcceptPatchValue) &&
        assertTrue(AcceptPatch.toAcceptPatch("") == InvalidAcceptPatchValue)
    },
  )
}
