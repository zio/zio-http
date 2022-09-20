package zio.http.html

import zio.http.html.Attributes.PartialAttribute
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait Attributes {
  final def acceptAttr: PartialAttribute[String] = PartialAttribute("accept")

  final def acceptCharsetAttr: PartialAttribute[String] = PartialAttribute("accept-charset")

  final def accessKeyAttr: PartialAttribute[String] = PartialAttribute("accesskey")

  final def actionAttr: PartialAttribute[String] = PartialAttribute("action")

  final def alignAttr: PartialAttribute[String] = PartialAttribute("align")

  final def altAttr: PartialAttribute[String] = PartialAttribute("alt")

  final def asyncAttr: PartialAttribute[String] = PartialAttribute("async")

  final def autocompleteAttr: PartialAttribute[String] = PartialAttribute("autocomplete")

  final def autofocusAttr: PartialAttribute[String] = PartialAttribute("autofocus")

  final def autoplayAttr: PartialAttribute[String] = PartialAttribute("autoplay")

  final def bgColorAttr: PartialAttribute[String] = PartialAttribute("bgcolor")

  final def borderAttr: PartialAttribute[String] = PartialAttribute("border")

  final def charsetAttr: PartialAttribute[String] = PartialAttribute("charset")

  final def checkedAttr: PartialAttribute[String] = PartialAttribute("checked")

  final def citeAttr: PartialAttribute[String] = PartialAttribute("cite")

  final def classAttr: PartialAttribute[List[String]] = PartialAttribute("class")

  final def colSpanAttr: PartialAttribute[String] = PartialAttribute("colspan")

  final def colorAttr: PartialAttribute[String] = PartialAttribute("color")

  final def colsAttr: PartialAttribute[String] = PartialAttribute("cols")

  final def contentAttr: PartialAttribute[String] = PartialAttribute("content")

  final def contentEditableAttr: PartialAttribute[String] = PartialAttribute("contenteditable")

  final def controlsAttr: PartialAttribute[String] = PartialAttribute("controls")

  final def coordsAttr: PartialAttribute[String] = PartialAttribute("coords")

  final def css: PartialAttribute[List[String]] = classAttr

  final def dataAttr(name: String): PartialAttribute[String] = PartialAttribute("data-" + name)

  final def datetimeAttr: PartialAttribute[String] = PartialAttribute("datetime")

  final def defaultAttr: PartialAttribute[String] = PartialAttribute("default")

  final def deferAttr: PartialAttribute[String] = PartialAttribute("defer")

  final def dirAttr: PartialAttribute[String] = PartialAttribute("dir")

  final def dirNameAttr: PartialAttribute[String] = PartialAttribute("dirname")

  final def disabledAttr: PartialAttribute[String] = PartialAttribute("disabled")

  final def downloadAttr: PartialAttribute[String] = PartialAttribute("download")

  final def draggableAttr: PartialAttribute[String] = PartialAttribute("draggable")

  final def enctypeAttr: PartialAttribute[String] = PartialAttribute("enctype")

  final def forAttr: PartialAttribute[String] = PartialAttribute("for")

  final def formActionAttr: PartialAttribute[String] = PartialAttribute("formaction")

  final def formAttr: PartialAttribute[String] = PartialAttribute("form")

  final def headersAttr: PartialAttribute[String] = PartialAttribute("headers")

  final def heightAttr: PartialAttribute[String] = PartialAttribute("height")

  final def hiddenAttr: PartialAttribute[String] = PartialAttribute("hidden")

  final def highAttr: PartialAttribute[String] = PartialAttribute("high")

  final def href: PartialAttribute[String] = PartialAttribute("href")

  final def hrefAttr: PartialAttribute[String] = PartialAttribute("href")

  final def hrefLangAttr: PartialAttribute[String] = PartialAttribute("hreflang")

  final def httpEquivAttr: PartialAttribute[String] = PartialAttribute("http-equiv")

  final def id: PartialAttribute[String] = PartialAttribute("id")

  final def idAttr: PartialAttribute[String] = PartialAttribute("id")

  final def isMapAttr: PartialAttribute[String] = PartialAttribute("ismap")

  final def kindAttr: PartialAttribute[String] = PartialAttribute("kind")

  final def labelAttr: PartialAttribute[String] = PartialAttribute("label")

  final def langAttr: PartialAttribute[String] = PartialAttribute("lang")

  final def listAttr: PartialAttribute[String] = PartialAttribute("list")

  final def loopAttr: PartialAttribute[String] = PartialAttribute("loop")

  final def lowAttr: PartialAttribute[String] = PartialAttribute("low")

  final def maxAttr: PartialAttribute[String] = PartialAttribute("max")

  final def maxLengthAttr: PartialAttribute[String] = PartialAttribute("maxlength")

  final def mediaAttr: PartialAttribute[String] = PartialAttribute("media")

  final def methodAttr: PartialAttribute[String] = PartialAttribute("method")

  final def minAttr: PartialAttribute[String] = PartialAttribute("min")

  final def multipleAttr: PartialAttribute[String] = PartialAttribute("multiple")

  final def mutedAttr: PartialAttribute[String] = PartialAttribute("muted")

  final def nameAttr: PartialAttribute[String] = PartialAttribute("name")

  final def noValidateAttr: PartialAttribute[String] = PartialAttribute("novalidate")

  final def onAbortAttr: PartialAttribute[String] = PartialAttribute("onabort")

  final def onAfterPrintAttr: PartialAttribute[String] = PartialAttribute("onafterprint")

  final def onBeforePrintAttr: PartialAttribute[String] = PartialAttribute("onbeforeprint")

  final def onBeforeUnloadAttr: PartialAttribute[String] = PartialAttribute("onbeforeunload")

  final def onBlurAttr: PartialAttribute[String] = PartialAttribute("onblur")

  final def onCanPlayAttr: PartialAttribute[String] = PartialAttribute("oncanplay")

  final def onCanPlayThroughAttr: PartialAttribute[String] = PartialAttribute("oncanplaythrough")

  final def onChangeAttr: PartialAttribute[String] = PartialAttribute("onchange")

  final def onClickAttr: PartialAttribute[String] = PartialAttribute("onclick")

  final def onContextMenuAttr: PartialAttribute[String] = PartialAttribute("oncontextmenu")

  final def onCopyAttr: PartialAttribute[String] = PartialAttribute("oncopy")

  final def onCueChangeAttr: PartialAttribute[String] = PartialAttribute("oncuechange")

  final def onCutAttr: PartialAttribute[String] = PartialAttribute("oncut")

  final def onDblClickAttr: PartialAttribute[String] = PartialAttribute("ondblclick")

  final def onDragAttr: PartialAttribute[String] = PartialAttribute("ondrag")

  final def onDragEndAttr: PartialAttribute[String] = PartialAttribute("ondragend")

  final def onDragEnterAttr: PartialAttribute[String] = PartialAttribute("ondragenter")

  final def onDragLeaveAttr: PartialAttribute[String] = PartialAttribute("ondragleave")

  final def onDragOverAttr: PartialAttribute[String] = PartialAttribute("ondragover")

  final def onDragStartAttr: PartialAttribute[String] = PartialAttribute("ondragstart")

  final def onDropAttr: PartialAttribute[String] = PartialAttribute("ondrop")

  final def onDurationChangeAttr: PartialAttribute[String] = PartialAttribute("ondurationchange")

  final def onEmptiedAttr: PartialAttribute[String] = PartialAttribute("onemptied")

  final def onEndedAttr: PartialAttribute[String] = PartialAttribute("onended")

  final def onErrorAttr: PartialAttribute[String] = PartialAttribute("onerror")

  final def onFocusAttr: PartialAttribute[String] = PartialAttribute("onfocus")

  final def onHashChangeAttr: PartialAttribute[String] = PartialAttribute("onhashchange")

  final def onInputAttr: PartialAttribute[String] = PartialAttribute("oninput")

  final def onInvalidAttr: PartialAttribute[String] = PartialAttribute("oninvalid")

  final def onKeyDownAttr: PartialAttribute[String] = PartialAttribute("onkeydown")

  final def onKeyPressAttr: PartialAttribute[String] = PartialAttribute("onkeypress")

  final def onKeyUpAttr: PartialAttribute[String] = PartialAttribute("onkeyup")

  final def onLoadAttr: PartialAttribute[String] = PartialAttribute("onload")

  final def onLoadStartAttr: PartialAttribute[String] = PartialAttribute("onloadstart")

  final def onLoadedDataAttr: PartialAttribute[String] = PartialAttribute("onloadeddata")

  final def onLoadedMetadataAttr: PartialAttribute[String] = PartialAttribute("onloadedmetadata")

  final def onMouseDownAttr: PartialAttribute[String] = PartialAttribute("onmousedown")

  final def onMouseMoveAttr: PartialAttribute[String] = PartialAttribute("onmousemove")

  final def onMouseOutAttr: PartialAttribute[String] = PartialAttribute("onmouseout")

  final def onMouseOverAttr: PartialAttribute[String] = PartialAttribute("onmouseover")

  final def onMouseUpAttr: PartialAttribute[String] = PartialAttribute("onmouseup")

  final def onMouseWheelAttr: PartialAttribute[String] = PartialAttribute("onmousewheel")

  final def onOfflineAttr: PartialAttribute[String] = PartialAttribute("onoffline")

  final def onOnlineAttr: PartialAttribute[String] = PartialAttribute("ononline")

  final def onPageHideAttr: PartialAttribute[String] = PartialAttribute("onpagehide")

  final def onPageShowAttr: PartialAttribute[String] = PartialAttribute("onpageshow")

  final def onPasteAttr: PartialAttribute[String] = PartialAttribute("onpaste")

  final def onPauseAttr: PartialAttribute[String] = PartialAttribute("onpause")

  final def onPlayAttr: PartialAttribute[String] = PartialAttribute("onplay")

  final def onPlayingAttr: PartialAttribute[String] = PartialAttribute("onplaying")

  final def onPopStateAttr: PartialAttribute[String] = PartialAttribute("onpopstate")

  final def onProgressAttr: PartialAttribute[String] = PartialAttribute("onprogress")

  final def onRateChangeAttr: PartialAttribute[String] = PartialAttribute("onratechange")

  final def onResetAttr: PartialAttribute[String] = PartialAttribute("onreset")

  final def onResizeAttr: PartialAttribute[String] = PartialAttribute("onresize")

  final def onScrollAttr: PartialAttribute[String] = PartialAttribute("onscroll")

  final def onSearchAttr: PartialAttribute[String] = PartialAttribute("onsearch")

  final def onSeekedAttr: PartialAttribute[String] = PartialAttribute("onseeked")

  final def onSeekingAttr: PartialAttribute[String] = PartialAttribute("onseeking")

  final def onSelectAttr: PartialAttribute[String] = PartialAttribute("onselect")

  final def onStalledAttr: PartialAttribute[String] = PartialAttribute("onstalled")

  final def onStorageAttr: PartialAttribute[String] = PartialAttribute("onstorage")

  final def onSubmitAttr: PartialAttribute[String] = PartialAttribute("onsubmit")

  final def onSuspendAttr: PartialAttribute[String] = PartialAttribute("onsuspend")

  final def onTimeUpdateAttr: PartialAttribute[String] = PartialAttribute("ontimeupdate")

  final def onToggleAttr: PartialAttribute[String] = PartialAttribute("ontoggle")

  final def onUnloadAttr: PartialAttribute[String] = PartialAttribute("onunload")

  final def onVolumeChangeAttr: PartialAttribute[String] = PartialAttribute("onvolumechange")

  final def onWaitingAttr: PartialAttribute[String] = PartialAttribute("onwaiting")

  final def onWheelAttr: PartialAttribute[String] = PartialAttribute("onwheel")

  final def openAttr: PartialAttribute[String] = PartialAttribute("open")

  final def optimumAttr: PartialAttribute[String] = PartialAttribute("optimum")

  final def patternAttr: PartialAttribute[String] = PartialAttribute("pattern")

  final def placeholderAttr: PartialAttribute[String] = PartialAttribute("placeholder")

  final def posterAttr: PartialAttribute[String] = PartialAttribute("poster")

  final def preloadAttr: PartialAttribute[String] = PartialAttribute("preload")

  final def readonlyAttr: PartialAttribute[String] = PartialAttribute("readonly")

  final def relAttr: PartialAttribute[String] = PartialAttribute("rel")

  final def requiredAttr: PartialAttribute[String] = PartialAttribute("required")

  final def reversedAttr: PartialAttribute[String] = PartialAttribute("reversed")

  final def rowSpanAttr: PartialAttribute[String] = PartialAttribute("rowspan")

  final def rowsAttr: PartialAttribute[String] = PartialAttribute("rows")

  final def sandboxAttr: PartialAttribute[String] = PartialAttribute("sandbox")

  final def scopeAttr: PartialAttribute[String] = PartialAttribute("scope")

  final def selectedAttr: PartialAttribute[String] = PartialAttribute("selected")

  final def shapeAttr: PartialAttribute[String] = PartialAttribute("shape")

  final def sizeAttr: PartialAttribute[String] = PartialAttribute("size")

  final def sizesAttr: PartialAttribute[String] = PartialAttribute("sizes")

  final def spanAttr: PartialAttribute[String] = PartialAttribute("span")

  final def spellcheckAttr: PartialAttribute[String] = PartialAttribute("spellcheck")

  final def srcAttr: PartialAttribute[String] = PartialAttribute("src")

  final def srcDocAttr: PartialAttribute[String] = PartialAttribute("srcdoc")

  final def srcLangAttr: PartialAttribute[String] = PartialAttribute("srclang")

  final def srcSetAttr: PartialAttribute[String] = PartialAttribute("srcset")

  final def startAttr: PartialAttribute[String] = PartialAttribute("start")

  final def stepAttr: PartialAttribute[String] = PartialAttribute("step")

  final def styleAttr: PartialAttribute[Seq[(String, String)]] = PartialAttribute("style")

  final def styles: PartialAttribute[Seq[(String, String)]] = styleAttr

  final def tabIndexAttr: PartialAttribute[String] = PartialAttribute("tabindex")

  final def targetAttr: PartialAttribute[String] = PartialAttribute("target")

  final def titleAttr: PartialAttribute[String] = PartialAttribute("title")

  final def translateAttr: PartialAttribute[String] = PartialAttribute("translate")

  final def typeAttr: PartialAttribute[String] = PartialAttribute("type")

  final def useMapAttr: PartialAttribute[String] = PartialAttribute("usemap")

  final def valueAttr: PartialAttribute[String] = PartialAttribute("value")

  final def widthAttr: PartialAttribute[String] = PartialAttribute("width")

  final def wrapAttr: PartialAttribute[String] = PartialAttribute("wrap")

}

object Attributes {
  case class PartialAttribute[A](name: String) {
    def :=(value: A)(implicit ev: IsAttributeValue[A]): Html    = Dom.attr(name, ev(value))
    def apply(value: A)(implicit ev: IsAttributeValue[A]): Html = Dom.attr(name, ev(value))
  }
}
