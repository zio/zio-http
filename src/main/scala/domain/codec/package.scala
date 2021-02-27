package zio-http.domain

package object codec {
  implicit final class CodecSyntax[F[-_, +_, -_, +_], R, E, B, C](self: F[R, E, B, C]) {
    def codec[R1 <: R, E1 >: E, A, B1 <: B, C1 >: C, D](codec: Codec[R1, E1, A, B1, C1, D])(implicit
      c: CodecSupport[F],
    ): F[R1, E1, A, D] = c.codec(self, codec)
  }
}
