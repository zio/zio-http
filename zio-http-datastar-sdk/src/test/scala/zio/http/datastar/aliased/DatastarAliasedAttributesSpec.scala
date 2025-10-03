package zio.http.datastar.aliased

import java.time.Duration

import zio._
import zio.test._

import zio.schema.{DeriveSchema, Schema}

import zio.http.template2._

object DatastarAliasedAttributesSpec extends ZIOSpecDefault {

  case class Customer(name: String, age: Int)
  object Customer { implicit val schema: Schema[Customer] = DeriveSchema.gen[Customer] }

  override def spec = suite("DatastarAttributesSpec")(
    test("basic attribute rendering") {
      val signalName = SignalName("mySignal")
      val view       = div(dataText := js"Hello", dataShow := signalName)("Body")
      val rendered   = view.render
      val expected   = "data-star-show=\"$mySignal\""
      assertTrue(rendered.contains("data-star-text=\"Hello\""), rendered.contains(expected))
    },
    test("data bind") {
      val view = a(dataBind("href"))
      assertTrue(view.render.contains("data-star-bind-href"))
    },
    test("event attribute rendering") {
      val view = button(dataOn.click := js"increment()")
      assertTrue(view.render.contains("data-star-on-click=\"increment()\""))
    },
    test("signal rendering") {
      val c        = Signal[Int]("count")
      val view     = span(dataText := c)
      val expected = """data-star-text="$count""""
      assertTrue(view.render.contains(expected))
    },
    test("nested signal via builder API") {
      val sig      = Signal.nested("bar")[Boolean]("foo")
      val view     = span(dataShow := sig)
      val expected = "data-star-show=\"$bar.foo\""
      assertTrue(view.render.contains(expected))
    },
    test("signal update rendering") {
      val customer = Signal[Customer]("customer")
      val update   = customer := Customer("Jake", 43)
      val expected = "{customer: {name: 'Jake', age: 43}}"
      assertTrue(update.toExpression.value == expected)
    },
    test("event modifiers chain renders with dots and colon before event name") {
      val view     = button(
        dataOn.click
          .debounce(3.seconds, leading = true, notrail = true)
          .prevent
          .stop := js"doStuff()",
      )
      val rendered = view.render
      assertTrue(
        rendered.contains("data-star-on-click__debounce.3000ms.leading.notrail__prevent__stop=\"doStuff()\""),
      )
    },
    test("event case modifier and throttle rendering order is preserved") {
      val view     = button(
        dataOn.click.camel
          .throttle(750.millis, noleading = true, trailing = true) := js"count++",
      )("+")
      val rendered = view.render
      assertTrue(
        rendered.contains("data-star-on-click__case.camel__throttle.750ms.noleading.trailing=\"count++\""),
      )
    },
    test("data-star-ignore-self boolean attribute short form") {
      val view     = div(dataIgnoreSelf)("body")
      val rendered = view.render
      assertTrue(rendered.contains("data-star-ignore__self"))
    },
    test("data-star-json-signals terse modifier") {
      val view     = div(dataJsonSignals.terse := js"{foo:1}")
      val rendered = view.render
      assertTrue(rendered.contains("data-star-json-signals__terse=\"{foo:1}\""))
    },
    test("data-star-ref with case modifier") {
      val view     = div(dataRef.kebab("customer-id"))
      val rendered = view.render
      assertTrue(rendered == "<div data-star-ref-customer-id__case.kebab></div>")
    },
    test("single data-star-class with case modifier bound to signal name") {
      val a        = Signal[Boolean]("active")
      val view     = div(dataClass("active").camel := a)
      val rendered = view.render
      val expected = """data-star-class-active__case.camel="$active""""
      assertTrue(rendered.contains(expected))
    },
    test("data-star-signals with snake case modifier") {
      val sig      = Signal[Int]("count")
      val view     = div(dataSignals(sig).snake := js"1")
      val rendered = view.render
      assertTrue(rendered.contains("data-star-signals-count__case.snake=\"1\""))
    },
    test("data-star-on-interval with duration and viewTransition") {
      val t        = Signal[Int]("tick")
      val view     = div(dataOnInterval.duration(Duration.ofMillis(250), leading = true).viewTransition := js"$t++")
      val rendered = view.render
      val expected = """data-star-on-interval__duration.250ms.leading__viewtransition="$tick++""""
      assertTrue(rendered.contains(expected))
    },
    test("data-star-on-load with delay and viewTransition") {
      val view     = div(dataOnLoad.delay(Duration.ofMillis(100)).viewTransition := js"init()")
      val rendered = view.render
      assertTrue(rendered.contains("data-star-on-load__delay.100ms__viewtransition=\"init()\""))
    },
    test("data-star-on-signal-patch with debounce and throttle modifiers") {
      val view     = div(
        dataOnSignalPatch
          .debounce(Duration.ofMillis(200), leading = true)
          .throttle(Duration.ofMillis(300), noleading = true, trailing = true) := js"handle()",
      )
      val rendered = view.render
      assertTrue(
        rendered.contains(
          "data-star-on-signal-patch__debounce.200ms.leading__throttle.300ms.noleading.trailing=\"handle()\"",
        ),
      )
    },
    test("SignalUpdate primitive produces assignment expression") {
      val sig  = Signal[Int]("count")
      val expr = (sig := 42).toExpression
      assertTrue(expr == Js("42"))
    },
    test("SignalUpdate root complex type produces raw JSON value (no assignment)") {
      val sig  = Signal[Customer]("customer")
      val expr = (sig := Customer("Alice", 30)).toExpression
      assertTrue(expr == Js("{customer: {name: 'Alice', age: 30}}"))
    },
    test("SignalUpdate nested complex type builds nested JSON object") {
      val sig    = Signal[Customer]("customer").nest("state")
      val update = sig := Customer("Bob", 25)
      val expr   = update.toExpression
      assertTrue(expr == Js("{state: {customer: {name: 'Bob', age: 25}}}"))
    },
  )
}
