package zio.http

/**
 * A `ZClientAspect` is capable on modifying some aspect of the execution of a
 * client, such as metrics, tracing, encoding, decoding, or logging.
 */
trait ZClientAspect[+LowerEnv, -UpperEnv, +LowerIn, -UpperIn, +LowerErr, -UpperErr, +LowerOut, -UpperOut] { self =>

  /**
   * Applies this metric to modify the execution of the specified client.
   */
  def apply[
    Env >: LowerEnv <: UpperEnv,
    In >: LowerIn <: UpperIn,
    Err >: LowerErr <: UpperErr,
    Out >: LowerOut <: UpperOut,
  ](client: ZClient[Env, In, Err, Out]): ZClient[Env, In, Err, Out]

  /**
   * Composes this client aspect with the specified client aspect to return a
   * new client aspect that applies the modifications of this client aspect and
   * then the modifications of that client aspect.
   */
  final def @@[
    LowerEnv1 >: LowerEnv,
    UpperEnv1 <: UpperEnv,
    LowerIn1 >: LowerIn,
    UpperIn1 <: UpperIn,
    LowerErr1 >: LowerErr,
    UpperErr1 <: UpperErr,
    LowerOut1 >: LowerOut,
    UpperOut1 <: UpperOut,
  ](
    that: ZClientAspect[LowerEnv1, UpperEnv1, LowerIn1, UpperIn1, LowerErr1, UpperErr1, LowerOut1, UpperOut1],
  ): ZClientAspect[LowerEnv1, UpperEnv1, LowerIn1, UpperIn1, LowerErr1, UpperErr1, LowerOut1, UpperOut1] =
    self.andThen(that)

  /**
   * Composes this client aspect with the specified client aspect to return a
   * new client aspect that applies the modifications of this client aspect and
   * then the modifications of that client aspect.
   */
  final def >>>[
    LowerEnv1 >: LowerEnv,
    UpperEnv1 <: UpperEnv,
    LowerIn1 >: LowerIn,
    UpperIn1 <: UpperIn,
    LowerErr1 >: LowerErr,
    UpperErr1 <: UpperErr,
    LowerOut1 >: LowerOut,
    UpperOut1 <: UpperOut,
  ](
    that: ZClientAspect[LowerEnv1, UpperEnv1, LowerIn1, UpperIn1, LowerErr1, UpperErr1, LowerOut1, UpperOut1],
  ): ZClientAspect[LowerEnv1, UpperEnv1, LowerIn1, UpperIn1, LowerErr1, UpperErr1, LowerOut1, UpperOut1] =
    self.andThen(that)

  /**
   * Composes this client aspect with the specified client aspect to return a
   * new client aspect that applies the modifications of this client aspect and
   * then the modifications of that client aspect.
   */
  final def andThen[
    LowerEnv1 >: LowerEnv,
    UpperEnv1 <: UpperEnv,
    LowerIn1 >: LowerIn,
    UpperIn1 <: UpperIn,
    LowerErr1 >: LowerErr,
    UpperErr1 <: UpperErr,
    LowerOut1 >: LowerOut,
    UpperOut1 <: UpperOut,
  ](
    that: ZClientAspect[LowerEnv1, UpperEnv1, LowerIn1, UpperIn1, LowerErr1, UpperErr1, LowerOut1, UpperOut1],
  ): ZClientAspect[LowerEnv1, UpperEnv1, LowerIn1, UpperIn1, LowerErr1, UpperErr1, LowerOut1, UpperOut1] =
    new ZClientAspect[LowerEnv1, UpperEnv1, LowerIn1, UpperIn1, LowerErr1, UpperErr1, LowerOut1, UpperOut1] {
      override def apply[
        Env >: LowerEnv1 <: UpperEnv1,
        In >: LowerIn1 <: UpperIn1,
        Err >: LowerErr1 <: UpperErr1,
        Out >: LowerOut1 <: UpperOut1,
      ](client: ZClient[Env, In, Err, Out]): ZClient[Env, In, Err, Out] =
        that(self(client))
    }
}
