package zio.http.codec

import java.util.UUID

import scala.util._

import zio._
import zio.test._

import zio.http._
import zio.http.codec.SegmentCodec.literal

object SegmentCodecSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("SegmentCodec")(
    test("combining literals is simplified to a single literal") {
      val combineLitLit = Try(
        "prefix" ~ "suffix",
      )

      val combineIntLitLit = Try(
        int("anInt") ~ "prefix" ~ "suffix",
      )

      val expectedLit: Try[SegmentCodec[Unit]]   = Success(SegmentCodec.Literal("prefixsuffix"))
      val expectedIntLit: Try[SegmentCodec[Int]] = Success(
        SegmentCodec.Combined(
          SegmentCodec.IntSeg("anInt"),
          SegmentCodec.Literal("prefixsuffix"),
          Combiner.combine[Int, Unit].asInstanceOf[Combiner.WithOut[Int, Unit, Int]],
        ),
      )
      assertTrue(
        combineLitLit == expectedLit,
        combineIntLitLit == expectedIntLit,
      )
    },
    test("Can't combine two string extracting segments") {
      val combineStrStr    = Try(
        string("aString") ~ string("anotherString"),
      )
      val expectedErrorMsg = "Cannot combine two string segments. Their names are aString and anotherString"
      assertTrue(combineStrStr.failed.toOption.map(_.getMessage).contains(expectedErrorMsg))
    },
    test("Can't combine two int extracting segments") {
      val combineIntInt     = Try(
        int("anInt") ~ int("anotherInt"),
      )
      val combineUUIDIntInt = Try(
        uuid("aUUID") ~ int("anInt") ~ int("anotherInt"),
      )
      val expectedErrorMsg  = "Cannot combine two numeric segments. Their names are anInt and anotherInt"
      assertTrue(
        combineIntInt.failed.toOption.map(_.getMessage).contains(expectedErrorMsg),
        combineUUIDIntInt.failed.toOption.map(_.getMessage).contains(expectedErrorMsg),
      )
    },
    test("Can't combine two long extracting segments") {
      val combineLongLong  = Try(
        long("aLong") ~ long("anotherLong"),
      )
      val uuidLongLong     = Try(
        uuid("aUUID") ~ long("aLong") ~ long("anotherLong"),
      )
      val expectedErrorMsg = "Cannot combine two numeric segments. Their names are aLong and anotherLong"
      assertTrue(
        combineLongLong.failed.toOption.map(_.getMessage).contains(expectedErrorMsg),
        uuidLongLong.failed.toOption.map(_.getMessage).contains(expectedErrorMsg),
      )
    },
    test("Can't combine an int and a long extracting segment") {
      val combineIntLong   = Try(
        int("anInt") ~ long("aLong"),
      )
      val uuidIntLong      = Try(
        uuid("aUUID") ~ int("anInt") ~ long("aLong"),
      )
      val expectedErrorMsg = "Cannot combine two numeric segments. Their names are anInt and aLong"
      assertTrue(
        combineIntLong.failed.toOption.map(_.getMessage).contains(expectedErrorMsg),
        uuidIntLong.failed.toOption.map(_.getMessage).contains(expectedErrorMsg),
      )
    },
    test("Can't combine a long and an int extracting segment") {
      val combineLongInt   = Try(
        long("aLong") ~ int("anInt"),
      )
      val uuidLongInt      = Try(
        uuid("aUUID") ~ long("aLong") ~ int("anInt"),
      )
      val expectedErrorMsg = "Cannot combine two numeric segments. Their names are aLong and anInt"
      assertTrue(
        combineLongInt.failed.toOption.map(_.getMessage).contains(expectedErrorMsg),
        uuidLongInt.failed.toOption.map(_.getMessage).contains(expectedErrorMsg),
      )
    },
    suite("matches")(
      test("uuid successful matches") {
        val codec = SegmentCodec.uuid("entityId")
        val uuid  = UUID.randomUUID().toString()
        val path  = Chunk("api", uuid)
        assertTrue(codec.matches(path, 1) == 1)
      },
    ),
  )
}
