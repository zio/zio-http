package zio.http.htmx

import zio.http.htmx.Attributes.PartialAttribute
import zio.http.template.{Dom, Html, IsAttributeValue}

trait Attributes {

  final def hxBoostAttr: PartialAttribute[String] = PartialAttribute("hx-boost")

  final def hxGetAttr: PartialAttribute[String] = PartialAttribute("hx-get")

  final def hxPostAttr: PartialAttribute[String] = PartialAttribute("hx-post")

  final def hxOnAttr: PartialAttribute[String] = PartialAttribute("hx-on*")

  final def hxPushUrlAttr: PartialAttribute[String] = PartialAttribute("hx-push-url")

  final def hxSelectAttr: PartialAttribute[String] = PartialAttribute("hx-select")

  final def hxSelectOobAttr: PartialAttribute[String] = PartialAttribute("hx-select-oob")

  final def hxSwapAttr: PartialAttribute[String] = PartialAttribute("hx-swap")

  final def hxSwapOobAttr: PartialAttribute[String] = PartialAttribute("hx-swap-oob")

  final def hxTargetAttr: PartialAttribute[String] = PartialAttribute("hx-target")

  final def hxTriggerAttr: PartialAttribute[String] = PartialAttribute("hx-trigger")

  final def hxValsAttr: PartialAttribute[String] = PartialAttribute("hx-vals")

  final def hxConfirmAttr: PartialAttribute[String] = PartialAttribute("hx-confirm")

  final def hxDeleteAttr: PartialAttribute[String] = PartialAttribute("hx-delete")

  final def hxDisableAttr: PartialAttribute[String] = PartialAttribute("hx-disable")

  final def hxDisabledEltAttr: PartialAttribute[String] = PartialAttribute("hx-disabled-elt")

  final def hxDisinheritAttr: PartialAttribute[String] = PartialAttribute("hx-disinherit")

  final def hxEncodingAttr: PartialAttribute[String] = PartialAttribute("hx-encoding")

  final def hxExtAttr: PartialAttribute[String] = PartialAttribute("hx-ext")

  final def hxHeadersAttr: PartialAttribute[String] = PartialAttribute("hx-headers")

  final def hxHistoryAttr: PartialAttribute[String] = PartialAttribute("hx-history")

  final def hxHistoryEltAttr: PartialAttribute[String] = PartialAttribute("hx-history-elt")

  final def hxIncludeAttr: PartialAttribute[String] = PartialAttribute("hx-include")

  final def hxIndicatorAttr: PartialAttribute[String] = PartialAttribute("hx-indicator")

  final def hxParamsAttr: PartialAttribute[String] = PartialAttribute("hx-params")

  final def hxPatchAttr: PartialAttribute[String] = PartialAttribute("hx-patch")

  final def hxPreserveAttr: PartialAttribute[String] = PartialAttribute("hx-preserve")

  final def hxPromptAttr: PartialAttribute[String] = PartialAttribute("hx-prompt")

  final def hxPutAttr: PartialAttribute[String] = PartialAttribute("hx-put")

  final def hxReplaceUrlAttr: PartialAttribute[String] = PartialAttribute("hx-replace-url")

  final def hxRequestAttr: PartialAttribute[String] = PartialAttribute("hx-request")

  final def hxSseAttr: PartialAttribute[String] = PartialAttribute("hx-sse")

  final def hxSyncAttr: PartialAttribute[String] = PartialAttribute("hx-sync")

  final def hxValidateAttr: PartialAttribute[String] = PartialAttribute("hx-validate")

  final def hxVarsAttr: PartialAttribute[String] = PartialAttribute("hx-vars")

  final def hxWsAttr: PartialAttribute[String] = PartialAttribute("hx-ws")

  final def hxOnAbortAttr: PartialAttribute[String] = PartialAttribute("hx-on:abort")

  final def hxOnAfterPrintAttr: PartialAttribute[String] = PartialAttribute("hx-on:afterprint")

  final def hxOnBeforePrintAttr: PartialAttribute[String] = PartialAttribute("hx-on:beforeprint")

  final def hxOnBeforeUnloadAttr: PartialAttribute[String] = PartialAttribute("hx-on:beforeunload")

  final def hxOnBlurAttr: PartialAttribute[String] = PartialAttribute("hx-on:blur")

  final def hxOnCanPlayAttr: PartialAttribute[String] = PartialAttribute("hx-on:canplay")

  final def hxOnCanPlayThroughAttr: PartialAttribute[String] = PartialAttribute("hx-on:canplaythrough")

  final def hxOnChangeAttr: PartialAttribute[String] = PartialAttribute("hx-on:change")

  final def hxOnClickAttr: PartialAttribute[String] = PartialAttribute("hx-on:click")

  final def hxOnContextMenuAttr: PartialAttribute[String] = PartialAttribute("hx-on:contextmenu")

  final def hxOnCopyAttr: PartialAttribute[String] = PartialAttribute("hx-on:copy")

  final def hxOnCueChangeAttr: PartialAttribute[String] = PartialAttribute("hx-on:cuechange")

  final def hxOnCutAttr: PartialAttribute[String] = PartialAttribute("hx-on:cut")

  final def hxOnDblClickAttr: PartialAttribute[String] = PartialAttribute("hx-on:dblclick")

  final def hxOnDragAttr: PartialAttribute[String] = PartialAttribute("hx-on:drag")

  final def hxOnDragEndAttr: PartialAttribute[String] = PartialAttribute("hx-on:dragend")

  final def hxOnDragEnterAttr: PartialAttribute[String] = PartialAttribute("hx-on:dragenter")

  final def hxOnDragLeaveAttr: PartialAttribute[String] = PartialAttribute("hx-on:dragleave")

  final def hxOnDragOverAttr: PartialAttribute[String] = PartialAttribute("hx-on:dragover")

  final def hxOnDragStartAttr: PartialAttribute[String] = PartialAttribute("hx-on:dragstart")

  final def hxOnDropAttr: PartialAttribute[String] = PartialAttribute("hx-on:drop")

  final def hxOnDurationChangeAttr: PartialAttribute[String] = PartialAttribute("hx-on:durationchange")

  final def hxOnEmptiedAttr: PartialAttribute[String] = PartialAttribute("hx-on:emptied")

  final def hxOnEndedAttr: PartialAttribute[String] = PartialAttribute("hx-on:ended")

  final def hxOnErrorAttr: PartialAttribute[String] = PartialAttribute("hx-on:error")

  final def hxOnFocusAttr: PartialAttribute[String] = PartialAttribute("hx-on:focus")

  final def hxOnHashChangeAttr: PartialAttribute[String] = PartialAttribute("hx-on:hashchange")

  final def hxOnInputAttr: PartialAttribute[String] = PartialAttribute("hx-on:input")

  final def hxOnInvalidAttr: PartialAttribute[String] = PartialAttribute("hx-on:invalid")

  final def hxOnKeyDownAttr: PartialAttribute[String] = PartialAttribute("hx-on:keydown")

  final def hxOnKeyPressAttr: PartialAttribute[String] = PartialAttribute("hx-on:keypress")

  final def hxOnKeyUpAttr: PartialAttribute[String] = PartialAttribute("hx-on:keyup")

  final def hxOnLoadAttr: PartialAttribute[String] = PartialAttribute("hx-on:load")

  final def hxOnLoadStartAttr: PartialAttribute[String] = PartialAttribute("hx-on:loadstart")

  final def hxOnLoadedDataAttr: PartialAttribute[String] = PartialAttribute("hx-on:loadeddata")

  final def hxOnLoadedMetadataAttr: PartialAttribute[String] = PartialAttribute("hx-on:loadedmetadata")

  final def hxOnMouseDownAttr: PartialAttribute[String] = PartialAttribute("hx-on:mousedown")

  final def hxOnMouseMoveAttr: PartialAttribute[String] = PartialAttribute("hx-on:mousemove")

  final def hxOnMouseOutAttr: PartialAttribute[String] = PartialAttribute("hx-on:mouseout")

  final def hxOnMouseOverAttr: PartialAttribute[String] = PartialAttribute("hx-on:mouseover")

  final def hxOnMouseUpAttr: PartialAttribute[String] = PartialAttribute("hx-on:mouseup")

  final def hxOnMouseWheelAttr: PartialAttribute[String] = PartialAttribute("hx-on:mousewheel")

  final def hxOnOfflineAttr: PartialAttribute[String] = PartialAttribute("hx-on:offline")

  final def hxOnOnlineAttr: PartialAttribute[String] = PartialAttribute("hx-on:online")

  final def hxOnPageHideAttr: PartialAttribute[String] = PartialAttribute("hx-on:pagehide")

  final def hxOnPageShowAttr: PartialAttribute[String] = PartialAttribute("hx-on:pageshow")

  final def hxOnPasteAttr: PartialAttribute[String] = PartialAttribute("hx-on:paste")

  final def hxOnPauseAttr: PartialAttribute[String] = PartialAttribute("hx-on:pause")

  final def hxOnPlayAttr: PartialAttribute[String] = PartialAttribute("hx-on:play")

  final def hxOnPlayingAttr: PartialAttribute[String] = PartialAttribute("hx-on:playing")

  final def hxOnPopStateAttr: PartialAttribute[String] = PartialAttribute("hx-on:popstate")

  final def hxOnProgressAttr: PartialAttribute[String] = PartialAttribute("hx-on:progress")

  final def hxOnRateChangeAttr: PartialAttribute[String] = PartialAttribute("hx-on:ratechange")

  final def hxOnResetAttr: PartialAttribute[String] = PartialAttribute("hx-on:reset")

  final def hxOnResizeAttr: PartialAttribute[String] = PartialAttribute("hx-on:resize")

  final def hxOnScrollAttr: PartialAttribute[String] = PartialAttribute("hx-on:scroll")

  final def hxOnSearchAttr: PartialAttribute[String] = PartialAttribute("hx-on:search")

  final def hxOnSeekedAttr: PartialAttribute[String] = PartialAttribute("hx-on:seeked")

  final def hxOnSeekingAttr: PartialAttribute[String] = PartialAttribute("hx-on:seeking")

  final def hxOnSelectAttr: PartialAttribute[String] = PartialAttribute("hx-on:select")

  final def hxOnStalledAttr: PartialAttribute[String] = PartialAttribute("hx-on:stalled")

  final def hxOnStorageAttr: PartialAttribute[String] = PartialAttribute("hx-on:storage")

  final def hxOnSubmitAttr: PartialAttribute[String] = PartialAttribute("hx-on:submit")

  final def hxOnSuspendAttr: PartialAttribute[String] = PartialAttribute("hx-on:suspend")

  final def hxOnTimeUpdateAttr: PartialAttribute[String] = PartialAttribute("hx-on:timeupdate")

  final def hxOnToggleAttr: PartialAttribute[String] = PartialAttribute("hx-on:toggle")

  final def hxOnUnloadAttr: PartialAttribute[String] = PartialAttribute("hx-on:unload")

  final def hxOnVolumeChangeAttr: PartialAttribute[String] = PartialAttribute("hx-on:volumechange")

  final def hxOnWaitingAttr: PartialAttribute[String] = PartialAttribute("hx-on:waiting")

  final def hxOnWheelAttr: PartialAttribute[String] = PartialAttribute("hx-on:wheel")

}

object Attributes {
  case class PartialAttribute[A](name: String) {
    def :=(value: A)(implicit ev: IsAttributeValue[A]): Html    = Dom.attr(name, ev(value))
    def apply(value: A)(implicit ev: IsAttributeValue[A]): Html = Dom.attr(name, ev(value))
  }
}
