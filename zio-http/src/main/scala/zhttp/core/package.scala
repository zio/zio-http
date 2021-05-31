package zhttp

package object core extends AliasModule {
  type HBuf0[D] = HBuf[Nat.Zero, D]
  type HBuf1[D] = HBuf[Nat.One, D]
  type HBuf2[D] = HBuf[Nat.Two, D]
}
