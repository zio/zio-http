/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.template

import scala.language.implicitConversions

import zio.http.template.Attributes.{PartialAttribute, PartialBooleanAttribute}

/**
 * The HTML attribute helpers, such as `id`, `href` and `classAttr`. Each one returns a partial attribute that gets its value with `:=` or by being applied to the value, and then goes among the children of an element. The `zio.http.template` package object extends this trait, so `import zio.http.template._` brings the helpers into scope.
 */
trait Attributes {
  /** Returns a partial `accept` attribute (the file types that a file input accepts). */
  final def acceptAttr: PartialAttribute[String] = PartialAttribute("accept")

  /** Returns a partial `accept-charset` attribute (the character encodings of a form submission). */
  final def acceptCharsetAttr: PartialAttribute[String] = PartialAttribute("accept-charset")

  /** Returns a partial `accesskey` attribute (a keyboard shortcut for the element). */
  final def accessKeyAttr: PartialAttribute[String] = PartialAttribute("accesskey")

  /** Returns a partial `action` attribute (the URL that a form submits to). */
  final def actionAttr: PartialAttribute[String] = PartialAttribute("action")

  /** Returns a partial `align` attribute (the alignment of the content). The HTML standard lists it as obsolete. */
  final def alignAttr: PartialAttribute[String] = PartialAttribute("align")

  /** Returns a partial `alt` attribute (the alternative text of an image). */
  final def altAttr: PartialAttribute[String] = PartialAttribute("alt")

  /** Returns a partial `async` attribute (runs a script asynchronously). It takes a string value: `asyncAttr := "async"` renders `async="async"`. */
  final def asyncAttr: PartialAttribute[String] = PartialAttribute("async")

  /** Returns a partial attribute with the given name, for an attribute that has no helper of its own. */
  final def attr(name: String): PartialAttribute[String] = PartialAttribute(name)

  /** Returns a partial `autocomplete` attribute (the autofill hint of a form control). */
  final def autocompleteAttr: PartialAttribute[String] = PartialAttribute("autocomplete")

  /** Returns a partial `autofocus` attribute (focuses the element when the page loads). It takes a string value: `autofocusAttr := "autofocus"` renders `autofocus="autofocus"`. */
  final def autofocusAttr: PartialAttribute[String] = PartialAttribute("autofocus")

  /** Returns a partial `autoplay` attribute (starts media playback automatically). It takes a string value: `autoplayAttr := "autoplay"` renders `autoplay="autoplay"`. */
  final def autoplayAttr: PartialAttribute[String] = PartialAttribute("autoplay")

  /** Returns a partial `bgcolor` attribute (the background color). The HTML standard lists it as obsolete. */
  final def bgColorAttr: PartialAttribute[String] = PartialAttribute("bgcolor")

  /** Returns a partial `border` attribute (the border width). */
  final def borderAttr: PartialAttribute[String] = PartialAttribute("border")

  /** Returns a partial `charset` attribute (the character encoding of the document). */
  final def charsetAttr: PartialAttribute[String] = PartialAttribute("charset")

  /** Returns a partial `checked` attribute (marks a checkbox or radio button as checked). It takes a string value: `checkedAttr := "checked"` renders `checked="checked"`. */
  final def checkedAttr: PartialAttribute[String] = PartialAttribute("checked")

  /** Returns a partial `cite` attribute (the URL of the source of a quotation or change). */
  final def citeAttr: PartialAttribute[String] = PartialAttribute("cite")

  /** Returns a partial `class` attribute (the CSS classes of the element). */
  final def classAttr: PartialAttribute[String] = PartialAttribute("class")

  /** Returns a partial `colspan` attribute (the number of columns that a cell spans). */
  final def colSpanAttr: PartialAttribute[String] = PartialAttribute("colspan")

  /** Returns a partial `color` attribute (a color). */
  final def colorAttr: PartialAttribute[String] = PartialAttribute("color")

  /** Returns a partial `cols` attribute (the visible width of a textarea in characters). */
  final def colsAttr: PartialAttribute[String] = PartialAttribute("cols")

  /** Returns a partial `content` attribute (the value of a meta element). */
  final def contentAttr: PartialAttribute[String] = PartialAttribute("content")

  /** Returns a partial `contenteditable` attribute (whether the user can edit the content). */
  final def contentEditableAttr: PartialAttribute[String] = PartialAttribute("contenteditable")

  /** Returns a partial `controls` attribute (shows the media controls). It takes a string value: `controlsAttr := "controls"` renders `controls="controls"`. */
  final def controlsAttr: PartialAttribute[String] = PartialAttribute("controls")

  /** Returns a partial `coords` attribute (the coordinates of an image map area). */
  final def coordsAttr: PartialAttribute[String] = PartialAttribute("coords")

  /** Returns a partial `class` attribute (the CSS classes of the element); the same as `classAttr`. */
  final def css: PartialAttribute[String] = classAttr

  /** Returns a partial custom data attribute named `data-` followed by `name`: `dataAttr("user-id")` is the `data-user-id` attribute. */
  final def dataAttr(name: String): PartialAttribute[String] = PartialAttribute("data-" + name)

  /** Returns a partial `datetime` attribute (a machine-readable date or time). */
  final def datetimeAttr: PartialAttribute[String] = PartialAttribute("datetime")

  /** Returns a partial `default` attribute (marks the default text track). It takes a string value: `defaultAttr := "default"` renders `default="default"`. */
  final def defaultAttr: PartialAttribute[String] = PartialAttribute("default")

  /** Returns a partial `defer` attribute (runs a script after the document is parsed). It takes a string value: `deferAttr := "defer"` renders `defer="defer"`. */
  final def deferAttr: PartialAttribute[String] = PartialAttribute("defer")

  /** Returns a partial `dir` attribute (the text direction). */
  final def dirAttr: PartialAttribute[String] = PartialAttribute("dir")

  /** Returns a partial `dirname` attribute (the form field that sends the text direction). */
  final def dirNameAttr: PartialAttribute[String] = PartialAttribute("dirname")

  /** Returns a partial `disabled` attribute (disables a form control). It takes a string value: `disabledAttr := "disabled"` renders `disabled="disabled"`. */
  final def disabledAttr: PartialAttribute[String] = PartialAttribute("disabled")

  /** Returns a partial `download` attribute (downloads the link target instead of navigating to it). */
  final def downloadAttr: PartialAttribute[String] = PartialAttribute("download")

  /** Returns a partial `draggable` attribute (whether the element can be dragged). */
  final def draggableAttr: PartialAttribute[String] = PartialAttribute("draggable")

  /** Returns a partial `enctype` attribute (the encoding of a form submission). */
  final def enctypeAttr: PartialAttribute[String] = PartialAttribute("enctype")

  /** Returns a partial `for` attribute (the id of the form control that a label or output belongs to). */
  final def forAttr: PartialAttribute[String] = PartialAttribute("for")

  /** Returns a partial `formaction` attribute (the URL that a submit button submits to). */
  final def formActionAttr: PartialAttribute[String] = PartialAttribute("formaction")

  /** Returns a partial `form` attribute (the id of the form that the control belongs to). */
  final def formAttr: PartialAttribute[String] = PartialAttribute("form")

  /** Returns a partial `headers` attribute (the header cells of a table cell). */
  final def headersAttr: PartialAttribute[String] = PartialAttribute("headers")

  /** Returns a partial `height` attribute (the height of the element). */
  final def heightAttr: PartialAttribute[String] = PartialAttribute("height")

  /** Returns a partial `hidden` attribute (hides the element). It takes a string value: `hiddenAttr := "hidden"` renders `hidden="hidden"`. */
  final def hiddenAttr: PartialAttribute[String] = PartialAttribute("hidden")

  /** Returns a partial `high` attribute (the lower bound of the high range of a meter). */
  final def highAttr: PartialAttribute[String] = PartialAttribute("high")

  /**
   * Returns a partial `href` attribute (the URL of a link). Give it a value with `:=` or by applying it to the value; as a child of an element, such as `a`, it becomes an attribute of that element. The value is escaped when the element renders, so `&` in a URL becomes `&amp;`. `hrefAttr` builds the same attribute.
   *
   * {{{
   * val next: Dom = a(href := "/search?q=zio&page=2", "Next page")
   *
   * next.encode
   * // <a href="/search?q=zio&amp;page=2">Next page</a>
   * }}}
   *
   * @see [[https://ziohttp.com/reference/template/ Template DSL]]
   */
  final def href: PartialAttribute[String] = PartialAttribute("href")

  /** Returns a partial `href` attribute (the URL of a link). `href` builds the same attribute. */
  final def hrefAttr: PartialAttribute[String] = PartialAttribute("href")

  /** Returns a partial `hreflang` attribute (the language of the linked resource). */
  final def hrefLangAttr: PartialAttribute[String] = PartialAttribute("hreflang")

  /** Returns a partial `http-equiv` attribute (the pragma directive of a meta element). */
  final def httpEquivAttr: PartialAttribute[String] = PartialAttribute("http-equiv")

  /** Returns a partial `id` attribute (the unique identifier of the element). `idAttr` builds the same attribute. */
  final def id: PartialAttribute[String] = PartialAttribute("id")

  /** Returns a partial `id` attribute (the unique identifier of the element). `id` builds the same attribute. */
  final def idAttr: PartialAttribute[String] = PartialAttribute("id")

  /** Returns a partial `ismap` attribute (marks an image as a server-side image map). It takes a string value: `isMapAttr := "ismap"` renders `ismap="ismap"`. */
  final def isMapAttr: PartialAttribute[String] = PartialAttribute("ismap")

  /** Returns a partial `kind` attribute (the kind of a text track). */
  final def kindAttr: PartialAttribute[String] = PartialAttribute("kind")

  /** Returns a partial `label` attribute (a user-visible label). */
  final def labelAttr: PartialAttribute[String] = PartialAttribute("label")

  /** Returns a partial `lang` attribute (the language of the content). */
  final def langAttr: PartialAttribute[String] = PartialAttribute("lang")

  /** Returns a partial `list` attribute (the id of the datalist of an input). */
  final def listAttr: PartialAttribute[String] = PartialAttribute("list")

  /** Returns a partial `loop` attribute (replays the media when it ends). It takes a string value: `loopAttr := "loop"` renders `loop="loop"`. */
  final def loopAttr: PartialAttribute[String] = PartialAttribute("loop")

  /** Returns a partial `low` attribute (the upper bound of the low range of a meter). */
  final def lowAttr: PartialAttribute[String] = PartialAttribute("low")

  /** Returns a partial `max` attribute (the maximum value). */
  final def maxAttr: PartialAttribute[String] = PartialAttribute("max")

  /** Returns a partial `maxlength` attribute (the maximum length of the value). */
  final def maxLengthAttr: PartialAttribute[String] = PartialAttribute("maxlength")

  /** Returns a partial `media` attribute (the media query that the resource applies to). */
  final def mediaAttr: PartialAttribute[String] = PartialAttribute("media")

  /** Returns a partial `method` attribute (the HTTP method of a form submission). */
  final def methodAttr: PartialAttribute[String] = PartialAttribute("method")

  /** Returns a partial `min` attribute (the minimum value). */
  final def minAttr: PartialAttribute[String] = PartialAttribute("min")

  /** Returns a partial `multiple` attribute (allows more than one value). It takes a string value: `multipleAttr := "multiple"` renders `multiple="multiple"`. */
  final def multipleAttr: PartialAttribute[String] = PartialAttribute("multiple")

  /** Returns a partial `muted` attribute (mutes the media by default). It takes a string value: `mutedAttr := "muted"` renders `muted="muted"`. */
  final def mutedAttr: PartialAttribute[String] = PartialAttribute("muted")

  /** Returns a partial `name` attribute (the name of the element, such as a form control's field name). */
  final def nameAttr: PartialAttribute[String] = PartialAttribute("name")

  /** Returns a partial `novalidate` attribute (turns off form validation on submission). It takes a string value: `noValidateAttr := "novalidate"` renders `novalidate="novalidate"`. */
  final def noValidateAttr: PartialAttribute[String] = PartialAttribute("novalidate")

  /** Returns a partial `onabort` attribute (JavaScript code that runs on the `abort` event). */
  final def onAbortAttr: PartialAttribute[String] = PartialAttribute("onabort")

  /** Returns a partial `onafterprint` attribute (JavaScript code that runs on the `afterprint` event). */
  final def onAfterPrintAttr: PartialAttribute[String] = PartialAttribute("onafterprint")

  /** Returns a partial `onbeforeprint` attribute (JavaScript code that runs on the `beforeprint` event). */
  final def onBeforePrintAttr: PartialAttribute[String] = PartialAttribute("onbeforeprint")

  /** Returns a partial `onbeforeunload` attribute (JavaScript code that runs on the `beforeunload` event). */
  final def onBeforeUnloadAttr: PartialAttribute[String] = PartialAttribute("onbeforeunload")

  /** Returns a partial `onblur` attribute (JavaScript code that runs on the `blur` event). */
  final def onBlurAttr: PartialAttribute[String] = PartialAttribute("onblur")

  /** Returns a partial `oncanplay` attribute (JavaScript code that runs on the `canplay` event). */
  final def onCanPlayAttr: PartialAttribute[String] = PartialAttribute("oncanplay")

  /** Returns a partial `oncanplaythrough` attribute (JavaScript code that runs on the `canplaythrough` event). */
  final def onCanPlayThroughAttr: PartialAttribute[String] = PartialAttribute("oncanplaythrough")

  /** Returns a partial `onchange` attribute (JavaScript code that runs on the `change` event). */
  final def onChangeAttr: PartialAttribute[String] = PartialAttribute("onchange")

  /** Returns a partial `onclick` attribute (JavaScript code that runs on the `click` event). */
  final def onClickAttr: PartialAttribute[String] = PartialAttribute("onclick")

  /** Returns a partial `oncontextmenu` attribute (JavaScript code that runs on the `contextmenu` event). */
  final def onContextMenuAttr: PartialAttribute[String] = PartialAttribute("oncontextmenu")

  /** Returns a partial `oncopy` attribute (JavaScript code that runs on the `copy` event). */
  final def onCopyAttr: PartialAttribute[String] = PartialAttribute("oncopy")

  /** Returns a partial `oncuechange` attribute (JavaScript code that runs on the `cuechange` event). */
  final def onCueChangeAttr: PartialAttribute[String] = PartialAttribute("oncuechange")

  /** Returns a partial `oncut` attribute (JavaScript code that runs on the `cut` event). */
  final def onCutAttr: PartialAttribute[String] = PartialAttribute("oncut")

  /** Returns a partial `ondblclick` attribute (JavaScript code that runs on the `dblclick` event). */
  final def onDblClickAttr: PartialAttribute[String] = PartialAttribute("ondblclick")

  /** Returns a partial `ondrag` attribute (JavaScript code that runs on the `drag` event). */
  final def onDragAttr: PartialAttribute[String] = PartialAttribute("ondrag")

  /** Returns a partial `ondragend` attribute (JavaScript code that runs on the `dragend` event). */
  final def onDragEndAttr: PartialAttribute[String] = PartialAttribute("ondragend")

  /** Returns a partial `ondragenter` attribute (JavaScript code that runs on the `dragenter` event). */
  final def onDragEnterAttr: PartialAttribute[String] = PartialAttribute("ondragenter")

  /** Returns a partial `ondragleave` attribute (JavaScript code that runs on the `dragleave` event). */
  final def onDragLeaveAttr: PartialAttribute[String] = PartialAttribute("ondragleave")

  /** Returns a partial `ondragover` attribute (JavaScript code that runs on the `dragover` event). */
  final def onDragOverAttr: PartialAttribute[String] = PartialAttribute("ondragover")

  /** Returns a partial `ondragstart` attribute (JavaScript code that runs on the `dragstart` event). */
  final def onDragStartAttr: PartialAttribute[String] = PartialAttribute("ondragstart")

  /** Returns a partial `ondrop` attribute (JavaScript code that runs on the `drop` event). */
  final def onDropAttr: PartialAttribute[String] = PartialAttribute("ondrop")

  /** Returns a partial `ondurationchange` attribute (JavaScript code that runs on the `durationchange` event). */
  final def onDurationChangeAttr: PartialAttribute[String] = PartialAttribute("ondurationchange")

  /** Returns a partial `onemptied` attribute (JavaScript code that runs on the `emptied` event). */
  final def onEmptiedAttr: PartialAttribute[String] = PartialAttribute("onemptied")

  /** Returns a partial `onended` attribute (JavaScript code that runs on the `ended` event). */
  final def onEndedAttr: PartialAttribute[String] = PartialAttribute("onended")

  /** Returns a partial `onerror` attribute (JavaScript code that runs on the `error` event). */
  final def onErrorAttr: PartialAttribute[String] = PartialAttribute("onerror")

  /** Returns a partial `onfocus` attribute (JavaScript code that runs on the `focus` event). */
  final def onFocusAttr: PartialAttribute[String] = PartialAttribute("onfocus")

  /** Returns a partial `onhashchange` attribute (JavaScript code that runs on the `hashchange` event). */
  final def onHashChangeAttr: PartialAttribute[String] = PartialAttribute("onhashchange")

  /** Returns a partial `oninput` attribute (JavaScript code that runs on the `input` event). */
  final def onInputAttr: PartialAttribute[String] = PartialAttribute("oninput")

  /** Returns a partial `oninvalid` attribute (JavaScript code that runs on the `invalid` event). */
  final def onInvalidAttr: PartialAttribute[String] = PartialAttribute("oninvalid")

  /** Returns a partial `onkeydown` attribute (JavaScript code that runs on the `keydown` event). */
  final def onKeyDownAttr: PartialAttribute[String] = PartialAttribute("onkeydown")

  /** Returns a partial `onkeypress` attribute (JavaScript code that runs on the `keypress` event). */
  final def onKeyPressAttr: PartialAttribute[String] = PartialAttribute("onkeypress")

  /** Returns a partial `onkeyup` attribute (JavaScript code that runs on the `keyup` event). */
  final def onKeyUpAttr: PartialAttribute[String] = PartialAttribute("onkeyup")

  /** Returns a partial `onload` attribute (JavaScript code that runs on the `load` event). */
  final def onLoadAttr: PartialAttribute[String] = PartialAttribute("onload")

  /** Returns a partial `onloadstart` attribute (JavaScript code that runs on the `loadstart` event). */
  final def onLoadStartAttr: PartialAttribute[String] = PartialAttribute("onloadstart")

  /** Returns a partial `onloadeddata` attribute (JavaScript code that runs on the `loadeddata` event). */
  final def onLoadedDataAttr: PartialAttribute[String] = PartialAttribute("onloadeddata")

  /** Returns a partial `onloadedmetadata` attribute (JavaScript code that runs on the `loadedmetadata` event). */
  final def onLoadedMetadataAttr: PartialAttribute[String] = PartialAttribute("onloadedmetadata")

  /** Returns a partial `onmousedown` attribute (JavaScript code that runs on the `mousedown` event). */
  final def onMouseDownAttr: PartialAttribute[String] = PartialAttribute("onmousedown")

  /** Returns a partial `onmousemove` attribute (JavaScript code that runs on the `mousemove` event). */
  final def onMouseMoveAttr: PartialAttribute[String] = PartialAttribute("onmousemove")

  /** Returns a partial `onmouseout` attribute (JavaScript code that runs on the `mouseout` event). */
  final def onMouseOutAttr: PartialAttribute[String] = PartialAttribute("onmouseout")

  /** Returns a partial `onmouseover` attribute (JavaScript code that runs on the `mouseover` event). */
  final def onMouseOverAttr: PartialAttribute[String] = PartialAttribute("onmouseover")

  /** Returns a partial `onmouseup` attribute (JavaScript code that runs on the `mouseup` event). */
  final def onMouseUpAttr: PartialAttribute[String] = PartialAttribute("onmouseup")

  /** Returns a partial `onmousewheel` attribute (JavaScript code that runs on the `mousewheel` event). */
  final def onMouseWheelAttr: PartialAttribute[String] = PartialAttribute("onmousewheel")

  /** Returns a partial `onoffline` attribute (JavaScript code that runs on the `offline` event). */
  final def onOfflineAttr: PartialAttribute[String] = PartialAttribute("onoffline")

  /** Returns a partial `ononline` attribute (JavaScript code that runs on the `online` event). */
  final def onOnlineAttr: PartialAttribute[String] = PartialAttribute("ononline")

  /** Returns a partial `onpagehide` attribute (JavaScript code that runs on the `pagehide` event). */
  final def onPageHideAttr: PartialAttribute[String] = PartialAttribute("onpagehide")

  /** Returns a partial `onpageshow` attribute (JavaScript code that runs on the `pageshow` event). */
  final def onPageShowAttr: PartialAttribute[String] = PartialAttribute("onpageshow")

  /** Returns a partial `onpaste` attribute (JavaScript code that runs on the `paste` event). */
  final def onPasteAttr: PartialAttribute[String] = PartialAttribute("onpaste")

  /** Returns a partial `onpause` attribute (JavaScript code that runs on the `pause` event). */
  final def onPauseAttr: PartialAttribute[String] = PartialAttribute("onpause")

  /** Returns a partial `onplay` attribute (JavaScript code that runs on the `play` event). */
  final def onPlayAttr: PartialAttribute[String] = PartialAttribute("onplay")

  /** Returns a partial `onplaying` attribute (JavaScript code that runs on the `playing` event). */
  final def onPlayingAttr: PartialAttribute[String] = PartialAttribute("onplaying")

  /** Returns a partial `onpopstate` attribute (JavaScript code that runs on the `popstate` event). */
  final def onPopStateAttr: PartialAttribute[String] = PartialAttribute("onpopstate")

  /** Returns a partial `onprogress` attribute (JavaScript code that runs on the `progress` event). */
  final def onProgressAttr: PartialAttribute[String] = PartialAttribute("onprogress")

  /** Returns a partial `onratechange` attribute (JavaScript code that runs on the `ratechange` event). */
  final def onRateChangeAttr: PartialAttribute[String] = PartialAttribute("onratechange")

  /** Returns a partial `onreset` attribute (JavaScript code that runs on the `reset` event). */
  final def onResetAttr: PartialAttribute[String] = PartialAttribute("onreset")

  /** Returns a partial `onresize` attribute (JavaScript code that runs on the `resize` event). */
  final def onResizeAttr: PartialAttribute[String] = PartialAttribute("onresize")

  /** Returns a partial `onscroll` attribute (JavaScript code that runs on the `scroll` event). */
  final def onScrollAttr: PartialAttribute[String] = PartialAttribute("onscroll")

  /** Returns a partial `onsearch` attribute (JavaScript code that runs on the `search` event). */
  final def onSearchAttr: PartialAttribute[String] = PartialAttribute("onsearch")

  /** Returns a partial `onseeked` attribute (JavaScript code that runs on the `seeked` event). */
  final def onSeekedAttr: PartialAttribute[String] = PartialAttribute("onseeked")

  /** Returns a partial `onseeking` attribute (JavaScript code that runs on the `seeking` event). */
  final def onSeekingAttr: PartialAttribute[String] = PartialAttribute("onseeking")

  /** Returns a partial `onselect` attribute (JavaScript code that runs on the `select` event). */
  final def onSelectAttr: PartialAttribute[String] = PartialAttribute("onselect")

  /** Returns a partial `onstalled` attribute (JavaScript code that runs on the `stalled` event). */
  final def onStalledAttr: PartialAttribute[String] = PartialAttribute("onstalled")

  /** Returns a partial `onstorage` attribute (JavaScript code that runs on the `storage` event). */
  final def onStorageAttr: PartialAttribute[String] = PartialAttribute("onstorage")

  /** Returns a partial `onsubmit` attribute (JavaScript code that runs on the `submit` event). */
  final def onSubmitAttr: PartialAttribute[String] = PartialAttribute("onsubmit")

  /** Returns a partial `onsuspend` attribute (JavaScript code that runs on the `suspend` event). */
  final def onSuspendAttr: PartialAttribute[String] = PartialAttribute("onsuspend")

  /** Returns a partial `ontimeupdate` attribute (JavaScript code that runs on the `timeupdate` event). */
  final def onTimeUpdateAttr: PartialAttribute[String] = PartialAttribute("ontimeupdate")

  /** Returns a partial `ontoggle` attribute (JavaScript code that runs on the `toggle` event). */
  final def onToggleAttr: PartialAttribute[String] = PartialAttribute("ontoggle")

  /** Returns a partial `onunload` attribute (JavaScript code that runs on the `unload` event). */
  final def onUnloadAttr: PartialAttribute[String] = PartialAttribute("onunload")

  /** Returns a partial `onvolumechange` attribute (JavaScript code that runs on the `volumechange` event). */
  final def onVolumeChangeAttr: PartialAttribute[String] = PartialAttribute("onvolumechange")

  /** Returns a partial `onwaiting` attribute (JavaScript code that runs on the `waiting` event). */
  final def onWaitingAttr: PartialAttribute[String] = PartialAttribute("onwaiting")

  /** Returns a partial `onwheel` attribute (JavaScript code that runs on the `wheel` event). */
  final def onWheelAttr: PartialAttribute[String] = PartialAttribute("onwheel")

  /** Returns a partial `open` attribute (shows the content of a details or dialog element). It takes a string value: `openAttr := "open"` renders `open="open"`. */
  final def openAttr: PartialAttribute[String] = PartialAttribute("open")

  /** Returns a partial `optimum` attribute (the optimum value of a meter). */
  final def optimumAttr: PartialAttribute[String] = PartialAttribute("optimum")

  /** Returns a partial `pattern` attribute (the regular expression that the value must match). */
  final def patternAttr: PartialAttribute[String] = PartialAttribute("pattern")

  /** Returns a partial `placeholder` attribute (a hint shown in an empty form control). */
  final def placeholderAttr: PartialAttribute[String] = PartialAttribute("placeholder")

  /** Returns a partial `poster` attribute (the image shown before a video plays). */
  final def posterAttr: PartialAttribute[String] = PartialAttribute("poster")

  /** Returns a partial `preload` attribute (how much of the media to load in advance). */
  final def preloadAttr: PartialAttribute[String] = PartialAttribute("preload")

  /** Returns a partial `readonly` attribute (makes a form control read-only). It takes a string value: `readonlyAttr := "readonly"` renders `readonly="readonly"`. */
  final def readonlyAttr: PartialAttribute[String] = PartialAttribute("readonly")

  /** Returns a partial `rel` attribute (the relationship of the linked resource). */
  final def relAttr: PartialAttribute[String] = PartialAttribute("rel")

  /**
   * Returns a partial boolean `required` attribute, which marks a form control as required. Used as a child on its own, it renders as the bare name `required`. With a value, `requiredAttr := true` renders `required="true"` and `requiredAttr := false` renders `required="false"`.
   */
  final def requiredAttr: PartialBooleanAttribute = PartialBooleanAttribute("required")

  /** Returns a partial `reversed` attribute (numbers an ordered list in descending order). It takes a string value: `reversedAttr := "reversed"` renders `reversed="reversed"`. */
  final def reversedAttr: PartialAttribute[String] = PartialAttribute("reversed")

  /** Returns a partial `rowspan` attribute (the number of rows that a cell spans). */
  final def rowSpanAttr: PartialAttribute[String] = PartialAttribute("rowspan")

  /** Returns a partial `rows` attribute (the visible number of lines of a textarea). */
  final def rowsAttr: PartialAttribute[String] = PartialAttribute("rows")

  /** Returns a partial `sandbox` attribute (the restrictions on the content of an iframe). */
  final def sandboxAttr: PartialAttribute[String] = PartialAttribute("sandbox")

  /** Returns a partial `scope` attribute (the cells that a header cell applies to). */
  final def scopeAttr: PartialAttribute[String] = PartialAttribute("scope")

  /** Returns a partial `selected` attribute (marks an option as selected). It takes a string value: `selectedAttr := "selected"` renders `selected="selected"`. */
  final def selectedAttr: PartialAttribute[String] = PartialAttribute("selected")

  /** Returns a partial `shape` attribute (the shape of an image map area). */
  final def shapeAttr: PartialAttribute[String] = PartialAttribute("shape")

  /** Returns a partial `size` attribute (the size of the control). */
  final def sizeAttr: PartialAttribute[String] = PartialAttribute("size")

  /** Returns a partial `sizes` attribute (the image sizes for different page layouts). */
  final def sizesAttr: PartialAttribute[String] = PartialAttribute("sizes")

  /** Returns a partial `span` attribute (the number of columns that a col or colgroup element spans). */
  final def spanAttr: PartialAttribute[String] = PartialAttribute("span")

  /** Returns a partial `spellcheck` attribute (whether to check the spelling of the content). */
  final def spellcheckAttr: PartialAttribute[String] = PartialAttribute("spellcheck")

  /** Returns a partial `src` attribute (the URL of the embedded resource). */
  final def srcAttr: PartialAttribute[String] = PartialAttribute("src")

  /** Returns a partial `srcdoc` attribute (the HTML content of an iframe). */
  final def srcDocAttr: PartialAttribute[String] = PartialAttribute("srcdoc")

  /** Returns a partial `srclang` attribute (the language of a text track). */
  final def srcLangAttr: PartialAttribute[String] = PartialAttribute("srclang")

  /** Returns a partial `srcset` attribute (the image candidates for different screen sizes). */
  final def srcSetAttr: PartialAttribute[String] = PartialAttribute("srcset")

  /** Returns a partial `start` attribute (the first number of an ordered list). */
  final def startAttr: PartialAttribute[String] = PartialAttribute("start")

  /** Returns a partial `step` attribute (the step between allowed values). */
  final def stepAttr: PartialAttribute[String] = PartialAttribute("step")

  /** Returns a partial `style` attribute (the inline CSS of the element). */
  final def styleAttr: PartialAttribute[String] = PartialAttribute("style")

  /** Returns a partial `style` attribute (the inline CSS of the element); the same as `styleAttr`. */
  final def styles: PartialAttribute[String] = styleAttr

  /** Returns a partial `tabindex` attribute (the position of the element in the tab order). */
  final def tabIndexAttr: PartialAttribute[String] = PartialAttribute("tabindex")

  /** Returns a partial `target` attribute (where to open a link or form result). */
  final def targetAttr: PartialAttribute[String] = PartialAttribute("target")

  /** Returns a partial `title` attribute (advisory information, often shown as a tooltip). */
  final def titleAttr: PartialAttribute[String] = PartialAttribute("title")

  /** Returns a partial `translate` attribute (whether to translate the content). */
  final def translateAttr: PartialAttribute[String] = PartialAttribute("translate")

  /** Returns a partial `type` attribute (the type of the element, such as an input type). */
  final def typeAttr: PartialAttribute[String] = PartialAttribute("type")

  /** Returns a partial `usemap` attribute (the image map of an image). */
  final def useMapAttr: PartialAttribute[String] = PartialAttribute("usemap")

  /** Returns a partial `value` attribute (the value of the element). */
  final def valueAttr: PartialAttribute[String] = PartialAttribute("value")

  /** Returns a partial `width` attribute (the width of the element). */
  final def widthAttr: PartialAttribute[String] = PartialAttribute("width")

  /** Returns a partial `wrap` attribute (how a textarea wraps its value on submission). */
  final def wrapAttr: PartialAttribute[String] = PartialAttribute("wrap")

  /** Returns a partial `xmlns` attribute (the XML namespace of the element). */
  final def xmlnsAttr: PartialAttribute[String] = PartialAttribute("xmlns")

  /** Returns a partial `cellpadding` attribute (the padding of table cells). The HTML standard lists it as obsolete. */
  final def cellpaddingAttr: PartialAttribute[String] = PartialAttribute("cellpadding")

  /** Returns a partial `cellspacing` attribute (the spacing between table cells). The HTML standard lists it as obsolete. */
  final def cellspacingAttr: PartialAttribute[String] = PartialAttribute("cellspacing")

  /** Converts a partial boolean attribute used without a value, such as `requiredAttr`, into the attribute with the bare name. */
  implicit def partialBooleanToHtml(attr: PartialBooleanAttribute): Html = attr.apply()
}

/** Holds the partial attribute types that the helpers of [[Attributes]] return. */
object Attributes {

  /** A boolean attribute with the name `name` that has no value yet. */
  case class PartialBooleanAttribute(name: String) {

    /** Returns the attribute with `value`, rendered as `name="true"` or `name="false"`. */
    def :=(value: Boolean): Html    = Dom.booleanAttr(name, Some(value))

    /** Returns the attribute with `value`, rendered as `name="true"` or `name="false"`. */
    def apply(value: Boolean): Html = Dom.booleanAttr(name, Some(value))

    /** Returns the attribute without a value, rendered as the bare name. */
    def apply(): Html               = Dom.booleanAttr(name, None)
  }

  /**
   * An attribute with the name `name` that has no value yet. A value of type `A` needs an [[IsAttributeValue]] instance, which turns it into the attribute's string.
   */
  case class PartialAttribute[A](name: String) {

    /** Returns the attribute with `value`, turned into a string by `ev`. The string is escaped when the element renders. */
    def :=(value: A)(implicit ev: IsAttributeValue[A]): Html    = Dom.attr(name, ev(value))

    /** Returns the attribute with `value`, turned into a string by `ev`. The string is escaped when the element renders. */
    def apply(value: A)(implicit ev: IsAttributeValue[A]): Html = Dom.attr(name, ev(value))
  }
}
