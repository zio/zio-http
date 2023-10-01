package zio.http

import zio.test._
import zio.{NonEmptyChunk, ZIO}

import zio.schema.{DeriveSchema, Schema}

object FlashSpec extends ZIOHttpSpec {

  case class Article(name: String, price: Double)
  object Article {
    implicit val schema: Schema[Article] = DeriveSchema.gen
  }

  case class Articles(list: List[Article])

  object Articles {
    implicit val schema: Schema[Articles] = DeriveSchema.gen
  }

  override def spec =
    suite("flash")(
      test("set and get") {

        val flash1  = Flash.set("articles", Articles(List(Article("m\"i`l'k", 2.99), Article("choco", 4.99))))
        val flash2  = Flash.set("dataMap", Map("a" -> "A", "b" -> "B", "c" -> "CCC\"CCC\"CCCCC"))
        val flash3  = Flash.set("dataList", List("a", "b", "c"))
        val flash4  = Flash.set("articlesTuple", Article("a", 1.00) -> Article("b", 2.00))
        val cookie1 = Flash.Setter.run(flash1 ++ flash2 ++ flash3 ++ flash4)

        val cookie2 = Cookie.Request(Flash.COOKIE_NAME, cookie1.content)
        val request = Request(headers = Headers(Header.Cookie(NonEmptyChunk(cookie2))))

        val aaa = request.flashWithZIO(Flash.get[Articles])(a => ZIO.succeed(a))

        assertTrue(request.flash(Flash.get[Articles]("does-not-exist") <> Flash.get[Articles]("articles")).isDefined) &&
        assertTrue(request.flash(Flash.get[Map[String, String]]).isDefined) &&
        assertTrue(
          request
            .flash(Flash.get[Articles]("articles").zip(Flash.get[Map[String, String]]).map { case (a, b) =>
              s" ---> $a @@@ $b <---- "
            })
            .isDefined,
        ) &&
        assertTrue(
          request.flash(Flash.getString("articles").optional.zip(Flash.getDouble("bbb").optional)).isDefined,
        ) &&
        assertTrue(request.flash(Flash.get[List[String]]("dataList")).isDefined) &&
        assertTrue(request.flash(Flash.get[List[String]]).isDefined) &&
        assertTrue(request.flash(Flash.get[List[Int]]).isEmpty) &&
        assertTrue(request.flash(Flash.get[(Article, Article)]("articlesTuple")).isDefined)
      },
    )

}
