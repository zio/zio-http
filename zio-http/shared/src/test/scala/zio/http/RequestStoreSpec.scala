package zio.http

import zio.{Scope, UIO}
import zio.test._

object RequestStoreSpec extends ZIOHttpSpec {

  // Shared case classes for all tests
  private final case class T1(v: Int)
  private final case class T2(v: Int)
  private final case class T3(v: Int)
  private final case class T4(v: Int)
  private final case class T5(v: Int)
  private final case class T6(v: Int)
  private final case class T7(v: Int)
  private final case class T8(v: Int)
  private final case class T9(v: Int)
  private final case class T10(v: Int)
  private final case class T11(v: Int)
  private final case class T12(v: Int)
  private final case class T13(v: Int)
  private final case class T14(v: Int)
  private final case class T15(v: Int)
  private final case class T16(v: Int)
  private final case class T17(v: Int)
  private final case class T18(v: Int)
  private final case class T19(v: Int)
  private final case class T20(v: Int)
  private final case class T21(v: Int)
  private final case class T22(v: Int)

  // Types that are never set (for testing empty results)
  private final case class E1(v: Int)
  private final case class E2(v: Int)
  private final case class E3(v: Int)

  // Shared instances for tests
  private val t1  = T1(1)
  private val t2  = T2(2)
  private val t3  = T3(3)
  private val t4  = T4(4)
  private val t5  = T5(5)
  private val t6  = T6(6)
  private val t7  = T7(7)
  private val t8  = T8(8)
  private val t9  = T9(9)
  private val t10 = T10(10)
  private val t11 = T11(11)
  private val t12 = T12(12)
  private val t13 = T13(13)
  private val t14 = T14(14)
  private val t15 = T15(15)
  private val t16 = T16(16)
  private val t17 = T17(17)
  private val t18 = T18(18)
  private val t19 = T19(19)
  private val t20 = T20(20)
  private val t21 = T21(21)
  private val t22 = T22(22)

  // Expected tuples for getMany tests (avoid Scala 2.12 "adapting argument list" warnings)
  // format: off
  private val expected2  = (Some(t1), Some(t2))
  private val expected3  = (Some(t1), Some(t2), Some(t3))
  private val expected4  = (Some(t1), Some(t2), Some(t3), Some(t4))
  private val expected5  = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5))
  private val expected6  = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6))
  private val expected7  = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7))
  private val expected8  = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8))
  private val expected9  = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8), Some(t9))
  private val expected10 = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8), Some(t9), Some(t10))
  private val expected11 = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8), Some(t9), Some(t10), Some(t11))
  private val expected12 = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8), Some(t9), Some(t10), Some(t11), Some(t12))
  private val expected13 = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8), Some(t9), Some(t10), Some(t11), Some(t12), Some(t13))
  private val expected14 = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8), Some(t9), Some(t10), Some(t11), Some(t12), Some(t13), Some(t14))
  private val expected15 = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8), Some(t9), Some(t10), Some(t11), Some(t12), Some(t13), Some(t14), Some(t15))
  private val expected16 = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8), Some(t9), Some(t10), Some(t11), Some(t12), Some(t13), Some(t14), Some(t15), Some(t16))
  private val expected17 = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8), Some(t9), Some(t10), Some(t11), Some(t12), Some(t13), Some(t14), Some(t15), Some(t16), Some(t17))
  private val expected18 = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8), Some(t9), Some(t10), Some(t11), Some(t12), Some(t13), Some(t14), Some(t15), Some(t16), Some(t17), Some(t18))
  private val expected19 = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8), Some(t9), Some(t10), Some(t11), Some(t12), Some(t13), Some(t14), Some(t15), Some(t16), Some(t17), Some(t18), Some(t19))
  private val expected20 = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8), Some(t9), Some(t10), Some(t11), Some(t12), Some(t13), Some(t14), Some(t15), Some(t16), Some(t17), Some(t18), Some(t19), Some(t20))
  private val expected21 = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8), Some(t9), Some(t10), Some(t11), Some(t12), Some(t13), Some(t14), Some(t15), Some(t16), Some(t17), Some(t18), Some(t19), Some(t20), Some(t21))
  private val expected22 = (Some(t1), Some(t2), Some(t3), Some(t4), Some(t5), Some(t6), Some(t7), Some(t8), Some(t9), Some(t10), Some(t11), Some(t12), Some(t13), Some(t14), Some(t15), Some(t16), Some(t17), Some(t18), Some(t19), Some(t20), Some(t21), Some(t22))
  // format: on

  // Helper method to set all 22 test values in the RequestStore
  // format: off
  private def setAll: UIO[Unit] =
    RequestStore.set(t1) *> RequestStore.set(t2) *> RequestStore.set(t3) *> RequestStore.set(t4) *>
    RequestStore.set(t5) *> RequestStore.set(t6) *> RequestStore.set(t7) *> RequestStore.set(t8) *>
    RequestStore.set(t9) *> RequestStore.set(t10) *> RequestStore.set(t11) *> RequestStore.set(t12) *>
    RequestStore.set(t13) *> RequestStore.set(t14) *> RequestStore.set(t15) *> RequestStore.set(t16) *>
    RequestStore.set(t17) *> RequestStore.set(t18) *> RequestStore.set(t19) *> RequestStore.set(t20) *>
    RequestStore.set(t21) *> RequestStore.set(t22)
  // format: on

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("RequestStore")(
      suite(".get")(
        test("returns None when value is not set") {
          for {
            result <- RequestStore.get[T1]
          } yield assertTrue(result.isEmpty)
        },
        test("returns Some when value is set") {
          for {
            _      <- RequestStore.set(t1)
            result <- RequestStore.get[T1]
          } yield assertTrue(result.contains(t1))
        },
      ),
      suite(".getOrElse")(
        test("returns default when value is not set") {
          for {
            result <- RequestStore.getOrElse(t2)
          } yield assertTrue(result == t2)
        },
        test("returns value when set") {
          for {
            _      <- RequestStore.set(t3)
            result <- RequestStore.getOrElse(T3(0))
          } yield assertTrue(result == t3)
        },
      ),
      suite(".getOrFail")(
        test("fails when value is not set") {
          for {
            result <- RequestStore.getOrFail[T4].either
          } yield assertTrue(result.isLeft)
        },
        test("succeeds when value is set") {
          for {
            _      <- RequestStore.set(t5)
            result <- RequestStore.getOrFail[T5]
          } yield assertTrue(result == t5)
        },
      ),
      suite(".set")(
        test("stores value by type") {
          for {
            _       <- RequestStore.set(t6)
            _       <- RequestStore.set(t7)
            result1 <- RequestStore.get[T6]
            result2 <- RequestStore.get[T7]
          } yield assertTrue(
            result1.contains(t6),
            result2.contains(t7),
          )
        },
        test("overwrites previous value of same type") {
          val secondValue = T8(20)
          for {
            _      <- RequestStore.set(t8)
            _      <- RequestStore.set(secondValue)
            result <- RequestStore.get[T8]
          } yield assertTrue(result.contains(secondValue))
        },
      ),
      suite(".update")(
        test("updates value when present") {
          val expected = T9(14)
          for {
            _      <- RequestStore.set(t9)
            _      <- RequestStore.update[T9](_.map(t => T9(t.v + 5)).getOrElse(T9(0)))
            result <- RequestStore.get[T9]
          } yield assertTrue(result.contains(expected))
        },
        test("sets value when not present") {
          for {
            _      <- RequestStore.update[T10](_ => t10)
            result <- RequestStore.get[T10]
          } yield assertTrue(result.contains(t10))
        },
      ),
      suite(".storeRequest")(
        test("stores the request when applied to a handler") {
          val request = Request.get(URL.root / "test")
          val handler = Handler.ok @@ RequestStore.storeRequest
          for {
            _      <- handler(request)
            result <- RequestStore.getRequest
          } yield assertTrue(result.contains(request))
        },
      ),
      suite(".getRequest")(
        test("returns None when request is not set") {
          for {
            result <- RequestStore.getRequest
          } yield assertTrue(result.isEmpty)
        },
        test("returns Some when request is set") {
          val request = Request.get(URL.root)
          for {
            _      <- RequestStore.set(request)
            result <- RequestStore.getRequest
          } yield assertTrue(result.contains(request))
        },
      ),
      suite(".getRequestOrFail")(
        test("fails when request is not set") {
          for {
            result <- RequestStore.getRequestOrFail.either
          } yield assertTrue(result.isLeft)
        },
        test("succeeds when request is set") {
          val request = Request.get(URL.root)
          for {
            _      <- RequestStore.set(request)
            result <- RequestStore.getRequestOrFail
          } yield assertTrue(result == request)
        },
      ),
      suite(".getMany")(
        test("with 2 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2]
          } yield assertTrue(result == expected2)
        },
        test("with 3 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3]
          } yield assertTrue(result == expected3)
        },
        test("with 4 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3, T4]
          } yield assertTrue(result == expected4)
        },
        test("with 5 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3, T4, T5]
          } yield assertTrue(result == expected5)
        },
        test("with 6 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3, T4, T5, T6]
          } yield assertTrue(result == expected6)
        },
        test("with 7 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3, T4, T5, T6, T7]
          } yield assertTrue(result == expected7)
        },
        test("with 8 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3, T4, T5, T6, T7, T8]
          } yield assertTrue(result == expected8)
        },
        test("with 9 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3, T4, T5, T6, T7, T8, T9]
          } yield assertTrue(result == expected9)
        },
        test("with 10 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10]
          } yield assertTrue(result == expected10)
        },
        test("with 11 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11]
          } yield assertTrue(result == expected11)
        },
        test("with 12 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12]
          } yield assertTrue(result == expected12)
        },
        test("with 13 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13]
          } yield assertTrue(result == expected13)
        },
        test("with 14 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14]
          } yield assertTrue(result == expected14)
        },
        test("with 15 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15]
          } yield assertTrue(result == expected15)
        },
        test("with 16 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16]
          } yield assertTrue(result == expected16)
        },
        test("with 17 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17]
          } yield assertTrue(result == expected17)
        },
        test("with 18 types") {
          for {
            _      <- setAll
            result <- RequestStore
              .getMany[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18]
          } yield assertTrue(result == expected18)
        },
        test("with 19 types") {
          for {
            _      <- setAll
            result <- RequestStore
              .getMany[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19]
          } yield assertTrue(result == expected19)
        },
        test("with 20 types") {
          for {
            _      <- setAll
            result <- RequestStore
              .getMany[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20]
          } yield assertTrue(result == expected20)
        },
        test("with 21 types") {
          for {
            _      <- setAll
            result <- RequestStore
              .getMany[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21]
          } yield assertTrue(result == expected21)
        },
        test("with 22 types") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[
              T1,
              T2,
              T3,
              T4,
              T5,
              T6,
              T7,
              T8,
              T9,
              T10,
              T11,
              T12,
              T13,
              T14,
              T15,
              T16,
              T17,
              T18,
              T19,
              T20,
              T21,
              T22,
            ]
          } yield assertTrue(result == expected22)
        },
        test("returns None for types not set - E1 to E3 are never set") {
          for {
            _      <- setAll
            result <- RequestStore.getMany[T1, E1, T2, E2, E3]
          } yield assertTrue(
            result._1.contains(t1),
            result._2.isEmpty,
            result._3.contains(t2),
            result._4.isEmpty,
            result._5.isEmpty,
          )
        },
      ),
    )
}
