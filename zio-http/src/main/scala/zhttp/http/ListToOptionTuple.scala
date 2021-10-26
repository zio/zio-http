package zhttp.http

object ListToOptionTuple {

  /**
   * Utility to create Tuple from a list
   */
  def getOptionTuple(input: List[Any]): Option[Any] =
    input match {
      // scalafmt: { maxColumn = 1200 }
      case List()                                                                                                   => Some(())
      case List(a0)                                                                                                 => Some(a0)
      case List(a0, a1)                                                                                             => Some((a0, a1))
      case List(a0, a1, a2)                                                                                         => Some((a0, a1, a2))
      case List(a0, a1, a2, a3)                                                                                     => Some((a0, a1, a2, a3))
      case List(a0, a1, a2, a3, a4)                                                                                 => Some((a0, a1, a2, a3, a4))
      case List(a0, a1, a2, a3, a4, a5)                                                                             => Some((a0, a1, a2, a3, a4, a5))
      case List(a0, a1, a2, a3, a4, a5, a6)                                                                         => Some((a0, a1, a2, a3, a4, a5, a6))
      case List(a0, a1, a2, a3, a4, a5, a6, a7)                                                                     => Some((a0, a1, a2, a3, a4, a5, a6, a7))
      case List(a0, a1, a2, a3, a4, a5, a6, a7, a8)                                                                 => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8))
      case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)                                                             => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9))
      case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10)                                                        => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10))
      case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11)                                                   => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11))
      case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12)                                              => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12))
      case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13)                                         => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13))
      case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14)                                    => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14))
      case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15)                               => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15))
      case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16)                          => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16))
      case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17)                     => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17))
      case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18)                => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18))
      case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)           => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19))
      case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20)      => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20))
      case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20, a21) => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20, a21))
      case _                                                                                                        => None
    }
  // scalafmt: { maxColumn = 120 }
}
