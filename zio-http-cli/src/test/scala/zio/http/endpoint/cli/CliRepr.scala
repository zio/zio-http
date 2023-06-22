package zio.http.endpoint.cli

import zio.cli._

/**
 * CliRepr(a, repr) represents a value "a" constructed using the cli package and the correct representation "repr".
 */

final case class CliRepr[+A, R](value: A, repr: R) {

    def map[B](f: (A => B)) = CliRepr[B, R](f(value), repr)

    def map2[B](f: (R => B)) = CliRepr[A, B](value, f(repr))
        
}

object CliRepr {
    
    type CliReprOf[A] = CliRepr[A, CliEndpoint]

    type HelpRepr[A] = CliRepr[A, HelpDoc]

}

