package zio.http.template

import zio.http.template.Attributes.PartialAttribute

trait HtmxAttributes {
  final def hxGet: PartialAttribute[String] = PartialAttribute("hx-get")
  final def hxPost: PartialAttribute[String] = PartialAttribute("hx-post")
  final def hxPut: PartialAttribute[String] = PartialAttribute("hx-put")
  final def hxDelete: PartialAttribute[String] = PartialAttribute("hx-delete")

  final def hxTrigger: PartialAttribute[String] = PartialAttribute("hx-trigger")

  final def hxTarget: PartialAttribute[String] = PartialAttribute("hx-target")
  final def hxBoost: PartialAttribute[String] = PartialAttribute("hx-boost")
  final def hxOn: PartialAttribute[String] = PartialAttribute("hx-on" )
  final def hxPushUrl: PartialAttribute[String] = PartialAttribute("hx-push-url")
  final def hxSelect: PartialAttribute[String] = PartialAttribute("hx-select")
  final def hxSelectOob: PartialAttribute[String] = PartialAttribute("hx-select-oob")
  final def hxSwap: PartialAttribute[String] = PartialAttribute("hx-swap")
  final def hxSwapOob: PartialAttribute[String] = PartialAttribute("hx-swap-oob")
  final def hxVals: PartialAttribute[String] = PartialAttribute("hx-vals")
  final def hxConfirm: PartialAttribute[String] = PartialAttribute("hx-confirm")
  final def hxDisable: PartialAttribute[String] = PartialAttribute("hx-disable")
  final def hxDisableElt: PartialAttribute[String] = PartialAttribute("hx-disable-elt")
  final def hxDisinherit: PartialAttribute[String] = PartialAttribute("hx-disinherit")
  final def hxEncoding: PartialAttribute[String] = PartialAttribute("hx-encoding")
  final def hxExt: PartialAttribute[String] = PartialAttribute("hx-ext")
  final def hxHeaders: PartialAttribute[String] = PartialAttribute("hx-headers")
  final def hxHistory: PartialAttribute[String] = PartialAttribute("hx-history")
  final def hxInclude: PartialAttribute[String] = PartialAttribute("hx-include")
  final def hxIndicator: PartialAttribute[String] = PartialAttribute("hx-indicator")
  final def hxParams: PartialAttribute[String] = PartialAttribute("hx-params")
  final def hxPatch: PartialAttribute[String] = PartialAttribute("hx-patch")
  final def hxPreserve: PartialAttribute[String] = PartialAttribute("hx-preserve")
  final def hxPromote: PartialAttribute[String] = PartialAttribute("hx-promote")
  final def hxReplaceUrl: PartialAttribute[String] = PartialAttribute("hx-replace-url")
  final def hxRequest: PartialAttribute[String] = PartialAttribute("hx-request")
  final def hxSync: PartialAttribute[String] = PartialAttribute("hx-sync")
  final def hxValidate: PartialAttribute[String] = PartialAttribute("hx-validate")
  final def hxVars: PartialAttribute[String] = PartialAttribute("hx-vars")

}
