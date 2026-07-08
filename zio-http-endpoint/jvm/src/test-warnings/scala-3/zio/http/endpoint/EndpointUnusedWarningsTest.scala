/*
 * Copyright 2026 the ZIO HTTP contributors.
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
package zio.http.endpoint

/**
 * Scratch file to test `.unused` marker warning scenarios.
 * Compile this with `./mill` to see the 4-combination warnings.
 *
 * Run: ./mill 'endpoint.jvm[3.8.3].compile' 2>&1 | grep -A 1 "warning"
 *
 * Expected warnings:
 *   1. Case 1 (unconsumed, not marked): "Variable x:Int was defined in the endpoint input but is never used"
 *   2. Case 4 (consumed, marked): "Variable y:Int was marked .unused but is referenced by the handler"
 *   
 * Not expected (no warning):
 *   - Case 2 (consumed, not marked)
 *   - Case 3 (unconsumed, marked)
 */

object EndpointUnusedWarningsTest {
  // This file is compile-time only; the warnings are emitted during compilation.
  // To see them, compile and grep for "warning" in the output.
}
