package zhttp

package object core extends AliasModule {
  type HBuf0[D <: Direction] = HBuf[Nat.Zero, D]
  type HBuf1[D <: Direction] = HBuf[Nat.One, D]
  type HBuf2[D <: Direction] = HBuf[Nat.Two, D]
}
