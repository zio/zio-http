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

package zio.http.template

import scala.language.implicitConversions

/**
 * A type class that converts a value of type `A` into the string of an HTML
 * attribute value. `PartialAttribute` uses it in `:=` and `apply`. It does not
 * check the value; the attribute value is escaped when the element is rendered.
 * The trait is sealed, so its instances are the three in the companion object:
 * `String`, `Seq[String]` and `Seq[(String, String)]`.
 */
sealed trait IsAttributeValue[-A] {

  /** Returns `a` as an attribute value string. */
  def apply(a: A): String
}

/** The [[IsAttributeValue]] instances. */
object IsAttributeValue {

  /** Uses the string as it is. */
  implicit val instanceString: IsAttributeValue[String] = new IsAttributeValue[String] {
    override def apply(a: String): String = a
  }

  /**
   * Joins the strings with a space. The helpers of `Attributes` are all
   * `PartialAttribute[String]`, so this instance applies to a
   * `PartialAttribute[Seq[String]]` built directly, such as
   * `PartialAttribute[Seq[String]]("class")`: `Seq("btn", "primary")` gives
   * `btn primary`.
   */
  implicit val instanceList: IsAttributeValue[Seq[String]] = new IsAttributeValue[Seq[String]] {
    override def apply(a: Seq[String]): String = a.mkString(" ")
  }

  /**
   * Writes each pair as `key:value` and joins the pairs with `;`, as in a
   * `style` value: `Seq("color" -> "red", "margin" -> "0")` gives
   * `color:red;margin:0`. Keys and values are not checked.
   */
  implicit val instanceTuple2Seq: IsAttributeValue[Seq[(String, String)]] =
    new IsAttributeValue[Seq[(String, String)]] {
      override def apply(a: Seq[(String, String)]): String =
        a.map { case (k, v) => s"""${k}:${v}""" }.mkString(";")
    }
}
