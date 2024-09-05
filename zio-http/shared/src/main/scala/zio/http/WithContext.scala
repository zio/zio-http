/*
 * Copyright 2023 the ZIO HTTP contributors.
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

package zio.http

import scala.annotation.implicitNotFound

import zio._

@implicitNotFound("""
The type ${C} does not appear to be one that can be used when requesting a context. The following types may be used when requesting a context:

 - Simple Functions:  Ctx => Out
 - ZIO Functions:     Ctx => ZIO[Env, Err, Out]

The types to the function must be specified explicitly: Scala cannot infer them due to the smart constructor.
""")
trait WithContext[C] {
  type Env
  type Err
  type Out

  def toZIO(h: => C): ZIO[Env, Err, Out]
}

object WithContext extends WithContextConstructorLowPriorityImplicits1

private[http] trait WithContextConstructorLowPriorityImplicits1 extends WithContextConstructorLowPriorityImplicits2 {

  implicit def function2ZIOWithContextConstructor[Env0, Err0, Ctx1, Ctx2, Out0](implicit
    tag1: Tag[Ctx1],
    tag2: Tag[Ctx2],
  ): WithContext.Typed[(Ctx1, Ctx2) => ZIO[Env0, Err0, Out0], Env0 with Ctx1 with Ctx2, Err0, Out0] =
    new WithContext[(Ctx1, Ctx2) => ZIO[Env0, Err0, Out0]] {
      type Env = Env0 with Ctx1 with Ctx2
      type Err = Err0
      type Out = Out0
      type Z   = (Ctx1, Ctx2) => ZIO[Env0, Err0, Out0]

      def toZIO(z: => Z): ZIO[Env, Err, Out] = {
        implicit val tag1_ = tag1.tag
        implicit val tag2_ = tag2.tag
        implicit val usf   = Unsafe.unsafe

        ZIO.suspendSucceed {
          FiberRef.currentEnvironment.get.flatMap(environment =>
            z(environment.unsafe.get[Ctx1](tag1_), environment.unsafe.get[Ctx2](tag2_)),
          )
        }
      }

    }

  implicit def function3ZIOWithContextConstructor[Env0, Err0, Ctx1, Ctx2, Ctx3, Out0](implicit
    tag1: Tag[Ctx1],
    tag2: Tag[Ctx2],
    tag3: Tag[Ctx3],
  ): WithContext.Typed[(Ctx1, Ctx2, Ctx3) => ZIO[Env0, Err0, Out0], Env0 with Ctx1 with Ctx2 with Ctx3, Err0, Out0] =
    new WithContext[(Ctx1, Ctx2, Ctx3) => ZIO[Env0, Err0, Out0]] {
      type Env = Env0 with Ctx1 with Ctx2 with Ctx3
      type Err = Err0
      type Out = Out0
      type Z   = (Ctx1, Ctx2, Ctx3) => ZIO[Env0, Err0, Out0]

      def toZIO(z: => Z): ZIO[Env, Err, Out] =
        ZIO.suspendSucceed {
          implicit val tag1_ = tag1.tag
          implicit val tag2_ = tag2.tag
          implicit val tag3_ = tag3.tag
          implicit val usf   = Unsafe.unsafe
          FiberRef.currentEnvironment.get.flatMap(environment =>
            z(
              environment.unsafe.get[Ctx1](tag1_),
              environment.unsafe.get[Ctx2](tag2_),
              environment.unsafe.get[Ctx3](tag3_),
            ),
          )
        }

    }

  implicit def function4ZIOWithContextConstructor[Env0, Err0, Ctx1, Ctx2, Ctx3, Ctx4, Out0](implicit
    tag1: Tag[Ctx1],
    tag2: Tag[Ctx2],
    tag3: Tag[Ctx3],
    tag4: Tag[Ctx4],
  ): WithContext.Typed[(Ctx1, Ctx2, Ctx3, Ctx4) => ZIO[
    Env0,
    Err0,
    Out0,
  ], Env0 with Ctx1 with Ctx2 with Ctx3 with Ctx4, Err0, Out0] =
    new WithContext[(Ctx1, Ctx2, Ctx3, Ctx4) => ZIO[Env0, Err0, Out0]] {
      type Env = Env0 with Ctx1 with Ctx2 with Ctx3 with Ctx4
      type Err = Err0
      type Out = Out0
      type Z   = (Ctx1, Ctx2, Ctx3, Ctx4) => ZIO[Env0, Err0, Out0]

      def toZIO(z: => Z): ZIO[Env, Err, Out] =
        ZIO.suspendSucceed {
          implicit val tag1_ = tag1.tag
          implicit val tag2_ = tag2.tag
          implicit val tag3_ = tag3.tag
          implicit val tag4_ = tag4.tag
          implicit val usf   = Unsafe.unsafe
          FiberRef.currentEnvironment.get.flatMap(environment =>
            z(
              environment.unsafe.get[Ctx1](tag1_),
              environment.unsafe.get[Ctx2](tag2_),
              environment.unsafe.get[Ctx3](tag3_),
              environment.unsafe.get[Ctx4](tag4_),
            ),
          )
        }

    }

  implicit def function5ZIOWithContextConstructor[Env0, Err0, Ctx1, Ctx2, Ctx3, Ctx4, Ctx5, Out0](implicit
    tag1: Tag[Ctx1],
    tag2: Tag[Ctx2],
    tag3: Tag[Ctx3],
    tag4: Tag[Ctx4],
    tag5: Tag[Ctx5],
  ): WithContext.Typed[(Ctx1, Ctx2, Ctx3, Ctx4, Ctx5) => ZIO[
    Env0,
    Err0,
    Out0,
  ], Env0 with Ctx1 with Ctx2 with Ctx3 with Ctx4 with Ctx5, Err0, Out0] =
    new WithContext[(Ctx1, Ctx2, Ctx3, Ctx4, Ctx5) => ZIO[Env0, Err0, Out0]] {
      type Env = Env0 with Ctx1 with Ctx2 with Ctx3 with Ctx4 with Ctx5
      type Err = Err0
      type Out = Out0
      type Z   = (Ctx1, Ctx2, Ctx3, Ctx4, Ctx5) => ZIO[Env0, Err0, Out0]

      def toZIO(z: => Z): ZIO[Env, Err, Out] =
        ZIO.suspendSucceed {
          implicit val tag1_ = tag1.tag
          implicit val tag2_ = tag2.tag
          implicit val tag3_ = tag3.tag
          implicit val tag4_ = tag4.tag
          implicit val tag5_ = tag5.tag
          implicit val usf   = Unsafe.unsafe
          FiberRef.currentEnvironment.get.flatMap(environment =>
            z(
              environment.unsafe.get[Ctx1](tag1_),
              environment.unsafe.get[Ctx2](tag2_),
              environment.unsafe.get[Ctx3](tag3_),
              environment.unsafe.get[Ctx4](tag4_),
              environment.unsafe.get[Ctx5](tag5_),
            ),
          )
        }
    }

}

private[http] trait WithContextConstructorLowPriorityImplicits2 extends WithContextConstructorLowPriorityImplicits3 {
  implicit def functionZIOWithContextConstructor[Env0, Err0, Ctx0, Out0](implicit
    tag: Tag[Ctx0],
  ): WithContext.Typed[Ctx0 => ZIO[Env0, Err0, Out0], Env0 with Ctx0, Err0, Out0] =
    new WithContext[Ctx0 => ZIO[Env0, Err0, Out0]] {
      type Env = Env0 with Ctx0
      type Err = Err0
      type Out = Out0
      type Z   = Ctx0 => ZIO[Env0, Err0, Out0]

      def toZIO(z: => Z): ZIO[Env, Err, Out] = {
        implicit val tag_ = tag.tag
        implicit val usf  = Unsafe.unsafe

        ZIO.suspendSucceed {
          FiberRef.currentEnvironment.get.flatMap(environment => z(environment.unsafe.get[Ctx0](tag_)))
        }
      }
    }
}

private[http] trait WithContextConstructorLowPriorityImplicits3 extends WithContextConstructorLowPriorityImplicits4 {
  implicit def function2ValueWithContextConstructor[Err0, Ctx1, Ctx2, Out0](implicit
    tag1: Tag[Ctx1],
    tag2: Tag[Ctx2],
  ): WithContext.Typed[(Ctx1, Ctx2) => Out0, Ctx1 with Ctx2, Err0, Out0] =
    new WithContext[(Ctx1, Ctx2) => Out0] {
      type Env = Ctx1 with Ctx2
      type Err = Err0
      type Out = Out0
      type Z   = (Ctx1, Ctx2) => Out0

      def toZIO(z: => Z): ZIO[Env, Err, Out] =
        ZIO.suspendSucceed {
          implicit val tag1_ = tag1.tag
          implicit val tag2_ = tag2.tag
          implicit val usf   = Unsafe.unsafe
          FiberRef.currentEnvironment.get.map(environment =>
            z(environment.unsafe.get[Ctx1](tag1_), environment.unsafe.get[Ctx2](tag2_)),
          )
        }

    }

  implicit def function3ValueWithContextConstructor[Err0, Ctx1, Ctx2, Ctx3, Out0](implicit
    tag1: Tag[Ctx1],
    tag2: Tag[Ctx2],
    tag3: Tag[Ctx3],
  ): WithContext.Typed[(Ctx1, Ctx2, Ctx3) => Out0, Ctx1 with Ctx2 with Ctx3, Err0, Out0] =
    new WithContext[(Ctx1, Ctx2, Ctx3) => Out0] {
      type Env = Ctx1 with Ctx2 with Ctx3
      type Err = Err0
      type Out = Out0
      type Z   = (Ctx1, Ctx2, Ctx3) => Out0

      def toZIO(z: => Z): ZIO[Env, Err, Out] =
        ZIO.suspendSucceed {
          implicit val tag1_ = tag1.tag
          implicit val tag2_ = tag2.tag
          implicit val tag3_ = tag3.tag
          implicit val usf   = Unsafe.unsafe
          FiberRef.currentEnvironment.get.map(environment =>
            z(
              environment.unsafe.get[Ctx1](tag1_),
              environment.unsafe.get[Ctx2](tag2_),
              environment.unsafe.get[Ctx3](tag3_),
            ),
          )
        }
    }

  implicit def function4ValueWithContextConstructor[Err0, Ctx1, Ctx2, Ctx3, Ctx4, Out0](implicit
    tag1: Tag[Ctx1],
    tag2: Tag[Ctx2],
    tag3: Tag[Ctx3],
    tag4: Tag[Ctx4],
  ): WithContext.Typed[(Ctx1, Ctx2, Ctx3, Ctx4) => Out0, Ctx1 with Ctx2 with Ctx3 with Ctx4, Err0, Out0] =
    new WithContext[(Ctx1, Ctx2, Ctx3, Ctx4) => Out0] {
      type Env = Ctx1 with Ctx2 with Ctx3 with Ctx4
      type Err = Err0
      type Out = Out0
      type Z   = (Ctx1, Ctx2, Ctx3, Ctx4) => Out0

      def toZIO(z: => Z): ZIO[Env, Err, Out] =
        ZIO.suspendSucceed {
          implicit val tag1_ = tag1.tag
          implicit val tag2_ = tag2.tag
          implicit val tag3_ = tag3.tag
          implicit val tag4_ = tag4.tag
          implicit val usf   = Unsafe.unsafe
          FiberRef.currentEnvironment.get.map(environment =>
            z(
              environment.unsafe.get[Ctx1](tag1_),
              environment.unsafe.get[Ctx2](tag2_),
              environment.unsafe.get[Ctx3](tag3_),
              environment.unsafe.get[Ctx4](tag4_),
            ),
          )
        }
    }

  implicit def function5ValueWithContextConstructor[Err0, Ctx1, Ctx2, Ctx3, Ctx4, Ctx5, Out0](implicit
    tag1: Tag[Ctx1],
    tag2: Tag[Ctx2],
    tag3: Tag[Ctx3],
    tag4: Tag[Ctx4],
    tag5: Tag[Ctx5],
  ): WithContext.Typed[
    (Ctx1, Ctx2, Ctx3, Ctx4, Ctx5) => Out0,
    Ctx1 with Ctx2 with Ctx3 with Ctx4 with Ctx5,
    Err0,
    Out0,
  ] =
    new WithContext[(Ctx1, Ctx2, Ctx3, Ctx4, Ctx5) => Out0] {
      type Env = Ctx1 with Ctx2 with Ctx3 with Ctx4 with Ctx5
      type Err = Err0
      type Out = Out0
      type Z   = (Ctx1, Ctx2, Ctx3, Ctx4, Ctx5) => Out0

      def toZIO(z: => Z): ZIO[Env, Err, Out] =
        ZIO.suspendSucceed {
          implicit val tag1_ = tag1.tag
          implicit val tag2_ = tag2.tag
          implicit val tag3_ = tag3.tag
          implicit val tag4_ = tag4.tag
          implicit val tag5_ = tag5.tag
          implicit val usf   = Unsafe.unsafe
          FiberRef.currentEnvironment.get.map(environment =>
            z(
              environment.unsafe.get[Ctx1](tag1_),
              environment.unsafe.get[Ctx2](tag2_),
              environment.unsafe.get[Ctx3](tag3_),
              environment.unsafe.get[Ctx4](tag4_),
              environment.unsafe.get[Ctx5](tag5_),
            ),
          )
        }
    }
}

private[http] trait WithContextConstructorLowPriorityImplicits4 {
  type Typed[C, Env0, Err0, Out0] = WithContext[C] {
    type Env = Env0; type Err = Err0; type Out = Out0
  }

  implicit def functionWithContextConstructor[Ctx: Tag, Out0]: WithContext.Typed[Ctx => Out0, Ctx, Nothing, Out0] =
    new WithContext[Ctx => Out0] {
      type Env = Ctx
      type Err = Nothing
      type Out = Out0
      type Z   = Ctx => Out0

      def toZIO(z: => Z): ZIO[Env, Err, Out] =
        ZIO.serviceWith[Ctx] { in1 => z(in1) }
    }
}
