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

package zio.http.template2

import zio.test._

import zio.http.ZIOHttpSpec

object JsInterpolatorSpec extends ZIOHttpSpec {

  def spec = suite("JSInterpolatorSpec")(
    suite("js interpolator - valid JavaScript")(
      test("should accept variable declarations") {
        val result = js"var x = 5;"
        assertTrue(result.value == "var x = 5;")
      },
      test("should accept let declarations") {
        val result = js"let y = 'hello';"
        assertTrue(result.value == "let y = 'hello';")
      },
      test("should accept const declarations") {
        val result = js"const z = true;"
        assertTrue(result.value == "const z = true;")
      },
      test("should accept function declarations") {
        val result = js"function greet() { return 'Hello'; }"
        assertTrue(result.value == "function greet() { return 'Hello'; }")
      },
      test("should accept function calls") {
        val result = js"console.log('test');"
        assertTrue(result.value == "console.log('test');")
      },
      test("should accept if statements") {
        val result = js"if (x > 0) { console.log('positive'); }"
        assertTrue(result.value == "if (x > 0) { console.log('positive'); }")
      },
      test("should accept for loops") {
        val result = js"for (let i = 0; i < 10; i++) { console.log(i); }"
        assertTrue(result.value == "for (let i = 0; i < 10; i++) { console.log(i); }")
      },
      test("should accept while loops") {
        val result = js"while (condition) { doSomething(); }"
        assertTrue(result.value == "while (condition) { doSomething(); }")
      },
      test("should accept property assignments") {
        val result = js"obj.property = value;"
        assertTrue(result.value == "obj.property = value;")
      },
      test("should accept empty JavaScript") {
        val result = js""
        assertTrue(result.value == "")
      },
      test("should accept basic expressions") {
        val result = js"x + y * 2"
        assertTrue(result.value == "x + y * 2")
      },
      test("should accept boolean literals") {
        val result = js"true && false"
        assertTrue(result.value == "true && false")
      },
      test("should accept null and undefined") {
        val result = js"value === null || value === undefined"
        assertTrue(result.value == "value === null || value === undefined")
      },
    ),
    suite("js interpolator - runtime interpolation")(
      test("should handle variable interpolation") {
        val varName = "myVar"
        val value   = 42
        val result  = js"let $varName = $value;"
        assertTrue(result.value == "let myVar = 42;")
      },
      test("should handle function name interpolation") {
        val funcName = "processData"
        val param    = "input"
        val result   = js"function $funcName($param) { return $param.toUpperCase(); }"
        assertTrue(result.value == "function processData(input) { return input.toUpperCase(); }")
      },
      test("should handle property access interpolation") {
        val obj    = "window"
        val prop   = "location"
        val result = js"$obj.$prop.href"
        assertTrue(result.value == "window.location.href")
      },
      test("should handle string interpolation") {
        val message = "Hello, World!"
        val result  = js"alert('$message');"
        assertTrue(result.value == "alert('Hello, World!');")
      },
      test("should handle complex interpolation") {
        val className = "MyClass"
        val method    = "initialize"
        val params    = "config, options"
        val result    = js"class $className { $method($params) { this.setup(); } }"
        assertTrue(result.value == "class MyClass { initialize(config, options) { this.setup(); } }")
      },
    ),
    suite("js interpolator - JavaScript keywords")(
      test("should accept typeof operator") {
        val result = js"typeof variable === 'string'"
        assertTrue(result.value == "typeof variable === 'string'")
      },
      test("should accept instanceof operator") {
        val result = js"obj instanceof Array"
        assertTrue(result.value == "obj instanceof Array")
      },
      test("should accept in operator") {
        val result = js"'property' in object"
        assertTrue(result.value == "'property' in object")
      },
      test("should accept delete operator") {
        val result = js"delete obj.property"
        assertTrue(result.value == "delete obj.property")
      },
      test("should accept new operator") {
        val result = js"new Date()"
        assertTrue(result.value == "new Date()")
      },
      test("should accept this keyword") {
        val result = js"this.method()"
        assertTrue(result.value == "this.method()")
      },
      test("should accept try-catch-finally") {
        val result = js"try { risky(); } catch (e) { handle(e); } finally { cleanup(); }"
        assertTrue(result.value == "try { risky(); } catch (e) { handle(e); } finally { cleanup(); }")
      },
      test("should accept switch-case") {
        val result = js"switch (value) { case 1: break; default: return; }"
        assertTrue(result.value == "switch (value) { case 1: break; default: return; }")
      },
    ),
    suite("js interpolator - edge cases")(
      test("should handle whitespace-only content") {
        val result = js"   "
        assertTrue(result.value == "   ")
      },
      test("should handle semicolon variations") {
        val result = js"statement"
        assertTrue(result.value == "statement")
      },
      test("should handle complex expressions") {
        val result = js"(function() { return 42; })()"
        assertTrue(result.value == "(function() { return 42; })()")
      },
      test("should handle array access") {
        val result = js"arr[0] = newValue"
        assertTrue(result.value == "arr[0] = newValue")
      },
      test("should handle method chaining") {
        val result = js"obj.method1().method2().value"
        assertTrue(result.value == "obj.method1().method2().value")
      },
    ),
  )
}
