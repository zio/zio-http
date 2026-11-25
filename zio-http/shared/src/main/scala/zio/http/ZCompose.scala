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

package zio.http

/**
 * Evidence for composing two things that each have a lower bound, an upper
 * bound and an output type constructor. The members `Lower`, `Upper` and `Out`
 * describe the composition.
 *
 * The instances in the companion object cover these cases, where `Identity[A]`
 * is `A`:
 *
 *   - both outputs are constant types: `Lower` and `Upper` are those of the
 *     left side, and `Out` is the output of the right side. The left output
 *     must lie between the lower and upper bound of the right side;
 *   - both outputs are `Identity`: `Lower` is the wider of the two lower
 *     bounds (the right one when the left lower bound is a subtype of it,
 *     otherwise the left one), `Upper` is the intersection of the two upper
 *     bounds, and `Out` is `Identity`;
 *   - only the left output is `Identity`: `Lower` and `Upper` as for two
 *     `Identity` outputs, and `Out` is the right output;
 *   - only the right output is `Identity`: `Lower` and `Upper` are those of the
 *     left side, and `Out` is the left output.
 */
trait ZCompose[+LeftLower, -LeftUpper, LeftOut[In], +RightLower, -RightUpper, RightOut[In]] {

  /** The lower bound of the composition. */
  type Lower

  /** The upper bound of the composition. */
  type Upper

  /** The output type constructor of the composition. */
  type Out[In]
}

/** The `ZCompose` instances. */
object ZCompose extends ComposeLowPriorityImplicits {

  /**
   * A `ZCompose` whose `Lower`, `Upper` and `Out` members are the given types.
   */
  type WithOut[LeftLower, LeftUpper, LeftOut[In], RightLower, RightUpper, RightOut[In], Lower0, Upper0, Out0[In]] =
    ZCompose[LeftLower, LeftUpper, LeftOut, RightLower, RightUpper, RightOut] {
      type Lower   = Lower0
      type Upper   = Upper0
      type Out[In] = Out0[In]
    }

  /** The instance for two constant output types. */
  implicit def compose[
    LeftLower,
    LeftUpper,
    LeftOut >: RightLower <: RightUpper,
    RightLower,
    RightUpper,
    RightOut,
  ]: ZCompose.WithOut[
    LeftLower,
    LeftUpper,
    ({ type Out[In] = LeftOut })#Out,
    RightLower,
    RightUpper,
    ({ type Out[In] = RightOut })#Out,
    LeftLower,
    LeftUpper,
    ({ type Out[In] = RightOut })#Out,
  ] =
    new ZCompose[
      LeftLower,
      LeftUpper,
      ({ type Out[In] = LeftOut })#Out,
      RightLower,
      RightUpper,
      ({ type Out[In] = RightOut })#Out,
    ] {
      type Lower   = LeftLower
      type Upper   = LeftUpper
      type Out[In] = RightOut
    }

  /** The instance for two `Identity` outputs. */
  implicit def identity[LeftLower <: RightLower, LeftUpper, RightLower, RightUpper]: ZCompose.WithOut[
    LeftLower,
    LeftUpper,
    Identity,
    RightLower,
    RightUpper,
    Identity,
    RightLower,
    LeftUpper with RightUpper,
    Identity,
  ] =
    new ZCompose[
      LeftLower,
      LeftUpper,
      Identity,
      RightLower,
      RightUpper,
      Identity,
    ] {
      type Lower   = RightLower
      type Upper   = LeftUpper with RightUpper
      type Out[In] = In
    }

  /** The instance for an `Identity` left output and a constant right output. */
  implicit def leftIdentity[LeftLower <: RightLower, LeftUpper, RightLower, RightUpper, RightOut]: ZCompose.WithOut[
    LeftLower,
    LeftUpper,
    Identity,
    RightLower,
    RightUpper,
    ({ type Out[In] = RightOut })#Out,
    RightLower,
    LeftUpper with RightUpper,
    ({ type Out[In] = RightOut })#Out,
  ] =
    new ZCompose[
      LeftLower,
      LeftUpper,
      Identity,
      RightLower,
      RightUpper,
      ({ type Out[In] = RightOut })#Out,
    ] {
      type Lower   = RightLower
      type Upper   = LeftUpper with RightUpper
      type Out[In] = RightOut
    }

  /** The instance for a constant left output and an `Identity` right output. */
  implicit def rightIdentity[LeftLower, LeftUpper, LeftOut >: RightLower <: RightUpper, RightLower, RightUpper]
    : ZCompose.WithOut[
      LeftLower,
      LeftUpper,
      ({ type Out[In] = LeftOut })#Out,
      RightLower,
      RightUpper,
      Identity,
      LeftLower,
      LeftUpper,
      ({ type Out[In] = LeftOut })#Out,
    ] =
    new ZCompose[
      LeftLower,
      LeftUpper,
      ({ type Out[In] = LeftOut })#Out,
      RightLower,
      RightUpper,
      Identity,
    ] {
      type Lower   = LeftLower
      type Upper   = LeftUpper
      type Out[In] = LeftOut
    }
}

/** The lower priority `ZCompose` instances, and the `Identity` type. */
trait ComposeLowPriorityImplicits {

  /** The identity type constructor: `Identity[A]` is `A`. */
  type Identity[A] = A

  /**
   * The instance for two `Identity` outputs whose right lower bound is a
   * subtype of the left lower bound.
   */
  implicit def identityLowPriority[LeftLowerElem, LeftUpperElem, RightLowerElem <: LeftLowerElem, RightUpperElem]
    : ZCompose.WithOut[
      LeftLowerElem,
      LeftUpperElem,
      Identity,
      RightLowerElem,
      RightUpperElem,
      Identity,
      LeftLowerElem,
      LeftUpperElem with RightUpperElem,
      Identity,
    ] =
    new ZCompose[
      LeftLowerElem,
      LeftUpperElem,
      Identity,
      RightLowerElem,
      RightUpperElem,
      Identity,
    ] {
      type Lower   = LeftLowerElem
      type Upper   = LeftUpperElem with RightUpperElem
      type Out[In] = In
    }

  /**
   * The instance for an `Identity` left output and a constant right output,
   * when the right lower bound is a subtype of the left lower bound.
   */
  implicit def leftIdentityLowPriority[LeftLower, LeftUpper, RightLower <: LeftLower, RightUpper, RightOut]
    : ZCompose.WithOut[
      LeftLower,
      LeftUpper,
      Identity,
      RightLower,
      RightUpper,
      ({ type Out[In] = RightOut })#Out,
      LeftLower,
      LeftUpper with RightUpper,
      ({ type Out[In] = RightOut })#Out,
    ] =
    new ZCompose[
      LeftLower,
      LeftUpper,
      Identity,
      RightLower,
      RightUpper,
      ({ type Out[In] = RightOut })#Out,
    ] {
      type Lower   = LeftLower
      type Upper   = LeftUpper with RightUpper
      type Out[In] = RightOut
    }
}
