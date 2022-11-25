package zio.http.model.headers.values

import zio.Chunk
import zio.http.model.Method

sealed trait AccessControlAllowMethods

object AccessControlAllowMethods {

  final case class AllowMethods(methods: Chunk[Method]) extends AccessControlAllowMethods

  case object AllowAllMethods extends AccessControlAllowMethods

  case object NoMethodsAllowed extends AccessControlAllowMethods

  def fromAccessControlAllowMethods(accessControlAllowMethods: AccessControlAllowMethods): String =
    accessControlAllowMethods match {
      case AllowMethods(methods) => methods.map(_.toString()).mkString(", ")
      case AllowAllMethods       => "*"
      case NoMethodsAllowed      => ""
    }

  def toAccessControlAllowMethods(value: String): AccessControlAllowMethods = {
    value match {
      case ""          => NoMethodsAllowed
      case "*"         => AllowAllMethods
      case methodNames =>
        AllowMethods(
          Chunk.fromArray(
            methodNames
              .split(",")
              .map(_.trim)
              .map(Method.fromString),
          ),
        )
    }
  }
}
