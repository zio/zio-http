package zio.http.htmx

import zio.http.htmx.Attributes.PartialAttribute
import zio.http.template.{Dom, Html, IsAttributeValue}

/**
 * Definitions of the htmx attributes for the zio-http template DSL. Each member
 * is a [[Attributes.PartialAttribute]] named after an htmx attribute. The
 * members are available through `import zio.http.htmx._`, because the package
 * object extends this trait.
 *
 * {{{
 * import zio.http.template._
 * import zio.http.htmx._
 *
 * val view = button(hxGetAttr := "/clicked", hxSwapAttr("outerHTML"), "Click me")
 * // <button hx-get="/clicked" hx-swap="outerHTML">Click me</button>
 * }}}
 */
trait Attributes {

  /**
   * Returns a partial attribute that renders as `hx-boost`. htmx uses it to
   * turn anchors and forms in the element into AJAX requests.
   */
  final def hxBoostAttr: PartialAttribute[String] = PartialAttribute("hx-boost")

  /**
   * Returns a partial attribute that renders as `hx-get`. htmx uses it to issue
   * a GET request to the given URL.
   */
  final def hxGetAttr: PartialAttribute[String] = PartialAttribute("hx-get")

  /**
   * Returns a partial attribute that renders as `hx-post`. htmx uses it to
   * issue a POST request to the given URL.
   */
  final def hxPostAttr: PartialAttribute[String] = PartialAttribute("hx-post")

  /**
   * Returns a partial attribute named `hx-on*`; the name is rendered literally,
   * with the trailing asterisk.
   */
  final def hxOnAttr: PartialAttribute[String] = PartialAttribute("hx-on*")

  /**
   * Returns a partial attribute that renders as `hx-push-url`. htmx uses it to
   * push a URL onto the browser history stack.
   */
  final def hxPushUrlAttr: PartialAttribute[String] = PartialAttribute("hx-push-url")

  /**
   * Returns a partial attribute that renders as `hx-select`. htmx uses it to
   * pick which part of the response to swap in, by CSS selector.
   */
  final def hxSelectAttr: PartialAttribute[String] = PartialAttribute("hx-select")

  /**
   * Returns a partial attribute that renders as `hx-select-oob`. htmx uses it
   * to pick which parts of the response to swap in out of band.
   */
  final def hxSelectOobAttr: PartialAttribute[String] = PartialAttribute("hx-select-oob")

  /**
   * Returns a partial attribute that renders as `hx-swap`. htmx uses it to
   * control how the response is swapped into the DOM.
   */
  final def hxSwapAttr: PartialAttribute[String] = PartialAttribute("hx-swap")

  /**
   * Returns a partial attribute that renders as `hx-swap-oob`. htmx uses it to
   * mark response content that is swapped in out of band.
   */
  final def hxSwapOobAttr: PartialAttribute[String] = PartialAttribute("hx-swap-oob")

  /**
   * Returns a partial attribute that renders as `hx-target`. htmx uses it to
   * name the element that receives the response.
   */
  final def hxTargetAttr: PartialAttribute[String] = PartialAttribute("hx-target")

  /**
   * Returns a partial attribute that renders as `hx-trigger`. htmx uses it to
   * specify which event triggers the request.
   */
  final def hxTriggerAttr: PartialAttribute[String] = PartialAttribute("hx-trigger")

  /**
   * Returns a partial attribute that renders as `hx-vals`. htmx uses it to add
   * values to the request parameters.
   */
  final def hxValsAttr: PartialAttribute[String] = PartialAttribute("hx-vals")

  /**
   * Returns a partial attribute that renders as `hx-confirm`. htmx uses it to
   * show a confirmation dialog before the request is issued.
   */
  final def hxConfirmAttr: PartialAttribute[String] = PartialAttribute("hx-confirm")

  /**
   * Returns a partial attribute that renders as `hx-delete`. htmx uses it to
   * issue a DELETE request to the given URL.
   */
  final def hxDeleteAttr: PartialAttribute[String] = PartialAttribute("hx-delete")

  /**
   * Returns a partial attribute that renders as `hx-disable`. htmx uses it to
   * disable htmx processing for the element and its children.
   */
  final def hxDisableAttr: PartialAttribute[String] = PartialAttribute("hx-disable")

  /**
   * Returns a partial attribute that renders as `hx-disabled-elt`. htmx uses it
   * to name the elements that are disabled while a request is in flight.
   */
  final def hxDisabledEltAttr: PartialAttribute[String] = PartialAttribute("hx-disabled-elt")

  /**
   * Returns a partial attribute that renders as `hx-disinherit`. htmx uses it
   * to control which attributes the element does not inherit from its parents.
   */
  final def hxDisinheritAttr: PartialAttribute[String] = PartialAttribute("hx-disinherit")

  /**
   * Returns a partial attribute that renders as `hx-encoding`. htmx uses it to
   * change the request encoding, for example to multipart/form-data.
   */
  final def hxEncodingAttr: PartialAttribute[String] = PartialAttribute("hx-encoding")

  /**
   * Returns a partial attribute that renders as `hx-ext`. htmx uses it to
   * enable htmx extensions for the element.
   */
  final def hxExtAttr: PartialAttribute[String] = PartialAttribute("hx-ext")

  /**
   * Returns a partial attribute that renders as `hx-headers`. htmx uses it to
   * add headers to the request.
   */
  final def hxHeadersAttr: PartialAttribute[String] = PartialAttribute("hx-headers")

  /**
   * Returns a partial attribute that renders as `hx-history`. htmx uses it to
   * control whether the page is stored in the history snapshot cache.
   */
  final def hxHistoryAttr: PartialAttribute[String] = PartialAttribute("hx-history")

  /**
   * Returns a partial attribute that renders as `hx-history-elt`. htmx uses it
   * to name the element that is used for history snapshots.
   */
  final def hxHistoryEltAttr: PartialAttribute[String] = PartialAttribute("hx-history-elt")

  /**
   * Returns a partial attribute that renders as `hx-include`. htmx uses it to
   * include additional elements' values in the request.
   */
  final def hxIncludeAttr: PartialAttribute[String] = PartialAttribute("hx-include")

  /**
   * Returns a partial attribute that renders as `hx-indicator`. htmx uses it to
   * name the element that gets the request-in-flight indicator class.
   */
  final def hxIndicatorAttr: PartialAttribute[String] = PartialAttribute("hx-indicator")

  /**
   * Returns a partial attribute that renders as `hx-params`. htmx uses it to
   * filter which parameters are sent with the request.
   */
  final def hxParamsAttr: PartialAttribute[String] = PartialAttribute("hx-params")

  /**
   * Returns a partial attribute that renders as `hx-patch`. htmx uses it to
   * issue a PATCH request to the given URL.
   */
  final def hxPatchAttr: PartialAttribute[String] = PartialAttribute("hx-patch")

  /**
   * Returns a partial attribute that renders as `hx-preserve`. htmx uses it to
   * keep the element unchanged across swaps.
   */
  final def hxPreserveAttr: PartialAttribute[String] = PartialAttribute("hx-preserve")

  /**
   * Returns a partial attribute that renders as `hx-prompt`. htmx uses it to
   * ask the user for a value before the request is issued.
   */
  final def hxPromptAttr: PartialAttribute[String] = PartialAttribute("hx-prompt")

  /**
   * Returns a partial attribute that renders as `hx-put`. htmx uses it to issue
   * a PUT request to the given URL.
   */
  final def hxPutAttr: PartialAttribute[String] = PartialAttribute("hx-put")

  /**
   * Returns a partial attribute that renders as `hx-replace-url`. htmx uses it
   * to replace the current URL in the browser location bar.
   */
  final def hxReplaceUrlAttr: PartialAttribute[String] = PartialAttribute("hx-replace-url")

  /**
   * Returns a partial attribute that renders as `hx-request`. htmx uses it to
   * configure request details such as the timeout.
   */
  final def hxRequestAttr: PartialAttribute[String] = PartialAttribute("hx-request")

  /**
   * Returns a partial attribute that renders as `hx-sse`. Its meaning is
   * defined by htmx.
   */
  final def hxSseAttr: PartialAttribute[String] = PartialAttribute("hx-sse")

  /**
   * Returns a partial attribute that renders as `hx-sync`. htmx uses it to
   * coordinate requests between elements.
   */
  final def hxSyncAttr: PartialAttribute[String] = PartialAttribute("hx-sync")

  /**
   * Returns a partial attribute that renders as `hx-validate`. htmx uses it to
   * force validation of the element before a request.
   */
  final def hxValidateAttr: PartialAttribute[String] = PartialAttribute("hx-validate")

  /**
   * Returns a partial attribute that renders as `hx-vars`. Its meaning is
   * defined by htmx.
   */
  final def hxVarsAttr: PartialAttribute[String] = PartialAttribute("hx-vars")

  /**
   * Returns a partial attribute that renders as `hx-ws`. Its meaning is defined
   * by htmx.
   */
  final def hxWsAttr: PartialAttribute[String] = PartialAttribute("hx-ws")

  /**
   * Returns a partial attribute that renders as `hx-on:abort`. htmx runs the
   * attribute's script when the `abort` event fires on the element.
   */
  final def hxOnAbortAttr: PartialAttribute[String] = PartialAttribute("hx-on:abort")

  /**
   * Returns a partial attribute that renders as `hx-on:afterprint`. htmx runs
   * the attribute's script when the `afterprint` event fires. The browser
   * dispatches it on `window`, not on the element, so this attribute on an
   * element never sees it.
   */
  final def hxOnAfterPrintAttr: PartialAttribute[String] = PartialAttribute("hx-on:afterprint")

  /**
   * Returns a partial attribute that renders as `hx-on:beforeprint`. htmx runs
   * the attribute's script when the `beforeprint` event fires. The browser
   * dispatches it on `window`, not on the element, so this attribute on an
   * element never sees it.
   */
  final def hxOnBeforePrintAttr: PartialAttribute[String] = PartialAttribute("hx-on:beforeprint")

  /**
   * Returns a partial attribute that renders as `hx-on:beforeunload`. htmx runs
   * the attribute's script when the `beforeunload` event fires. The browser
   * dispatches it on `window`, not on the element, so this attribute on an
   * element never sees it.
   */
  final def hxOnBeforeUnloadAttr: PartialAttribute[String] = PartialAttribute("hx-on:beforeunload")

  /**
   * Returns a partial attribute that renders as `hx-on:blur`. htmx runs the
   * attribute's script when the `blur` event fires on the element.
   */
  final def hxOnBlurAttr: PartialAttribute[String] = PartialAttribute("hx-on:blur")

  /**
   * Returns a partial attribute that renders as `hx-on:canplay`. htmx runs the
   * attribute's script when the `canplay` event fires on the element.
   */
  final def hxOnCanPlayAttr: PartialAttribute[String] = PartialAttribute("hx-on:canplay")

  /**
   * Returns a partial attribute that renders as `hx-on:canplaythrough`. htmx
   * runs the attribute's script when the `canplaythrough` event fires on the
   * element.
   */
  final def hxOnCanPlayThroughAttr: PartialAttribute[String] = PartialAttribute("hx-on:canplaythrough")

  /**
   * Returns a partial attribute that renders as `hx-on:change`. htmx runs the
   * attribute's script when the `change` event fires on the element.
   */
  final def hxOnChangeAttr: PartialAttribute[String] = PartialAttribute("hx-on:change")

  /**
   * Returns a partial attribute that renders as `hx-on:click`. htmx runs the
   * attribute's script when the `click` event fires on the element.
   */
  final def hxOnClickAttr: PartialAttribute[String] = PartialAttribute("hx-on:click")

  /**
   * Returns a partial attribute that renders as `hx-on:contextmenu`. htmx runs
   * the attribute's script when the `contextmenu` event fires on the element.
   */
  final def hxOnContextMenuAttr: PartialAttribute[String] = PartialAttribute("hx-on:contextmenu")

  /**
   * Returns a partial attribute that renders as `hx-on:copy`. htmx runs the
   * attribute's script when the `copy` event fires on the element.
   */
  final def hxOnCopyAttr: PartialAttribute[String] = PartialAttribute("hx-on:copy")

  /**
   * Returns a partial attribute that renders as `hx-on:cuechange`. htmx runs
   * the attribute's script when the `cuechange` event fires on the element.
   */
  final def hxOnCueChangeAttr: PartialAttribute[String] = PartialAttribute("hx-on:cuechange")

  /**
   * Returns a partial attribute that renders as `hx-on:cut`. htmx runs the
   * attribute's script when the `cut` event fires on the element.
   */
  final def hxOnCutAttr: PartialAttribute[String] = PartialAttribute("hx-on:cut")

  /**
   * Returns a partial attribute that renders as `hx-on:dblclick`. htmx runs the
   * attribute's script when the `dblclick` event fires on the element.
   */
  final def hxOnDblClickAttr: PartialAttribute[String] = PartialAttribute("hx-on:dblclick")

  /**
   * Returns a partial attribute that renders as `hx-on:drag`. htmx runs the
   * attribute's script when the `drag` event fires on the element.
   */
  final def hxOnDragAttr: PartialAttribute[String] = PartialAttribute("hx-on:drag")

  /**
   * Returns a partial attribute that renders as `hx-on:dragend`. htmx runs the
   * attribute's script when the `dragend` event fires on the element.
   */
  final def hxOnDragEndAttr: PartialAttribute[String] = PartialAttribute("hx-on:dragend")

  /**
   * Returns a partial attribute that renders as `hx-on:dragenter`. htmx runs
   * the attribute's script when the `dragenter` event fires on the element.
   */
  final def hxOnDragEnterAttr: PartialAttribute[String] = PartialAttribute("hx-on:dragenter")

  /**
   * Returns a partial attribute that renders as `hx-on:dragleave`. htmx runs
   * the attribute's script when the `dragleave` event fires on the element.
   */
  final def hxOnDragLeaveAttr: PartialAttribute[String] = PartialAttribute("hx-on:dragleave")

  /**
   * Returns a partial attribute that renders as `hx-on:dragover`. htmx runs the
   * attribute's script when the `dragover` event fires on the element.
   */
  final def hxOnDragOverAttr: PartialAttribute[String] = PartialAttribute("hx-on:dragover")

  /**
   * Returns a partial attribute that renders as `hx-on:dragstart`. htmx runs
   * the attribute's script when the `dragstart` event fires on the element.
   */
  final def hxOnDragStartAttr: PartialAttribute[String] = PartialAttribute("hx-on:dragstart")

  /**
   * Returns a partial attribute that renders as `hx-on:drop`. htmx runs the
   * attribute's script when the `drop` event fires on the element.
   */
  final def hxOnDropAttr: PartialAttribute[String] = PartialAttribute("hx-on:drop")

  /**
   * Returns a partial attribute that renders as `hx-on:durationchange`. htmx
   * runs the attribute's script when the `durationchange` event fires on the
   * element.
   */
  final def hxOnDurationChangeAttr: PartialAttribute[String] = PartialAttribute("hx-on:durationchange")

  /**
   * Returns a partial attribute that renders as `hx-on:emptied`. htmx runs the
   * attribute's script when the `emptied` event fires on the element.
   */
  final def hxOnEmptiedAttr: PartialAttribute[String] = PartialAttribute("hx-on:emptied")

  /**
   * Returns a partial attribute that renders as `hx-on:ended`. htmx runs the
   * attribute's script when the `ended` event fires on the element.
   */
  final def hxOnEndedAttr: PartialAttribute[String] = PartialAttribute("hx-on:ended")

  /**
   * Returns a partial attribute that renders as `hx-on:error`. htmx runs the
   * attribute's script when the `error` event fires on the element.
   */
  final def hxOnErrorAttr: PartialAttribute[String] = PartialAttribute("hx-on:error")

  /**
   * Returns a partial attribute that renders as `hx-on:focus`. htmx runs the
   * attribute's script when the `focus` event fires on the element.
   */
  final def hxOnFocusAttr: PartialAttribute[String] = PartialAttribute("hx-on:focus")

  /**
   * Returns a partial attribute that renders as `hx-on:hashchange`. htmx runs
   * the attribute's script when the `hashchange` event fires. The browser
   * dispatches it on `window`, not on the element, so this attribute on an
   * element never sees it.
   */
  final def hxOnHashChangeAttr: PartialAttribute[String] = PartialAttribute("hx-on:hashchange")

  /**
   * Returns a partial attribute that renders as `hx-on:input`. htmx runs the
   * attribute's script when the `input` event fires on the element.
   */
  final def hxOnInputAttr: PartialAttribute[String] = PartialAttribute("hx-on:input")

  /**
   * Returns a partial attribute that renders as `hx-on:invalid`. htmx runs the
   * attribute's script when the `invalid` event fires on the element.
   */
  final def hxOnInvalidAttr: PartialAttribute[String] = PartialAttribute("hx-on:invalid")

  /**
   * Returns a partial attribute that renders as `hx-on:keydown`. htmx runs the
   * attribute's script when the `keydown` event fires on the element.
   */
  final def hxOnKeyDownAttr: PartialAttribute[String] = PartialAttribute("hx-on:keydown")

  /**
   * Returns a partial attribute that renders as `hx-on:keypress`. htmx runs the
   * attribute's script when the `keypress` event fires on the element.
   */
  final def hxOnKeyPressAttr: PartialAttribute[String] = PartialAttribute("hx-on:keypress")

  /**
   * Returns a partial attribute that renders as `hx-on:keyup`. htmx runs the
   * attribute's script when the `keyup` event fires on the element.
   */
  final def hxOnKeyUpAttr: PartialAttribute[String] = PartialAttribute("hx-on:keyup")

  /**
   * Returns a partial attribute that renders as `hx-on:load`. htmx runs the
   * attribute's script when the `load` event fires on the element.
   */
  final def hxOnLoadAttr: PartialAttribute[String] = PartialAttribute("hx-on:load")

  /**
   * Returns a partial attribute that renders as `hx-on:loadstart`. htmx runs
   * the attribute's script when the `loadstart` event fires on the element.
   */
  final def hxOnLoadStartAttr: PartialAttribute[String] = PartialAttribute("hx-on:loadstart")

  /**
   * Returns a partial attribute that renders as `hx-on:loadeddata`. htmx runs
   * the attribute's script when the `loadeddata` event fires on the element.
   */
  final def hxOnLoadedDataAttr: PartialAttribute[String] = PartialAttribute("hx-on:loadeddata")

  /**
   * Returns a partial attribute that renders as `hx-on:loadedmetadata`. htmx
   * runs the attribute's script when the `loadedmetadata` event fires on the
   * element.
   */
  final def hxOnLoadedMetadataAttr: PartialAttribute[String] = PartialAttribute("hx-on:loadedmetadata")

  /**
   * Returns a partial attribute that renders as `hx-on:mousedown`. htmx runs
   * the attribute's script when the `mousedown` event fires on the element.
   */
  final def hxOnMouseDownAttr: PartialAttribute[String] = PartialAttribute("hx-on:mousedown")

  /**
   * Returns a partial attribute that renders as `hx-on:mousemove`. htmx runs
   * the attribute's script when the `mousemove` event fires on the element.
   */
  final def hxOnMouseMoveAttr: PartialAttribute[String] = PartialAttribute("hx-on:mousemove")

  /**
   * Returns a partial attribute that renders as `hx-on:mouseout`. htmx runs the
   * attribute's script when the `mouseout` event fires on the element.
   */
  final def hxOnMouseOutAttr: PartialAttribute[String] = PartialAttribute("hx-on:mouseout")

  /**
   * Returns a partial attribute that renders as `hx-on:mouseover`. htmx runs
   * the attribute's script when the `mouseover` event fires on the element.
   */
  final def hxOnMouseOverAttr: PartialAttribute[String] = PartialAttribute("hx-on:mouseover")

  /**
   * Returns a partial attribute that renders as `hx-on:mouseup`. htmx runs the
   * attribute's script when the `mouseup` event fires on the element.
   */
  final def hxOnMouseUpAttr: PartialAttribute[String] = PartialAttribute("hx-on:mouseup")

  /**
   * Returns a partial attribute that renders as `hx-on:mousewheel`. htmx runs
   * the attribute's script when the `mousewheel` event fires on the element.
   */
  final def hxOnMouseWheelAttr: PartialAttribute[String] = PartialAttribute("hx-on:mousewheel")

  /**
   * Returns a partial attribute that renders as `hx-on:offline`. htmx runs the
   * attribute's script when the `offline` event fires. The browser dispatches
   * it on `window`, not on the element, so this attribute on an element never
   * sees it.
   */
  final def hxOnOfflineAttr: PartialAttribute[String] = PartialAttribute("hx-on:offline")

  /**
   * Returns a partial attribute that renders as `hx-on:online`. htmx runs the
   * attribute's script when the `online` event fires. The browser dispatches it
   * on `window`, not on the element, so this attribute on an element never sees
   * it.
   */
  final def hxOnOnlineAttr: PartialAttribute[String] = PartialAttribute("hx-on:online")

  /**
   * Returns a partial attribute that renders as `hx-on:pagehide`. htmx runs the
   * attribute's script when the `pagehide` event fires. The browser dispatches
   * it on `window`, not on the element, so this attribute on an element never
   * sees it.
   */
  final def hxOnPageHideAttr: PartialAttribute[String] = PartialAttribute("hx-on:pagehide")

  /**
   * Returns a partial attribute that renders as `hx-on:pageshow`. htmx runs the
   * attribute's script when the `pageshow` event fires. The browser dispatches
   * it on `window`, not on the element, so this attribute on an element never
   * sees it.
   */
  final def hxOnPageShowAttr: PartialAttribute[String] = PartialAttribute("hx-on:pageshow")

  /**
   * Returns a partial attribute that renders as `hx-on:paste`. htmx runs the
   * attribute's script when the `paste` event fires on the element.
   */
  final def hxOnPasteAttr: PartialAttribute[String] = PartialAttribute("hx-on:paste")

  /**
   * Returns a partial attribute that renders as `hx-on:pause`. htmx runs the
   * attribute's script when the `pause` event fires on the element.
   */
  final def hxOnPauseAttr: PartialAttribute[String] = PartialAttribute("hx-on:pause")

  /**
   * Returns a partial attribute that renders as `hx-on:play`. htmx runs the
   * attribute's script when the `play` event fires on the element.
   */
  final def hxOnPlayAttr: PartialAttribute[String] = PartialAttribute("hx-on:play")

  /**
   * Returns a partial attribute that renders as `hx-on:playing`. htmx runs the
   * attribute's script when the `playing` event fires on the element.
   */
  final def hxOnPlayingAttr: PartialAttribute[String] = PartialAttribute("hx-on:playing")

  /**
   * Returns a partial attribute that renders as `hx-on:popstate`. htmx runs the
   * attribute's script when the `popstate` event fires. The browser dispatches
   * it on `window`, not on the element, so this attribute on an element never
   * sees it.
   */
  final def hxOnPopStateAttr: PartialAttribute[String] = PartialAttribute("hx-on:popstate")

  /**
   * Returns a partial attribute that renders as `hx-on:progress`. htmx runs the
   * attribute's script when the `progress` event fires on the element.
   */
  final def hxOnProgressAttr: PartialAttribute[String] = PartialAttribute("hx-on:progress")

  /**
   * Returns a partial attribute that renders as `hx-on:ratechange`. htmx runs
   * the attribute's script when the `ratechange` event fires on the element.
   */
  final def hxOnRateChangeAttr: PartialAttribute[String] = PartialAttribute("hx-on:ratechange")

  /**
   * Returns a partial attribute that renders as `hx-on:reset`. htmx runs the
   * attribute's script when the `reset` event fires on the element.
   */
  final def hxOnResetAttr: PartialAttribute[String] = PartialAttribute("hx-on:reset")

  /**
   * Returns a partial attribute that renders as `hx-on:resize`. htmx runs the
   * attribute's script when the `resize` event fires. The browser dispatches it
   * on `window`, not on the element, so this attribute on an element never sees
   * it.
   */
  final def hxOnResizeAttr: PartialAttribute[String] = PartialAttribute("hx-on:resize")

  /**
   * Returns a partial attribute that renders as `hx-on:scroll`. htmx runs the
   * attribute's script when the `scroll` event fires on the element.
   */
  final def hxOnScrollAttr: PartialAttribute[String] = PartialAttribute("hx-on:scroll")

  /**
   * Returns a partial attribute that renders as `hx-on:search`. htmx runs the
   * attribute's script when the `search` event fires on the element.
   */
  final def hxOnSearchAttr: PartialAttribute[String] = PartialAttribute("hx-on:search")

  /**
   * Returns a partial attribute that renders as `hx-on:seeked`. htmx runs the
   * attribute's script when the `seeked` event fires on the element.
   */
  final def hxOnSeekedAttr: PartialAttribute[String] = PartialAttribute("hx-on:seeked")

  /**
   * Returns a partial attribute that renders as `hx-on:seeking`. htmx runs the
   * attribute's script when the `seeking` event fires on the element.
   */
  final def hxOnSeekingAttr: PartialAttribute[String] = PartialAttribute("hx-on:seeking")

  /**
   * Returns a partial attribute that renders as `hx-on:select`. htmx runs the
   * attribute's script when the `select` event fires on the element.
   */
  final def hxOnSelectAttr: PartialAttribute[String] = PartialAttribute("hx-on:select")

  /**
   * Returns a partial attribute that renders as `hx-on:stalled`. htmx runs the
   * attribute's script when the `stalled` event fires on the element.
   */
  final def hxOnStalledAttr: PartialAttribute[String] = PartialAttribute("hx-on:stalled")

  /**
   * Returns a partial attribute that renders as `hx-on:storage`. htmx runs the
   * attribute's script when the `storage` event fires. The browser dispatches
   * it on `window`, not on the element, so this attribute on an element never
   * sees it.
   */
  final def hxOnStorageAttr: PartialAttribute[String] = PartialAttribute("hx-on:storage")

  /**
   * Returns a partial attribute that renders as `hx-on:submit`. htmx runs the
   * attribute's script when the `submit` event fires on the element.
   */
  final def hxOnSubmitAttr: PartialAttribute[String] = PartialAttribute("hx-on:submit")

  /**
   * Returns a partial attribute that renders as `hx-on:suspend`. htmx runs the
   * attribute's script when the `suspend` event fires on the element.
   */
  final def hxOnSuspendAttr: PartialAttribute[String] = PartialAttribute("hx-on:suspend")

  /**
   * Returns a partial attribute that renders as `hx-on:timeupdate`. htmx runs
   * the attribute's script when the `timeupdate` event fires on the element.
   */
  final def hxOnTimeUpdateAttr: PartialAttribute[String] = PartialAttribute("hx-on:timeupdate")

  /**
   * Returns a partial attribute that renders as `hx-on:toggle`. htmx runs the
   * attribute's script when the `toggle` event fires on the element.
   */
  final def hxOnToggleAttr: PartialAttribute[String] = PartialAttribute("hx-on:toggle")

  /**
   * Returns a partial attribute that renders as `hx-on:unload`. htmx runs the
   * attribute's script when the `unload` event fires. The browser dispatches it
   * on `window`, not on the element, so this attribute on an element never sees
   * it.
   */
  final def hxOnUnloadAttr: PartialAttribute[String] = PartialAttribute("hx-on:unload")

  /**
   * Returns a partial attribute that renders as `hx-on:volumechange`. htmx runs
   * the attribute's script when the `volumechange` event fires on the element.
   */
  final def hxOnVolumeChangeAttr: PartialAttribute[String] = PartialAttribute("hx-on:volumechange")

  /**
   * Returns a partial attribute that renders as `hx-on:waiting`. htmx runs the
   * attribute's script when the `waiting` event fires on the element.
   */
  final def hxOnWaitingAttr: PartialAttribute[String] = PartialAttribute("hx-on:waiting")

  /**
   * Returns a partial attribute that renders as `hx-on:wheel`. htmx runs the
   * attribute's script when the `wheel` event fires on the element.
   */
  final def hxOnWheelAttr: PartialAttribute[String] = PartialAttribute("hx-on:wheel")

}

/**
 * Holds the `PartialAttribute` type that the members of [[Attributes]] return.
 */
object Attributes {

  /**
   * An attribute with a fixed name whose value is supplied later. Applying it
   * to a value yields an `Html` attribute node with that name.
   */
  case class PartialAttribute[A](name: String) {

    /**
     * Returns an `Html` attribute node with this name and the given value,
     * rendered through `IsAttributeValue`.
     */
    def :=(value: A)(implicit ev: IsAttributeValue[A]): Html = Dom.attr(name, ev(value))

    /**
     * Returns an `Html` attribute node with this name and the given value. Same
     * as `:=`.
     */
    def apply(value: A)(implicit ev: IsAttributeValue[A]): Html = Dom.attr(name, ev(value))
  }
}
