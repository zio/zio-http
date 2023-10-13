package zio.http

import zio._
import zio.test._

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

        val flash1  = Flash.setValue("articles", Articles(List(Article("m\"i`l'k", 2.99), Article("choco", 4.99))))
        val flash2  = Flash.setValue("dataMap", Map("a" -> "A", "b" -> "B", "c" -> "CCC\"CCC\"CCCCC"))
        val flash3  = Flash.setValue("dataList", List("a", "b", "c"))
        val flash4  = Flash.setValue("articlesTuple", Article("a", 1.00) -> Article("b", 2.00))
        val cookie1 = Flash.Setter.run(flash1 ++ flash2 ++ flash3 ++ flash4)

        val cookie2 = Cookie.Request(Flash.COOKIE_NAME, cookie1.content)
        val request = Request(headers = Headers(Header.Cookie(NonEmptyChunk(cookie2))))

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
      test("flash message") {
        val flashMessageDefaultBoth      = Flash.setNotice[String]("notice") ++ Flash.setAlert[String]("alert")
        val flashMessageCustomBoth       =
          Flash.setValue("custom-notice", Article("custom-notice", 10)) ++ Flash.setValue(
            "custom-alert",
            List("custom", "alert"),
          )
        val flashMessageCustomOnlyNotice = Flash.setValue("custom-notice-only", "custom-notice-only-value")
        val flashMessageCustomOnlyAlert  = Flash.setValue("custom-alert-only", "custom-alert-only")

        val cookie1 = Flash.Setter.run(
          flashMessageCustomBoth ++ flashMessageDefaultBoth ++ flashMessageCustomOnlyNotice ++ flashMessageCustomOnlyAlert,
        )
        val cookie2 = Cookie.Request(Flash.COOKIE_NAME, cookie1.content)
        val request = Request(headers = Headers(Header.Cookie(NonEmptyChunk(cookie2))))

        assertTrue(request.flash(Flash.getMessageHtml).get.isBoth) &&
        assertTrue(
          request
            .flash(Flash.getMessage(Flash.get[Article]("custom-notice"), Flash.get[List[String]]("custom-alert")))
            .get
            .isBoth,
        ) &&
        assertTrue(request.flash(Flash.getMessage(Flash.get[Article], Flash.get[List[String]])).get.isBoth) &&
        assertTrue(
          request
            .flash(Flash.getMessage(Flash.getString("custom-notice-only"), Flash.getInt("does-not-exist")))
            .get
            .isNotice,
        ) &&
        assertTrue(
          request
            .flash(Flash.getMessage(Flash.getInt("does-not-exist"), Flash.getString("custom-alert-only")))
            .get
            .isAlert,
        )
      },
      test("flash backend") {

        import zio.http.template._

        object ui {
          def flashEmpty                                 = Html.fromString("no-flash")
          def flashBoth(notice: Html, alert: Html): Html = notice ++ alert
          def flashNotice(html: Html): Html              = div(styleAttr := Seq("background" -> "green"), html)
          def flashAlert(html: Html): Html               = div(styleAttr := Seq("background" -> "red"), html)
        }

        val routeUserSavePath = Method.POST / "users" / "save"
        val routeUserSave     = routeUserSavePath -> handler {
          for {
            flashBackend <- ZIO.service[Flash.Backend]
            respose      <- flashBackend.addFlash(
              Response.seeOther(URL.empty / "users"),
              Flash.setNotice("user saved successfully"),
            )
          } yield respose
        }

        val routeConfirmPath = Method.GET / "users"
        val routeConfirm     = routeConfirmPath -> handler { (req: Request) =>
          for {
            flashBackend <- ZIO.service[Flash.Backend]
            html         <- flashBackend.flashOrElse(
              req,
              Flash.getMessageHtml.foldHtml(ui.flashNotice, ui.flashAlert)(ui.flashBoth),
            )(ui.flashEmpty)
          } yield Response.html(html)
        }

        val app = Routes(routeUserSave, routeConfirm).toHttpApp

        for {
          response1 <- app.runZIO(Request.post(URL(routeUserSavePath.format(()).toOption.get), Body.empty))
          flashString = response1.header(Header.SetCookie).get.value.content
          cookie      = Cookie.Request(Flash.COOKIE_NAME, flashString)
          response2  <- app.runZIO(
            Request(
              method = Method.GET,
              url = URL(routeConfirmPath.format(()).toOption.get),
              headers = Headers(Header.Cookie(NonEmptyChunk(cookie))),
            ),
          )
          bodyString <- response2.body.asString
        } yield assertTrue(bodyString.contains("successfully") && bodyString.contains("green"))
      }.provideLayer(Flash.Backend.layer),
    )

}
