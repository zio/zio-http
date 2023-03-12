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

package zio.http.html

import scala.language.implicitConversions
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Checks if the value A can be represented as a valid html attribute.
 */
sealed trait IsAttributeValue[-A] {
  implicit def apply(a: A): String
}

object IsAttributeValue {
  implicit def fromString: IsAttributeValue[String] = new IsAttributeValue[String] {
    override def apply(a: String): String = a
  }

  implicit def fromInt: IsAttributeValue[Int] = new IsAttributeValue[Int] {
    override def apply(a: Int): String = a.toString
  }

  implicit def fromList: IsAttributeValue[Seq[String]] = new IsAttributeValue[Seq[String]] {
    override def apply(a: Seq[String]): String = a.mkString(" ")
  }

  implicit def fromTuple2Seq: IsAttributeValue[Seq[(String, String)]] = new IsAttributeValue[Seq[(String, String)]] {
    override def apply(a: Seq[(String, String)]): String =
      a.map { case (k, v) => s"""${k}:${v}""" }.mkString(";")
  }
}
