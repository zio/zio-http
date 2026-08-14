package zio.http

import scala.quoted.*
import zio.blocks.context.IsNominalType

private[http] object MacroUtils {

  def findIsNominal(using q: Quotes)(t: q.reflect.TypeRepr): q.reflect.Term = {
    import q.reflect.*
    Implicits.search(TypeRepr.of[IsNominalType].appliedTo(List(t))) match {
      case s: ImplicitSearchSuccess => s.tree
      case _                        => report.errorAndAbort(s"Cannot find IsNominalType for ${t.show}.")
    }
  }

  /** Builds the apply call term for a function (handles FunctionXXL via IArray wrapper). */
  def buildFunctionCall(using q: Quotes)(fnTerm: q.reflect.Term, args: List[q.reflect.Term]): q.reflect.Term = {
    import q.reflect.*
    val fnTpe = fnTerm.tpe.widen
    if (fnTpe.typeSymbol.fullName == "scala.FunctionXXL") {
      val iarrayTpe  = TypeRepr.of[IArray[Any]]
      val consSym    = iarrayTpe.typeSymbol.companionModule.methodMember("apply").head
      val consSelect = Select(Ref(iarrayTpe.typeSymbol.companionModule), consSym)
      val iarrayCall = Apply(consSelect, args)
      val applyXXL   = fnTpe.typeSymbol.methodMember("apply").head
      Apply(Select(fnTerm, applyXXL), List(iarrayCall))
    } else {
      val applySym = fnTpe.typeSymbol.methodMember("apply").head
      Apply(Select(fnTerm, applySym), args)
    }
  }
}
