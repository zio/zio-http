package zhttp.html

trait HtmlAttributeMaker[A] {
  final def accept: A = make("accept")

  final def acceptCharset: A = make("accept-charset")

  final def accessKey: A = make("accesskey")

  final def action: A = make("action")

  final def align: A = make("align")

  final def alt: A = make("alt")

  final def async: A = make("async")

  final def autocomplete: A = make("autocomplete")

  final def autofocus: A = make("autofocus")

  final def autoplay: A = make("autoplay")

  final def bgColor: A = make("bgcolor")

  final def border: A = make("border")

  final def charset: A = make("charset")

  final def checked: A = make("checked")

  final def cite: A = make("cite")

  final def className: A = make("class")

  final def colSpan: A = make("colspan")

  final def color: A = make("color")

  final def cols: A = make("cols")

  final def content: A = make("content")

  final def contentEditable: A = make("contenteditable")

  final def controls: A = make("controls")

  final def coords: A = make("coords")

  final def data(name: String): A = make("data-" + name)

  final def datetime: A = make("datetime")

  final def default: A = make("default")

  final def defer: A = make("defer")

  final def dir: A = make("dir")

  final def dirName: A = make("dirname")

  final def disabled: A = make("disabled")

  final def download: A = make("download")

  final def draggable: A = make("draggable")

  final def enctype: A = make("enctype")

  final def `for`: A = make("for")

  final def form: A = make("form")

  final def formAction: A = make("formaction")

  final def headers: A = make("headers")

  final def height: A = make("height")

  final def hidden: A = make("hidden")

  final def high: A = make("high")

  final def href: A = make("href")

  final def hrefLang: A = make("hreflang")

  final def httpEquiv: A = make("http-equiv")

  final def id: A = make("id")

  final def isMap: A = make("ismap")

  final def kind: A = make("kind")

  final def label: A = make("label")

  final def lang: A = make("lang")

  final def list: A = make("list")

  final def loop: A = make("loop")

  final def low: A = make("low")

  final def max: A = make("max")

  final def maxLength: A = make("maxlength")

  final def media: A = make("media")

  final def method: A = make("method")

  final def min: A = make("min")

  final def multiple: A = make("multiple")

  final def muted: A = make("muted")

  final def name: A = make("name")

  final def novalidate: A = make("novalidate")

  final def onAbort: A = make("onabort")

  final def onAfterPrint: A = make("onafterprint")

  final def onBeforePrint: A = make("onbeforeprint")

  final def onBeforeUnload: A = make("onbeforeunload")

  final def onBlur: A = make("onblur")

  final def onCanPlay: A = make("oncanplay")

  final def onCanPlayThrough: A = make("oncanplaythrough")

  final def onChange: A = make("onchange")

  final def onClick: A = make("onclick")

  final def onContextMenu: A = make("oncontextmenu")

  final def onCopy: A = make("oncopy")

  final def onCueChange: A = make("oncuechange")

  final def onCut: A = make("oncut")

  final def onDblClick: A = make("ondblclick")

  final def onDrag: A = make("ondrag")

  final def onDragEnd: A = make("ondragend")

  final def onDragEnter: A = make("ondragenter")

  final def onDragLeave: A = make("ondragleave")

  final def onDragOver: A = make("ondragover")

  final def onDragStart: A = make("ondragstart")

  final def onDrop: A = make("ondrop")

  final def onDurationChange: A = make("ondurationchange")

  final def onEmptied: A = make("onemptied")

  final def onEnded: A = make("onended")

  final def onError: A = make("onerror")

  final def onFocus: A = make("onfocus")

  final def onHashChange: A = make("onhashchange")

  final def onInput: A = make("oninput")

  final def onInvalid: A = make("oninvalid")

  final def onKeyDown: A = make("onkeydown")

  final def onKeyPress: A = make("onkeypress")

  final def onKeyUp: A = make("onkeyup")

  final def onLoad: A = make("onload")

  final def onLoadStart: A = make("onloadstart")

  final def onLoadedData: A = make("onloadeddata")

  final def onLoadedMetadata: A = make("onloadedmetadata")

  final def onMouseDown: A = make("onmousedown")

  final def onMouseMove: A = make("onmousemove")

  final def onMouseOut: A = make("onmouseout")

  final def onMouseOver: A = make("onmouseover")

  final def onMouseUp: A = make("onmouseup")

  final def onMouseWheel: A = make("onmousewheel")

  final def onOffline: A = make("onoffline")

  final def onOnline: A = make("ononline")

  final def onPageHide: A = make("onpagehide")

  final def onPageShow: A = make("onpageshow")

  final def onPaste: A = make("onpaste")

  final def onPause: A = make("onpause")

  final def onPlay: A = make("onplay")

  final def onPlaying: A = make("onplaying")

  final def onPopState: A = make("onpopstate")

  final def onProgress: A = make("onprogress")

  final def onRateChange: A = make("onratechange")

  final def onReset: A = make("onreset")

  final def onResize: A = make("onresize")

  final def onScroll: A = make("onscroll")

  final def onSearch: A = make("onsearch")

  final def onSeeked: A = make("onseeked")

  final def onSeeking: A = make("onseeking")

  final def onSelect: A = make("onselect")

  final def onStalled: A = make("onstalled")

  final def onStorage: A = make("onstorage")

  final def onSubmit: A = make("onsubmit")

  final def onSuspend: A = make("onsuspend")

  final def onTimeUpdate: A = make("ontimeupdate")

  final def onToggle: A = make("ontoggle")

  final def onUnload: A = make("onunload")

  final def onVolumeChange: A = make("onvolumechange")

  final def onWaiting: A = make("onwaiting")

  final def onWheel: A = make("onwheel")

  final def open: A = make("open")

  final def optimum: A = make("optimum")

  final def pattern: A = make("pattern")

  final def placeholder: A = make("placeholder")

  final def poster: A = make("poster")

  final def preload: A = make("preload")

  final def readonly: A = make("readonly")

  final def rel: A = make("rel")

  final def required: A = make("required")

  final def reversed: A = make("reversed")

  final def rowSpan: A = make("rowspan")

  final def rows: A = make("rows")

  final def sandbox: A = make("sandbox")

  final def scope: A = make("scope")

  final def selected: A = make("selected")

  final def shape: A = make("shape")

  final def size: A = make("size")

  final def sizes: A = make("sizes")

  final def span: A = make("span")

  final def spellcheck: A = make("spellcheck")

  final def src: A = make("src")

  final def srcDoc: A = make("srcdoc")

  final def srcLang: A = make("srclang")

  final def srcSet: A = make("srcset")

  final def start: A = make("start")

  final def step: A = make("step")

  final def style: A = make("style")

  final def tabIndex: A = make("tabindex")

  final def target: A = make("target")

  final def title: A = make("title")

  final def translate: A = make("translate")

  final def `type`: A = make("type")

  final def useMap: A = make("usemap")

  final def value: A = make("value")

  final def width: A = make("width")

  final def wrap: A = make("wrap")

  private[zhttp] def make(name: String): A
}
