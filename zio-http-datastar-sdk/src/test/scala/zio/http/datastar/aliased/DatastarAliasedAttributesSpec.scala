package zio.http.datastar.aliased

import java.time.Duration

import scala.annotation.nowarn

import zio._
import zio.test._

import zio.schema._

import zio.http._
import zio.http.endpoint.Endpoint
import zio.http.template2._

@nowarn("msg=possible missing interpolator")
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
      assertTrue(view.render.contains("data-star-bind:href"))
    },
    test("event attribute rendering") {
      val view = button(dataOn.click := js"increment()")
      assertTrue(view.render.contains("data-star-on:click=\"increment()\""))
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
          .debounce(3.seconds, leading = true, notrailing = true)
          .prevent
          .stop := js"doStuff()",
      )
      val rendered = view.render
      assertTrue(
        rendered.contains("data-star-on:click__debounce.3000ms.leading.notrailing__prevent__stop=\"doStuff()\""),
      )
    },
    test("event case modifier and throttle rendering order is preserved") {
      val view     = button(
        dataOn.click.camel
          .throttle(750.millis, noleading = true, trailing = true) := js"count++",
      )("+")
      val rendered = view.render
      assertTrue(
        rendered.contains("data-star-on:click__case.camel__throttle.750ms.noleading.trailing=\"count++\""),
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
      assertTrue(rendered == "<div data-star-ref:customer-id__case.kebab></div>")
    },
    test("single data-star-class with case modifier bound to signal name") {
      val a        = Signal[Boolean]("active")
      val view     = div(dataClass("active").camel := a)
      val rendered = view.render
      val expected = """data-star-class:active__case.camel="$active""""
      assertTrue(rendered.contains(expected))
    },
    test("data-star-signals with snake case modifier") {
      val sig      = Signal[Int]("count")
      val view     = div(dataSignals(sig).snake := js"1")
      val rendered = view.render
      assertTrue(rendered.contains("data-star-signals:count__case.snake=\"1\""))
    },
    test("data-star-on-interval with duration and viewTransition") {
      val t        = Signal[Int]("tick")
      val view     = div(dataOnInterval.duration(Duration.ofMillis(250), leading = true).viewTransition := js"$t++")
      val rendered = view.render
      val expected = """data-star-on-interval__duration.250ms.leading__viewtransition="$tick++""""
      assertTrue(rendered.contains(expected))
    },
    test("data-star-init with delay and viewTransition") {
      val view     = div(dataInit.delay(Duration.ofMillis(100)).viewTransition := js"init()")
      val rendered = view.render
      assertTrue(rendered.contains("data-star-init__delay.100ms__viewtransition=\"init()\""))
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
      assertTrue(expr == Js("{count: 42}"))
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
    test("Request from endpoint as attribute value") {
      val getCustomer = Endpoint(Method.GET / "customer" / int("customer-id")).out[Customer]
      val req         = getCustomer.datastarRequest(123)
      val view        = div(dataInit := req)
      val rendered    = view.render
      assertTrue(rendered == "<div data-star-init=\"@get(&#x27;/customer/123&#x27;)\"></div>")
    },
    test("Request from endpoint as attribute value and signal") {
      val getCustomer = Endpoint(Method.GET / "customer" / int("customer-id")).out[Customer]
      val signal      = Signal[Int]("customerId")
      val req         = getCustomer.datastarRequest(signal)
      val view        = div(dataInit := req)
      val rendered    = view.render
      assertTrue(rendered == "<div data-star-init=\"@get(&#x27;/customer/$customerId&#x27;)\"></div>")
    },
    test("dataSignals := with SignalUpdate renders correct expression for primitive") {
      val signal   = Signal[Int]("count")
      val update   = signal := 42
      val view     = div(dataSignals(signal) := update)
      val rendered = view.render
      assertTrue(rendered == """<div data-star-signals="{count: 42}"></div>""")
    },
    test("dataSignals := with SignalUpdate renders correct expression for complex type") {
      val signal   = Signal[Customer]("customer")
      val update   = signal := Customer("Alice", 30)
      val view     = div(dataSignals(signal) := update)
      val rendered = view.render
      assertTrue(rendered == "<div data-star-signals=\"{customer: {name: &#x27;Alice&#x27;, age: 30}}\"></div>")
    },
    test("dataComputed := with nested primitive value renders correct expression") {
      val signal   = Signal[Int]("total").nest("computed")
      val update   = signal := 500
      val view     = div(dataComputed(signal) := update)
      val rendered = view.render
      assertTrue(rendered == """<div data-star-computed="{computed: {total: 500}}"></div>""")
    },
    test("dataSignals := with direct complex value renders correct expression") {
      val signal   = Signal[Customer]("customer")
      val view     = div(dataSignals(signal) := Customer("Bob", 25))
      val rendered = view.render
      assertTrue(rendered == "<div data-star-signals=\"{customer: {name: &#x27;Bob&#x27;, age: 25}}\"></div>")
    },
    test("dataSignals := with nested signal and direct value") {
      val signal   = Signal[Customer]("customer").nest("state")
      val view     = div(dataSignals(signal) := Customer("Charlie", 35))
      val rendered = view.render
      assertTrue(
        rendered == "<div data-star-signals=\"{state: {customer: {name: &#x27;Charlie&#x27;, age: 35}}}\"></div>",
      )
    },
    test("dataComputed := with SignalUpdate renders correct expression for primitive") {
      val signal   = Signal[Int]("total")
      val update   = signal := 100
      val view     = div(dataComputed(signal) := update)
      val rendered = view.render
      assertTrue(rendered == """<div data-star-computed="{total: 100}"></div>""")
    },
    test("dataComputed := with SignalUpdate renders correct expression for complex type") {
      val signal   = Signal[Customer]("computedCustomer")
      val update   = signal := Customer("ComputedAlice", 40)
      val view     = div(dataComputed(signal) := update)
      val rendered = view.render
      assertTrue(
        rendered == "<div data-star-computed=\"{computedCustomer: {name: &#x27;ComputedAlice&#x27;, age: 40}}\"></div>",
      )
    },
    test("SignalUpdate toAssignmentExpression for primitive produces $name = value") {
      val sig  = Signal[Int]("count")
      val expr = (sig := 42).toAssignmentExpression
      assertTrue(expr == Js("$count = 42"))
    },
    test("SignalUpdate toAssignmentExpression for complex type produces $name = {object}") {
      val sig  = Signal[Customer]("customer")
      val expr = (sig := Customer("Alice", 30)).toAssignmentExpression
      assertTrue(expr == Js("$customer = {name: 'Alice', age: 30}"))
    },
    test("SignalUpdate toAssignmentExpression for nested primitive produces $outer = {nested: value}") {
      val sig  = Signal[Int]("count").nest("state")
      val expr = (sig := 42).toAssignmentExpression
      assertTrue(expr == Js("$state = {count: 42}"))
    },
    test("SignalUpdate toAssignmentExpression for nested complex produces $outer = {nested: {object}}") {
      val sig  = Signal[Customer]("customer").nest("state")
      val expr = (sig := Customer("Bob", 25)).toAssignmentExpression
      assertTrue(expr == Js("$state = {customer: {name: 'Bob', age: 25}}"))
    },
    test("SignalUpdate implicit conversion uses assignment expression for event handlers") {
      val countdown = Signal[Int]("_restoreCountdown")
      val view      = button(dataOn.click := countdown.update(0))("Reset")
      val rendered  = view.render
      assertTrue(rendered == """<button data-star-on:click="$_restoreCountdown = 0">Reset</button>""")
    },
    test("SignalUpdate implicit conversion for nested signal in event handler") {
      val nested   = Signal[Int]("value").nest("state")
      val view     = button(dataOn.click := nested.update(100))("Set")
      val rendered = view.render
      assertTrue(rendered == """<button data-star-on:click="$state = {value: 100}">Set</button>""")
    },
    test("SignalUpdate implicit conversion for complex type in event handler") {
      val customer = Signal[Customer]("customer")
      val view     = button(dataOn.click := customer.update(Customer("Alice", 30)))("Set")
      val rendered = view.render
      assertTrue(
        rendered == "<button data-star-on:click=\"$customer = {name: &#x27;Alice&#x27;, age: 30}\">Set</button>",
      )
    },
    test("dataOnIntersect basic renders correct attribute") {
      val view     = div(dataOnIntersect := js"handleIntersect()")
      val rendered = view.render
      assertTrue(rendered == """<div data-star-on-intersect="handleIntersect()"></div>""")
    },
    test("dataOnIntersect with once modifier") {
      val view     = div(dataOnIntersect.once := js"handleOnce()")
      val rendered = view.render
      assertTrue(rendered == """<div data-star-on-intersect__once="handleOnce()"></div>""")
    },
    test("dataOnIntersect with half modifier") {
      val view     = div(dataOnIntersect.half := js"handleHalf()")
      val rendered = view.render
      assertTrue(rendered == """<div data-star-on-intersect__half="handleHalf()"></div>""")
    },
    test("dataOnIntersect with full modifier") {
      val view     = div(dataOnIntersect.full := js"handleFull()")
      val rendered = view.render
      assertTrue(rendered == """<div data-star-on-intersect__full="handleFull()"></div>""")
    },
    test("dataOnIntersect with delay modifier") {
      val view     = div(dataOnIntersect.delay(Duration.ofMillis(500)) := js"handleDelay()")
      val rendered = view.render
      assertTrue(rendered == """<div data-star-on-intersect__delay.500ms="handleDelay()"></div>""")
    },
    test("dataOnIntersect with debounce modifier") {
      val view     = div(dataOnIntersect.debounce(Duration.ofMillis(300), leading = true) := js"handleDebounce()")
      val rendered = view.render
      assertTrue(rendered == """<div data-star-on-intersect__debounce.300ms.leading="handleDebounce()"></div>""")
    },
    test("dataOnIntersect with throttle modifier") {
      val view     =
        div(dataOnIntersect.throttle(Duration.ofMillis(200), noleading = true, trailing = true) := js"handleThrottle()")
      val rendered = view.render
      assertTrue(
        rendered == """<div data-star-on-intersect__throttle.200ms.noleading.trailing="handleThrottle()"></div>""",
      )
    },
    test("dataOnIntersect with viewTransition modifier") {
      val view     = div(dataOnIntersect.viewTransition := js"handleViewTransition()")
      val rendered = view.render
      assertTrue(rendered == """<div data-star-on-intersect__viewtransition="handleViewTransition()"></div>""")
    },
    test("dataOnIntersect with chained modifiers") {
      val view     = div(dataOnIntersect.once.half.viewTransition := js"handleChained()")
      val rendered = view.render
      assertTrue(rendered == """<div data-star-on-intersect__once__half__viewtransition="handleChained()"></div>""")
    },
    test("dataOnInterval basic renders correct attribute") {
      val view     = div(dataOnInterval := js"tick()")
      val rendered = view.render
      assertTrue(rendered == """<div data-star-on-interval="tick()"></div>""")
    },
    test("dataOnInterval with duration modifier") {
      val view     = div(dataOnInterval.duration(Duration.ofMillis(1000)) := js"tick()")
      val rendered = view.render
      assertTrue(rendered == """<div data-star-on-interval__duration.1000ms="tick()"></div>""")
    },
    test("dataOnInterval with duration and leading modifier") {
      val view     = div(dataOnInterval.duration(Duration.ofMillis(500), leading = true) := js"tick()")
      val rendered = view.render
      assertTrue(rendered == """<div data-star-on-interval__duration.500ms.leading="tick()"></div>""")
    },
    test("dataOnInterval with viewTransition modifier") {
      val view     = div(dataOnInterval.viewTransition := js"tick()")
      val rendered = view.render
      assertTrue(rendered == """<div data-star-on-interval__viewtransition="tick()"></div>""")
    },
    test("dataOnInterval with chained modifiers") {
      val view     = div(dataOnInterval.duration(Duration.ofMillis(250), leading = true).viewTransition := js"tick()")
      val rendered = view.render
      assertTrue(rendered == """<div data-star-on-interval__duration.250ms.leading__viewtransition="tick()"></div>""")
    },
  )
}
