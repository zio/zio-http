package zio.http.html

import zio.http.html.Element.PartialElement
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait Elements {
  final def a: PartialElement = PartialElement("a")

  final def abbr: PartialElement = PartialElement("abbr")

  final def acronym: PartialElement = PartialElement("acronym")

  final def address: PartialElement = PartialElement("address")

  final def applet: PartialElement = PartialElement("applet")

  final def area: PartialElement = PartialElement("area")

  final def article: PartialElement = PartialElement("article")

  final def aside: PartialElement = PartialElement("aside")

  final def audio: PartialElement = PartialElement("audio")

  final def b: PartialElement = PartialElement("b")

  final def base: PartialElement = PartialElement("base")

  final def baseFont: PartialElement = PartialElement("basefont")

  final def bdi: PartialElement = PartialElement("bdi")

  final def bdo: PartialElement = PartialElement("bdo")

  final def big: PartialElement = PartialElement("big")

  final def blockquote: PartialElement = PartialElement("blockquote")

  final def body: PartialElement = PartialElement("body")

  final def br: PartialElement = PartialElement("br")

  final def button: PartialElement = PartialElement("button")

  final def canvas: PartialElement = PartialElement("canvas")

  final def caption: PartialElement = PartialElement("caption")

  final def center: PartialElement = PartialElement("center")

  final def cite: PartialElement = PartialElement("cite")

  final def code: PartialElement = PartialElement("code")

  final def col: PartialElement = PartialElement("col")

  final def colGroup: PartialElement = PartialElement("colgroup")

  final def data: PartialElement = PartialElement("data")

  final def dataList: PartialElement = PartialElement("datalist")

  final def dd: PartialElement = PartialElement("dd")

  final def del: PartialElement = PartialElement("del")

  final def details: PartialElement = PartialElement("details")

  final def dfn: PartialElement = PartialElement("dfn")

  final def dialog: PartialElement = PartialElement("dialog")

  final def dir: PartialElement = PartialElement("dir")

  final def div: PartialElement = PartialElement("div")

  final def dl: PartialElement = PartialElement("dl")

  final def dt: PartialElement = PartialElement("dt")

  final def em: PartialElement = PartialElement("em")

  final def embed: PartialElement = PartialElement("embed")

  final def fieldSet: PartialElement = PartialElement("fieldset")

  final def figCaption: PartialElement = PartialElement("figcaption")

  final def figure: PartialElement = PartialElement("figure")

  final def font: PartialElement = PartialElement("font")

  final def footer: PartialElement = PartialElement("footer")

  final def form: PartialElement = PartialElement("form")

  final def frame: PartialElement = PartialElement("frame")

  final def frameSet: PartialElement = PartialElement("frameset")

  final def h1: PartialElement = PartialElement("h1")

  final def h2: PartialElement = PartialElement("h2")

  final def h3: PartialElement = PartialElement("h3")

  final def h4: PartialElement = PartialElement("h4")

  final def h5: PartialElement = PartialElement("h5")

  final def h6: PartialElement = PartialElement("h6")

  final def head: PartialElement = PartialElement("head")

  final def header: PartialElement = PartialElement("header")

  final def hr: PartialElement = PartialElement("hr")

  final def html: PartialElement = PartialElement("html")

  final def i: PartialElement = PartialElement("i")

  final def iframe: PartialElement = PartialElement("iframe")

  final def img: PartialElement = PartialElement("img")

  final def input: PartialElement = PartialElement("input")

  final def ins: PartialElement = PartialElement("ins")

  final def kbd: PartialElement = PartialElement("kbd")

  final def label: PartialElement = PartialElement("label")

  final def legend: PartialElement = PartialElement("legend")

  final def li: PartialElement = PartialElement("li")

  final def link: PartialElement = PartialElement("link")

  final def main: PartialElement = PartialElement("main")

  final def map: PartialElement = PartialElement("map")

  final def mark: PartialElement = PartialElement("mark")

  final def meta: PartialElement = PartialElement("meta")

  final def meter: PartialElement = PartialElement("meter")

  final def nav: PartialElement = PartialElement("nav")

  final def noFrames: PartialElement = PartialElement("noframes")

  final def noScript: PartialElement = PartialElement("noscript")

  final def obj: PartialElement = PartialElement("object")

  final def ol: PartialElement = PartialElement("ol")

  final def optgroup: PartialElement = PartialElement("optgroup")

  final def option: PartialElement = PartialElement("option")

  final def output: PartialElement = PartialElement("output")

  final def p: PartialElement = PartialElement("p")

  final def param: PartialElement = PartialElement("param")

  final def picture: PartialElement = PartialElement("picture")

  final def pre: PartialElement = PartialElement("pre")

  final def progress: PartialElement = PartialElement("progress")

  final def q: PartialElement = PartialElement("q")

  final def rp: PartialElement = PartialElement("rp")

  final def rt: PartialElement = PartialElement("rt")

  final def ruby: PartialElement = PartialElement("ruby")

  final def s: PartialElement = PartialElement("s")

  final def sample: PartialElement = PartialElement("samp")

  final def script: PartialElement = PartialElement("script")

  final def section: PartialElement = PartialElement("section")

  final def select: PartialElement = PartialElement("select")

  final def small: PartialElement = PartialElement("small")

  final def source: PartialElement = PartialElement("source")

  final def span: PartialElement = PartialElement("span")

  final def strike: PartialElement = PartialElement("strike")

  final def strong: PartialElement = PartialElement("strong")

  final def style: PartialElement = PartialElement("style")

  final def sub: PartialElement = PartialElement("sub")

  final def summary: PartialElement = PartialElement("summary")

  final def sup: PartialElement = PartialElement("sup")

  final def svg: PartialElement = PartialElement("svg")

  final def tBody: PartialElement = PartialElement("tbody")

  final def tFoot: PartialElement = PartialElement("tfoot")

  final def tHead: PartialElement = PartialElement("thead")

  final def table: PartialElement = PartialElement("table")

  final def td: PartialElement = PartialElement("td")

  final def template: PartialElement = PartialElement("template")

  final def textarea: PartialElement = PartialElement("textarea")

  final def th: PartialElement = PartialElement("th")

  final def time: PartialElement = PartialElement("time")

  final def title: PartialElement = PartialElement("title")

  final def tr: PartialElement = PartialElement("tr")

  final def track: PartialElement = PartialElement("track")

  final def tt: PartialElement = PartialElement("tt")

  final def u: PartialElement = PartialElement("u")

  final def ul: PartialElement = PartialElement("ul")

  final def variable: PartialElement = PartialElement("var")

  final def video: PartialElement = PartialElement("video")

  final def wbr: PartialElement = PartialElement("wbr")

}

object Element {
  private[zio] val voidElementNames: Set[CharSequence] =
    Set(area, base, br, col, embed, hr, img, input, link, meta, param, source, track, wbr).map(_.name)

  private[zio] def isVoid(name: CharSequence): Boolean = voidElementNames.contains(name)

  case class PartialElement(name: CharSequence) {
    def apply(children: Html*): Dom = Dom.element(
      name,
      children.collect {
        case Html.Single(element)    => Seq(element)
        case Html.Multiple(children) => children
      }.flatten: _*,
    )
  }
}
