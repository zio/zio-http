package zio.http.datastar

import zio.test._

import zio.schema._

import zio.http.datastar.Attributes.{CaseModifier, DataBind}
import zio.http.template2._

object CaseModifierPrecedenceSpec extends ZIOSpecDefault {

  case class Customer(name: String, age: Int)
  object Customer { implicit val schema: Schema[Customer] = DeriveSchema.gen[Customer] }

  override def spec = suite("CaseModifierPrecedenceSpec")(
    suite("SignalName case modifier")(
      test("default case modifier is Camel") {
        val name = SignalName("mySignal")
        assertTrue(name.ref == "$mySignal")
      },
      test("explicit Camel case modifier") {
        val name = SignalName(CaseModifier.Camel)("mySignal")
        assertTrue(name.ref == "$mySignal")
      },
      test("Kebab case modifier transforms signal ref") {
        val name = SignalName(CaseModifier.Kebab)("mySignal")
        assertTrue(name.ref == "$my-signal")
      },
      test("Snake case modifier transforms signal ref") {
        val name = SignalName(CaseModifier.Snake)("mySignal")
        assertTrue(name.ref == "$my_signal")
      },
      test("Pascal case modifier transforms signal ref") {
        val name = SignalName(CaseModifier.Pascal)("mySignal")
        assertTrue(name.ref == "$MySignal")
      },
      test("case modifier can be changed after creation") {
        val original = SignalName("mySignal")
        val modified = original.caseModifier(CaseModifier.Kebab)
        assertTrue(
          original.ref == "$mySignal",
          modified.ref == "$my-signal",
        )
      },
      test("name property always uses kebab case for attribute names") {
        val camelName  = SignalName(CaseModifier.Camel)("mySignal")
        val kebabName  = SignalName(CaseModifier.Kebab)("mySignal")
        val snakeName  = SignalName(CaseModifier.Snake)("mySignal")
        val pascalName = SignalName(CaseModifier.Pascal)("mySignal")
        assertTrue(
          camelName.name == "my-signal",
          kebabName.name == "my-signal",
          snakeName.name == "my-signal",
          pascalName.name == "my-signal",
        )
      },
    ),
    suite("Signal case modifier propagation")(
      test("Signal inherits case modifier from construction") {
        val signal = Signal[Int]("mySignal")
        assertTrue(signal.ref == "$mySignal")
      },
      test("Signal case modifier can be changed") {
        val signal   = Signal[Int]("mySignal")
        val modified = signal.caseModifier(CaseModifier.Kebab)
        assertTrue(modified.ref == "$my-signal")
      },
      test("nested Signal preserves case modifier") {
        val signal = Signal.nested("outer")[Int]("inner")
        assertTrue(signal.ref == "$outer.inner")
      },
      test("nested Signal with explicit case modifier") {
        val signal = Signal.nested("outer")[Int](CaseModifier.Kebab)("myInner")
        assertTrue(signal.ref == "$outer.my-inner")
      },
    ),
    suite("dataBind case modifier precedence")(
      test("dataBind uses signal's case modifier for attribute suffix") {
        val signal   = Signal[String]("myValue").caseModifier(CaseModifier.Kebab)
        val view     = input(dataBind(signal))
        val rendered = view.render
        assertTrue(rendered.contains("data-bind:my-value__case.kebab"))
      },
      test("dataBind partial case modifier affects signal name transformation") {
        val view     = input(dataBind.kebab("myValue"))
        val rendered = view.render
        assertTrue(rendered.contains("data-bind:my-value__case.kebab"))
      },
      test("dataBind with camel (default) has no suffix") {
        val view     = input(dataBind("myValue"))
        val rendered = view.render
        assertTrue(
          rendered.contains("data-bind:my-value"),
          !rendered.contains("__case"),
        )
      },
      test("dataBind with SignalName uses SignalName's case modifier") {
        val signalName = SignalName(CaseModifier.Snake)("myValue")
        val view       = input(dataBind(signalName))
        val rendered   = view.render
        assertTrue(rendered.contains("data-bind:my-value__case.snake"))
      },
      test("DataBind case modifier methods update both caseModifier and signalName") {
        val bind     = DataBind("data", SignalName("myValue"), CaseModifier.Camel)
        val modified = bind.kebab
        val attr     = Attributes.DataBind.toAttribute(modified)
        assertTrue(attr.name == "data-bind:my-value__case.kebab")
      },
    ),
    suite("dataRef case modifier precedence")(
      test("dataRef uses SignalName's case modifier for attribute suffix") {
        val signalName = SignalName(CaseModifier.Pascal)("myRef")
        val view       = div(dataRef(signalName))
        val rendered   = view.render
        assertTrue(rendered.contains("data-ref:my-ref__case.pascal"))
      },
      test("dataRef partial case modifier affects signal name") {
        val view     = div(dataRef.snake("myRef"))
        val rendered = view.render
        assertTrue(rendered.contains("data-ref:my-ref__case.snake"))
      },
      test("dataRef with camel (default) has no suffix") {
        val view     = div(dataRef("myRef"))
        val rendered = view.render
        assertTrue(
          rendered.contains("data-ref:my-ref"),
          !rendered.contains("__case"),
        )
      },
    ),
    suite("dataSignals case modifier precedence")(
      test("dataSignals uses signal's case modifier for attribute suffix") {
        val signal   = Signal[Int]("myCount").caseModifier(CaseModifier.Snake)
        val view     = div(dataSignals(signal) := js"0")
        val rendered = view.render
        assertTrue(rendered.contains("data-signals:my-count__case.snake"))
      },
      test("dataSignals partial snake modifier") {
        val signal   = Signal[Int]("myCount")
        val view     = div(dataSignals(signal).snake := js"0")
        val rendered = view.render
        assertTrue(rendered.contains("data-signals:my-count__case.snake"))
      },
      test("dataSignals case modifier methods update signal's case modifier") {
        val signal   = Signal[Int]("myCount")
        val attr     = dataSignals(signal).kebab
        val view     = div(attr := js"1")
        val rendered = view.render
        assertTrue(rendered.contains("data-signals:my-count__case.kebab"))
      },
    ),
    suite("dataIndicator case modifier precedence")(
      test("dataIndicator uses signal's case modifier") {
        val signal   = Signal[Boolean]("isLoading").caseModifier(CaseModifier.Pascal)
        val view     = div(dataIndicator(signal))
        val rendered = view.render
        assertTrue(rendered.contains("data-indicator:is-loading__case.pascal"))
      },
      test("dataIndicator partial case modifier") {
        val view     = div(dataIndicator.kebab("isLoading"))
        val rendered = view.render
        assertTrue(rendered.contains("data-indicator:is-loading__case.kebab"))
      },
      test("dataIndicator case modifier methods") {
        val attr     = dataIndicator("isLoading").snake
        val view     = div(attr)
        val rendered = view.render
        assertTrue(rendered.contains("data-indicator:is-loading__case.snake"))
      },
    ),
    suite("dataComputed case modifier precedence")(
      test("dataComputed uses signal's case modifier for suffix") {
        val signal   = Signal[Int]("computedValue").caseModifier(CaseModifier.Kebab)
        val view     = div(dataComputed(signal) := js"1 + 1")
        val rendered = view.render
        assertTrue(rendered.contains("data-computed:computed-value__case.kebab"))
      },
      test("dataComputed partial case modifier") {
        val view     = div(dataComputed(CaseModifier.Snake)("computedValue")(Schema[Int]) := js"2 + 2")
        val rendered = view.render
        assertTrue(rendered.contains("data-computed:computed-value__case.snake"))
      },
    ),
    suite("dataClass case modifier")(
      test("dataClass single with camel (default) has suffix when not default") {
        val signal   = Signal[Boolean]("isActive")
        val view     = div(dataClass("my-class").camel := signal)
        val rendered = view.render
        assertTrue(rendered.contains("data-class:my-class__case.camel"))
      },
      test("dataClass single with kebab (attribute default) has no suffix") {
        val signal   = Signal[Boolean]("isActive")
        val view     = div(dataClass("my-class").kebab := signal)
        val rendered = view.render
        assertTrue(
          rendered.contains("data-class:my-class"),
          !rendered.contains("__case"),
        )
      },
      test("dataClass case modifier applied via parent function") {
        val signal   = Signal[Boolean]("isActive")
        val view     = div(dataClass(CaseModifier.Snake)("my_class") := signal)
        val rendered = view.render
        assertTrue(rendered.contains("data-class:my_class__case.snake"))
      },
    ),
    suite("CaseModifier suffix rendering")(
      test("Camel suffix is empty when Camel is default") {
        assertTrue(CaseModifier.Camel.suffix(CaseModifier.Camel) == "")
      },
      test("Kebab suffix is empty when Kebab is default") {
        assertTrue(CaseModifier.Kebab.suffix(CaseModifier.Kebab) == "")
      },
      test("Camel suffix when Kebab is default") {
        assertTrue(CaseModifier.Camel.suffix(CaseModifier.Kebab) == "__case.camel")
      },
      test("Kebab suffix when Camel is default") {
        assertTrue(CaseModifier.Kebab.suffix(CaseModifier.Camel) == "__case.kebab")
      },
      test("Snake suffix is always rendered") {
        assertTrue(CaseModifier.Snake.suffix(CaseModifier.Camel) == "__case.snake")
        assertTrue(CaseModifier.Snake.suffix(CaseModifier.Kebab) == "__case.snake")
      },
      test("Pascal suffix is always rendered") {
        assertTrue(CaseModifier.Pascal.suffix(CaseModifier.Camel) == "__case.pascal")
        assertTrue(CaseModifier.Pascal.suffix(CaseModifier.Kebab) == "__case.pascal")
      },
    ),
    suite("CaseModifier.modify method")(
      suite("Camel case transformation")(
        test("transforms snake_case to camelCase") {
          assertTrue(CaseModifier.Camel.modify("my_signal_name") == "mySignalName")
        },
        test("transforms kebab-case to camelCase") {
          assertTrue(CaseModifier.Camel.modify("my-signal-name") == "mySignalName")
        },
        test("preserves already camelCase") {
          assertTrue(CaseModifier.Camel.modify("mySignalName") == "mySignalName")
        },
        test("transforms PascalCase to camelCase") {
          assertTrue(CaseModifier.Camel.modify("MySignalName") == "mySignalName")
        },
        test("handles single word") {
          assertTrue(CaseModifier.Camel.modify("signal") == "signal")
        },
        test("handles single uppercase word") {
          assertTrue(CaseModifier.Camel.modify("Signal") == "signal")
        },
      ),
      suite("Kebab case transformation")(
        test("transforms camelCase to kebab-case") {
          assertTrue(CaseModifier.Kebab.modify("mySignalName") == "my-signal-name")
        },
        test("transforms snake_case to kebab-case") {
          assertTrue(CaseModifier.Kebab.modify("my_signal_name") == "my-signal-name")
        },
        test("preserves already kebab-case") {
          assertTrue(CaseModifier.Kebab.modify("my-signal-name") == "my-signal-name")
        },
        test("transforms PascalCase to kebab-case") {
          assertTrue(CaseModifier.Kebab.modify("MySignalName") == "my-signal-name")
        },
        test("handles single word") {
          assertTrue(CaseModifier.Kebab.modify("signal") == "signal")
        },
        test("handles single uppercase word") {
          assertTrue(CaseModifier.Kebab.modify("Signal") == "signal")
        },
      ),
      suite("Snake case transformation")(
        test("transforms camelCase to snake_case") {
          assertTrue(CaseModifier.Snake.modify("mySignalName") == "my_signal_name")
        },
        test("transforms kebab-case to snake_case") {
          assertTrue(CaseModifier.Snake.modify("my-signal-name") == "my_signal_name")
        },
        test("preserves already snake_case") {
          assertTrue(CaseModifier.Snake.modify("my_signal_name") == "my_signal_name")
        },
        test("transforms PascalCase to snake_case") {
          assertTrue(CaseModifier.Snake.modify("MySignalName") == "my_signal_name")
        },
        test("handles single word") {
          assertTrue(CaseModifier.Snake.modify("signal") == "signal")
        },
        test("handles single uppercase word") {
          assertTrue(CaseModifier.Snake.modify("Signal") == "signal")
        },
      ),
      suite("Pascal case transformation")(
        test("transforms camelCase to PascalCase") {
          assertTrue(CaseModifier.Pascal.modify("mySignalName") == "MySignalName")
        },
        test("transforms snake_case to PascalCase") {
          assertTrue(CaseModifier.Pascal.modify("my_signal_name") == "MySignalName")
        },
        test("transforms kebab-case to PascalCase") {
          assertTrue(CaseModifier.Pascal.modify("my-signal-name") == "MySignalName")
        },
        test("preserves already PascalCase") {
          assertTrue(CaseModifier.Pascal.modify("MySignalName") == "MySignalName")
        },
        test("handles single word") {
          assertTrue(CaseModifier.Pascal.modify("signal") == "Signal")
        },
        test("handles single uppercase word") {
          assertTrue(CaseModifier.Pascal.modify("Signal") == "Signal")
        },
      ),
      suite("Private signal underscore prefix preservation")(
        test("Camel preserves underscore prefix") {
          assertTrue(CaseModifier.Camel.modify("_privateSignal") == "_privateSignal")
        },
        test("Kebab preserves underscore prefix") {
          assertTrue(CaseModifier.Kebab.modify("_privateSignal") == "_private-signal")
        },
        test("Snake preserves underscore prefix") {
          assertTrue(CaseModifier.Snake.modify("_privateSignal") == "_private_signal")
        },
        test("Pascal preserves underscore prefix") {
          assertTrue(CaseModifier.Pascal.modify("_privateSignal") == "_PrivateSignal")
        },
        test("underscore prefix with snake_case input") {
          assertTrue(CaseModifier.Camel.modify("_my_private_signal") == "_myPrivateSignal")
          assertTrue(CaseModifier.Kebab.modify("_my_private_signal") == "_my-private-signal")
          assertTrue(CaseModifier.Snake.modify("_my_private_signal") == "_my_private_signal")
          assertTrue(CaseModifier.Pascal.modify("_my_private_signal") == "_MyPrivateSignal")
        },
        test("underscore prefix with kebab-case input") {
          assertTrue(CaseModifier.Camel.modify("_my-private-signal") == "_myPrivateSignal")
          assertTrue(CaseModifier.Kebab.modify("_my-private-signal") == "_my-private-signal")
          assertTrue(CaseModifier.Snake.modify("_my-private-signal") == "_my_private_signal")
          assertTrue(CaseModifier.Pascal.modify("_my-private-signal") == "_MyPrivateSignal")
        },
        test("underscore is not modified when it's the entire prefix") {
          // The underscore should remain as a single character prefix
          val result = CaseModifier.Camel.modify("_x")
          assertTrue(result == "_x")
        },
      ),
      suite("Edge cases")(
        test("empty string after underscore") {
          // This tests behavior with just underscore - though this would likely be invalid
          // The implementation drops the first char if it's underscore
          assertTrue(CaseModifier.Camel.modify("_") == "_")
        },
        test("multiple consecutive uppercase letters") {
          assertTrue(CaseModifier.Kebab.modify("myHTTPServer") == "my-httpserver")
          assertTrue(CaseModifier.Snake.modify("myHTTPServer") == "my_http_server")
        },
        test("numbers in signal names") {
          assertTrue(CaseModifier.Kebab.modify("signal1Name") == "signal1-name")
          assertTrue(CaseModifier.Snake.modify("signal1Name") == "signal1_name")
          assertTrue(CaseModifier.Camel.modify("signal_1_name") == "signal1Name")
          assertTrue(CaseModifier.Pascal.modify("signal_1_name") == "Signal1Name")
        },
      ),
    ),
    suite("combined attribute and signal case modifiers")(
      test("signal case modifier takes precedence in attribute rendering") {
        // When a signal has a case modifier, it should be used for the attribute suffix
        val signal   = Signal[Int]("myValue").caseModifier(CaseModifier.Snake)
        val view     = div(dataSignals(signal) := js"1")
        val rendered = view.render
        // The signal's case modifier (Snake) should be used for the suffix
        assertTrue(rendered.contains("data-signals:my-value__case.snake"))
      },
      test("attribute case modifier method overrides signal's case modifier") {
        val signal   = Signal[Int]("myValue").caseModifier(CaseModifier.Snake)
        val view     = div(dataSignals(signal).kebab := js"1")
        val rendered = view.render
        // The attribute method .kebab should override
        assertTrue(rendered.contains("data-signals:my-value__case.kebab"))
      },
    ),
  )
}
