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

import zio.http.template.Element.PartialElement

/**
 * The HTML element helpers, such as `div`, `p` and `a`. Each one returns an
 * [[Element.PartialElement]] that builds the element when it is applied to
 * children. The `zio.http.template` package object extends this trait, so
 * `import zio.http.template._` brings the helpers into scope.
 */
trait Elements {

  /**
   * Returns a partial `a` element (a hyperlink): apply it to children to get
   * the element as a [[Dom]].
   */
  final def a: PartialElement = PartialElement("a")

  /**
   * Returns a partial `abbr` element (an abbreviation): apply it to children to
   * get the element as a [[Dom]].
   */
  final def abbr: PartialElement = PartialElement("abbr")

  /**
   * Returns a partial `acronym` element (an acronym): apply it to children to
   * get the element as a [[Dom]]. The HTML standard lists it as obsolete.
   */
  final def acronym: PartialElement = PartialElement("acronym")

  /**
   * Returns a partial `address` element (contact information): apply it to
   * children to get the element as a [[Dom]].
   */
  final def address: PartialElement = PartialElement("address")

  /**
   * Returns a partial `applet` element (an embedded Java applet): apply it to
   * children to get the element as a [[Dom]]. The HTML standard lists it as
   * obsolete.
   */
  final def applet: PartialElement = PartialElement("applet")

  /**
   * Returns a partial `area` element (a clickable region of an image map):
   * apply it to children to get the element as a [[Dom]]. It is a void element:
   * with no child content it renders as one self-closing tag.
   */
  final def area: PartialElement = PartialElement("area")

  /**
   * Returns a partial `article` element (a self-contained composition): apply
   * it to children to get the element as a [[Dom]].
   */
  final def article: PartialElement = PartialElement("article")

  /**
   * Returns a partial `aside` element (content aside from the main content):
   * apply it to children to get the element as a [[Dom]].
   */
  final def aside: PartialElement = PartialElement("aside")

  /**
   * Returns a partial `audio` element (a sound or audio stream): apply it to
   * children to get the element as a [[Dom]].
   */
  final def audio: PartialElement = PartialElement("audio")

  /**
   * Returns a partial `b` element (text set apart, usually shown in bold):
   * apply it to children to get the element as a [[Dom]].
   */
  final def b: PartialElement = PartialElement("b")

  /**
   * Returns a partial `base` element (the base URL of the document): apply it
   * to children to get the element as a [[Dom]]. It is a void element: with no
   * child content it renders as one self-closing tag.
   */
  final def base: PartialElement = PartialElement("base")

  /**
   * Returns a partial `basefont` element (a default font): apply it to children
   * to get the element as a [[Dom]]. The HTML standard lists it as obsolete.
   */
  final def baseFont: PartialElement = PartialElement("basefont")

  /**
   * Returns a partial `bdi` element (text isolated for bidirectional
   * formatting): apply it to children to get the element as a [[Dom]].
   */
  final def bdi: PartialElement = PartialElement("bdi")

  /**
   * Returns a partial `bdo` element (text with an overridden direction): apply
   * it to children to get the element as a [[Dom]].
   */
  final def bdo: PartialElement = PartialElement("bdo")

  /**
   * Returns a partial `big` element (larger text): apply it to children to get
   * the element as a [[Dom]]. The HTML standard lists it as obsolete.
   */
  final def big: PartialElement = PartialElement("big")

  /**
   * Returns a partial `blockquote` element (a quotation from another source):
   * apply it to children to get the element as a [[Dom]].
   */
  final def blockquote: PartialElement = PartialElement("blockquote")

  /**
   * Returns a partial `body` element (the body of the document): apply it to
   * children to get the element as a [[Dom]].
   */
  final def body: PartialElement = PartialElement("body")

  /**
   * Returns a partial `br` element (a line break): apply it to children to get
   * the element as a [[Dom]]. It is a void element: with no child content it
   * renders as one self-closing tag.
   */
  final def br: PartialElement = PartialElement("br")

  /**
   * Returns a partial `button` element (a button): apply it to children to get
   * the element as a [[Dom]].
   */
  final def button: PartialElement = PartialElement("button")

  /**
   * Returns a partial `canvas` element (a drawing surface for scripts): apply
   * it to children to get the element as a [[Dom]].
   */
  final def canvas: PartialElement = PartialElement("canvas")

  /**
   * Returns a partial `caption` element (the caption of a table): apply it to
   * children to get the element as a [[Dom]].
   */
  final def caption: PartialElement = PartialElement("caption")

  /**
   * Returns a partial `center` element (centered content): apply it to children
   * to get the element as a [[Dom]]. The HTML standard lists it as obsolete.
   */
  final def center: PartialElement = PartialElement("center")

  /**
   * Returns a partial `cite` element (the title of a work): apply it to
   * children to get the element as a [[Dom]].
   */
  final def cite: PartialElement = PartialElement("cite")

  /**
   * Returns a partial `code` element (a fragment of computer code): apply it to
   * children to get the element as a [[Dom]].
   */
  final def code: PartialElement = PartialElement("code")

  /**
   * Returns a partial `col` element (a column in a table): apply it to children
   * to get the element as a [[Dom]]. It is a void element: with no child
   * content it renders as one self-closing tag.
   */
  final def col: PartialElement = PartialElement("col")

  /**
   * Returns a partial `colgroup` element (a group of columns in a table): apply
   * it to children to get the element as a [[Dom]].
   */
  final def colGroup: PartialElement = PartialElement("colgroup")

  /**
   * Returns a partial `data` element (content with a machine-readable value):
   * apply it to children to get the element as a [[Dom]].
   */
  final def data: PartialElement = PartialElement("data")

  /**
   * Returns a partial `datalist` element (a list of predefined options for an
   * input): apply it to children to get the element as a [[Dom]].
   */
  final def dataList: PartialElement = PartialElement("datalist")

  /**
   * Returns a partial `dd` element (the description in a description list):
   * apply it to children to get the element as a [[Dom]].
   */
  final def dd: PartialElement = PartialElement("dd")

  /**
   * Returns a partial `del` element (text removed from the document): apply it
   * to children to get the element as a [[Dom]].
   */
  final def del: PartialElement = PartialElement("del")

  /**
   * Returns a partial `details` element (a disclosure widget): apply it to
   * children to get the element as a [[Dom]].
   */
  final def details: PartialElement = PartialElement("details")

  /**
   * Returns a partial `dfn` element (the defining instance of a term): apply it
   * to children to get the element as a [[Dom]].
   */
  final def dfn: PartialElement = PartialElement("dfn")

  /**
   * Returns a partial `dialog` element (a dialog box): apply it to children to
   * get the element as a [[Dom]].
   */
  final def dialog: PartialElement = PartialElement("dialog")

  /**
   * Returns a partial `dir` element (a directory list): apply it to children to
   * get the element as a [[Dom]]. The HTML standard lists it as obsolete.
   */
  final def dir: PartialElement = PartialElement("dir")

  /**
   * Returns a partial `div` element (a generic container): apply it to children
   * to get the element as a [[Dom]]. Attribute nodes among the children, such
   * as `id := "main"`, become attributes of the element, in order. Strings
   * become escaped text, and other [[Html]] values, such as nested elements,
   * become its content, in order. Like the other element helpers, `div` is a
   * member of the `zio.http.template` package object.
   *
   * {{{
   * val card: Dom = div(id := "user-42", classAttr := "card", p("Ada Lovelace"), span("Mathematician"))
   *
   * card.encode
   * // <div id="user-42" class="card"><p>Ada Lovelace</p><span>Mathematician</span></div>
   * }}}
   *
   * @see
   *   [[https://ziohttp.com/reference/template/ Template DSL]]
   */
  final def div: PartialElement = PartialElement("div")

  /**
   * Returns a partial `dl` element (a description list): apply it to children
   * to get the element as a [[Dom]].
   */
  final def dl: PartialElement = PartialElement("dl")

  /**
   * Returns a partial `dt` element (the term in a description list): apply it
   * to children to get the element as a [[Dom]].
   */
  final def dt: PartialElement = PartialElement("dt")

  /**
   * Returns a partial `em` element (emphasized text): apply it to children to
   * get the element as a [[Dom]].
   */
  final def em: PartialElement = PartialElement("em")

  /**
   * Returns a partial `embed` element (an external resource or plugin): apply
   * it to children to get the element as a [[Dom]]. It is a void element: with
   * no child content it renders as one self-closing tag.
   */
  final def embed: PartialElement = PartialElement("embed")

  /**
   * Returns a partial `fieldset` element (a group of form controls): apply it
   * to children to get the element as a [[Dom]].
   */
  final def fieldSet: PartialElement = PartialElement("fieldset")

  /**
   * Returns a partial `figcaption` element (the caption of a figure): apply it
   * to children to get the element as a [[Dom]].
   */
  final def figCaption: PartialElement = PartialElement("figcaption")

  /**
   * Returns a partial `figure` element (a figure with an optional caption):
   * apply it to children to get the element as a [[Dom]].
   */
  final def figure: PartialElement = PartialElement("figure")

  /**
   * Returns a partial `font` element (text with font settings): apply it to
   * children to get the element as a [[Dom]]. The HTML standard lists it as
   * obsolete.
   */
  final def font: PartialElement = PartialElement("font")

  /**
   * Returns a partial `footer` element (the footer of a section or page): apply
   * it to children to get the element as a [[Dom]].
   */
  final def footer: PartialElement = PartialElement("footer")

  /**
   * Returns a partial `form` element (a form): apply it to children to get the
   * element as a [[Dom]].
   */
  final def form: PartialElement = PartialElement("form")

  /**
   * Returns a partial `frame` element (a frame in a frameset): apply it to
   * children to get the element as a [[Dom]]. The HTML standard lists it as
   * obsolete.
   */
  final def frame: PartialElement = PartialElement("frame")

  /**
   * Returns a partial `frameset` element (a set of frames): apply it to
   * children to get the element as a [[Dom]]. The HTML standard lists it as
   * obsolete.
   */
  final def frameSet: PartialElement = PartialElement("frameset")

  /**
   * Returns a partial `h1` element (a heading of level 1): apply it to children
   * to get the element as a [[Dom]].
   */
  final def h1: PartialElement = PartialElement("h1")

  /**
   * Returns a partial `h2` element (a heading of level 2): apply it to children
   * to get the element as a [[Dom]].
   */
  final def h2: PartialElement = PartialElement("h2")

  /**
   * Returns a partial `h3` element (a heading of level 3): apply it to children
   * to get the element as a [[Dom]].
   */
  final def h3: PartialElement = PartialElement("h3")

  /**
   * Returns a partial `h4` element (a heading of level 4): apply it to children
   * to get the element as a [[Dom]].
   */
  final def h4: PartialElement = PartialElement("h4")

  /**
   * Returns a partial `h5` element (a heading of level 5): apply it to children
   * to get the element as a [[Dom]].
   */
  final def h5: PartialElement = PartialElement("h5")

  /**
   * Returns a partial `h6` element (a heading of level 6): apply it to children
   * to get the element as a [[Dom]].
   */
  final def h6: PartialElement = PartialElement("h6")

  /**
   * Returns a partial `head` element (the metadata of the document): apply it
   * to children to get the element as a [[Dom]].
   */
  final def head: PartialElement = PartialElement("head")

  /**
   * Returns a partial `header` element (introductory content of a section or
   * page): apply it to children to get the element as a [[Dom]].
   */
  final def header: PartialElement = PartialElement("header")

  /**
   * Returns a partial `hr` element (a thematic break): apply it to children to
   * get the element as a [[Dom]]. It is a void element: with no child content
   * it renders as one self-closing tag.
   */
  final def hr: PartialElement = PartialElement("hr")

  /**
   * Returns a partial `html` element (the root of the document): apply it to
   * children to get the element as a [[Dom]].
   */
  final def html: PartialElement = PartialElement("html")

  /**
   * Returns a partial `i` element (text in an alternate voice, usually shown in
   * italics): apply it to children to get the element as a [[Dom]].
   */
  final def i: PartialElement = PartialElement("i")

  /**
   * Returns a partial `iframe` element (a nested browsing context): apply it to
   * children to get the element as a [[Dom]].
   */
  final def iframe: PartialElement = PartialElement("iframe")

  /**
   * Returns a partial `img` element (an image): apply it to children to get the
   * element as a [[Dom]]. It is a void element: with no child content it
   * renders as one self-closing tag.
   */
  final def img: PartialElement = PartialElement("img")

  /**
   * Returns a partial `input` element (a form input control): apply it to
   * children to get the element as a [[Dom]]. It is a void element: with no
   * child content it renders as one self-closing tag.
   */
  final def input: PartialElement = PartialElement("input")

  /**
   * Returns a partial `ins` element (text added to the document): apply it to
   * children to get the element as a [[Dom]].
   */
  final def ins: PartialElement = PartialElement("ins")

  /**
   * Returns a partial `kbd` element (user input, such as keys): apply it to
   * children to get the element as a [[Dom]].
   */
  final def kbd: PartialElement = PartialElement("kbd")

  /**
   * Returns a partial `label` element (the caption of a form control): apply it
   * to children to get the element as a [[Dom]].
   */
  final def label: PartialElement = PartialElement("label")

  /**
   * Returns a partial `legend` element (the caption of a fieldset): apply it to
   * children to get the element as a [[Dom]].
   */
  final def legend: PartialElement = PartialElement("legend")

  /**
   * Returns a partial `li` element (a list item): apply it to children to get
   * the element as a [[Dom]].
   */
  final def li: PartialElement = PartialElement("li")

  /**
   * Returns a partial `link` element (a link to an external resource, such as a
   * stylesheet): apply it to children to get the element as a [[Dom]]. It is a
   * void element: with no child content it renders as one self-closing tag.
   */
  final def link: PartialElement = PartialElement("link")

  /**
   * Returns a partial `main` element (the main content of the document): apply
   * it to children to get the element as a [[Dom]].
   */
  final def main: PartialElement = PartialElement("main")

  /**
   * Returns a partial `map` element (an image map): apply it to children to get
   * the element as a [[Dom]].
   */
  final def map: PartialElement = PartialElement("map")

  /**
   * Returns a partial `mark` element (highlighted text): apply it to children
   * to get the element as a [[Dom]].
   */
  final def mark: PartialElement = PartialElement("mark")

  /**
   * Returns a partial `meta` element (document metadata): apply it to children
   * to get the element as a [[Dom]]. It is a void element: with no child
   * content it renders as one self-closing tag.
   */
  final def meta: PartialElement = PartialElement("meta")

  /**
   * Returns a partial `meter` element (a scalar value within a known range):
   * apply it to children to get the element as a [[Dom]].
   */
  final def meter: PartialElement = PartialElement("meter")

  /**
   * Returns a partial `nav` element (a section of navigation links): apply it
   * to children to get the element as a [[Dom]].
   */
  final def nav: PartialElement = PartialElement("nav")

  /**
   * Returns a partial `noframes` element (content for browsers without frames):
   * apply it to children to get the element as a [[Dom]]. The HTML standard
   * lists it as obsolete.
   */
  final def noFrames: PartialElement = PartialElement("noframes")

  /**
   * Returns a partial `noscript` element (content for browsers without
   * scripting): apply it to children to get the element as a [[Dom]].
   */
  final def noScript: PartialElement = PartialElement("noscript")

  /**
   * Returns a partial `object` element (an external resource): apply it to
   * children to get the element as a [[Dom]].
   */
  final def obj: PartialElement = PartialElement("object")

  /**
   * Returns a partial `ol` element (an ordered list): apply it to children to
   * get the element as a [[Dom]].
   */
  final def ol: PartialElement = PartialElement("ol")

  /**
   * Returns a partial `optgroup` element (a group of options in a select
   * control): apply it to children to get the element as a [[Dom]].
   */
  final def optgroup: PartialElement = PartialElement("optgroup")

  /**
   * Returns a partial `option` element (an option in a select control or
   * datalist): apply it to children to get the element as a [[Dom]].
   */
  final def option: PartialElement = PartialElement("option")

  /**
   * Returns a partial `output` element (the result of a calculation): apply it
   * to children to get the element as a [[Dom]].
   */
  final def output: PartialElement = PartialElement("output")

  /**
   * Returns a partial `p` element (a paragraph): apply it to children to get
   * the element as a [[Dom]].
   */
  final def p: PartialElement = PartialElement("p")

  /**
   * Returns a partial `param` element (a parameter for an object element):
   * apply it to children to get the element as a [[Dom]]. It is a void element:
   * with no child content it renders as one self-closing tag. The HTML standard
   * lists it as obsolete.
   */
  final def param: PartialElement = PartialElement("param")

  /**
   * Returns a partial `picture` element (a container of image sources): apply
   * it to children to get the element as a [[Dom]].
   */
  final def picture: PartialElement = PartialElement("picture")

  /**
   * Returns a partial `pre` element (preformatted text): apply it to children
   * to get the element as a [[Dom]].
   */
  final def pre: PartialElement = PartialElement("pre")

  /**
   * Returns a partial `progress` element (the progress of a task): apply it to
   * children to get the element as a [[Dom]].
   */
  final def progress: PartialElement = PartialElement("progress")

  /**
   * Returns a partial `q` element (an inline quotation): apply it to children
   * to get the element as a [[Dom]].
   */
  final def q: PartialElement = PartialElement("q")

  /**
   * Returns a partial `rp` element (parentheses for browsers without ruby
   * support): apply it to children to get the element as a [[Dom]].
   */
  final def rp: PartialElement = PartialElement("rp")

  /**
   * Returns a partial `rt` element (the text of a ruby annotation): apply it to
   * children to get the element as a [[Dom]].
   */
  final def rt: PartialElement = PartialElement("rt")

  /**
   * Returns a partial `ruby` element (a ruby annotation): apply it to children
   * to get the element as a [[Dom]].
   */
  final def ruby: PartialElement = PartialElement("ruby")

  /**
   * Returns a partial `s` element (text that is no longer accurate): apply it
   * to children to get the element as a [[Dom]].
   */
  final def s: PartialElement = PartialElement("s")

  /**
   * Returns a partial `samp` element (sample output of a program): apply it to
   * children to get the element as a [[Dom]].
   */
  final def sample: PartialElement = PartialElement("samp")

  /**
   * Returns a partial `script` element (a script): apply it to children to get
   * the element as a [[Dom]]. Text inside it is not escaped when rendered.
   */
  final def script: PartialElement = PartialElement("script")

  /**
   * Returns a partial `section` element (a section of the document): apply it
   * to children to get the element as a [[Dom]].
   */
  final def section: PartialElement = PartialElement("section")

  /**
   * Returns a partial `select` element (a select control): apply it to children
   * to get the element as a [[Dom]].
   */
  final def select: PartialElement = PartialElement("select")

  /**
   * Returns a partial `small` element (side comments and small print): apply it
   * to children to get the element as a [[Dom]].
   */
  final def small: PartialElement = PartialElement("small")

  /**
   * Returns a partial `source` element (a media source for a picture, audio or
   * video element): apply it to children to get the element as a [[Dom]]. It is
   * a void element: with no child content it renders as one self-closing tag.
   */
  final def source: PartialElement = PartialElement("source")

  /**
   * Returns a partial `span` element (a generic inline container): apply it to
   * children to get the element as a [[Dom]].
   */
  final def span: PartialElement = PartialElement("span")

  /**
   * Returns a partial `strike` element (struck-through text): apply it to
   * children to get the element as a [[Dom]]. The HTML standard lists it as
   * obsolete.
   */
  final def strike: PartialElement = PartialElement("strike")

  /**
   * Returns a partial `strong` element (text of strong importance): apply it to
   * children to get the element as a [[Dom]].
   */
  final def strong: PartialElement = PartialElement("strong")

  /**
   * Returns a partial `style` element (style information, such as CSS): apply
   * it to children to get the element as a [[Dom]]. Text inside it is not
   * escaped when rendered.
   */
  final def style: PartialElement = PartialElement("style")

  /**
   * Returns a partial `sub` element (subscript text): apply it to children to
   * get the element as a [[Dom]].
   */
  final def sub: PartialElement = PartialElement("sub")

  /**
   * Returns a partial `summary` element (the summary of a details element):
   * apply it to children to get the element as a [[Dom]].
   */
  final def summary: PartialElement = PartialElement("summary")

  /**
   * Returns a partial `sup` element (superscript text): apply it to children to
   * get the element as a [[Dom]].
   */
  final def sup: PartialElement = PartialElement("sup")

  /**
   * Returns a partial `svg` element (an SVG graphic): apply it to children to
   * get the element as a [[Dom]].
   */
  final def svg: PartialElement = PartialElement("svg")

  /**
   * Returns a partial `tbody` element (the body rows of a table): apply it to
   * children to get the element as a [[Dom]].
   */
  final def tBody: PartialElement = PartialElement("tbody")

  /**
   * Returns a partial `tfoot` element (the footer rows of a table): apply it to
   * children to get the element as a [[Dom]].
   */
  final def tFoot: PartialElement = PartialElement("tfoot")

  /**
   * Returns a partial `thead` element (the header rows of a table): apply it to
   * children to get the element as a [[Dom]].
   */
  final def tHead: PartialElement = PartialElement("thead")

  /**
   * Returns a partial `table` element (a table): apply it to children to get
   * the element as a [[Dom]].
   */
  final def table: PartialElement = PartialElement("table")

  /**
   * Returns a partial `td` element (a table data cell): apply it to children to
   * get the element as a [[Dom]].
   */
  final def td: PartialElement = PartialElement("td")

  /**
   * Returns a partial `template` element (an HTML fragment for scripts to
   * clone): apply it to children to get the element as a [[Dom]].
   */
  final def template: PartialElement = PartialElement("template")

  /**
   * Returns a partial `textarea` element (a multi-line text input): apply it to
   * children to get the element as a [[Dom]].
   */
  final def textarea: PartialElement = PartialElement("textarea")

  /**
   * Returns a partial `th` element (a table header cell): apply it to children
   * to get the element as a [[Dom]].
   */
  final def th: PartialElement = PartialElement("th")

  /**
   * Returns a partial `time` element (a date or time): apply it to children to
   * get the element as a [[Dom]].
   */
  final def time: PartialElement = PartialElement("time")

  /**
   * Returns a partial `title` element (the title of the document): apply it to
   * children to get the element as a [[Dom]].
   */
  final def title: PartialElement = PartialElement("title")

  /**
   * Returns a partial `tr` element (a table row): apply it to children to get
   * the element as a [[Dom]].
   */
  final def tr: PartialElement = PartialElement("tr")

  /**
   * Returns a partial `track` element (a timed text track for audio or video):
   * apply it to children to get the element as a [[Dom]]. It is a void element:
   * with no child content it renders as one self-closing tag.
   */
  final def track: PartialElement = PartialElement("track")

  /**
   * Returns a partial `tt` element (teletype text): apply it to children to get
   * the element as a [[Dom]]. The HTML standard lists it as obsolete.
   */
  final def tt: PartialElement = PartialElement("tt")

  /**
   * Returns a partial `u` element (text with an unarticulated annotation,
   * usually shown underlined): apply it to children to get the element as a
   * [[Dom]].
   */
  final def u: PartialElement = PartialElement("u")

  /**
   * Returns a partial `ul` element (an unordered list): apply it to children to
   * get the element as a [[Dom]].
   */
  final def ul: PartialElement = PartialElement("ul")

  /**
   * Returns a partial `var` element (a variable): apply it to children to get
   * the element as a [[Dom]].
   */
  final def variable: PartialElement = PartialElement("var")

  /**
   * Returns a partial `video` element (a video): apply it to children to get
   * the element as a [[Dom]].
   */
  final def video: PartialElement = PartialElement("video")

  /**
   * Returns a partial `wbr` element (a line break opportunity): apply it to
   * children to get the element as a [[Dom]]. It is a void element: with no
   * child content it renders as one self-closing tag.
   */
  final def wbr: PartialElement = PartialElement("wbr")

}

/**
 * Holds [[Element.PartialElement]], the type that the element helpers of
 * [[Elements]] return.
 */
object Element {
  private[zio] val voidElementNames: Set[CharSequence] =
    Set(area, base, br, col, embed, hr, img, input, link, meta, param, source, track, wbr).map(_.name)

  private[zio] def isVoid(name: CharSequence): Boolean = voidElementNames.contains(name)

  /**
   * An element with the tag `name` that has no children yet. Apply it to
   * [[Html]] children to build the element.
   */
  case class PartialElement(name: CharSequence) {

    /**
     * Builds the element with these children. The nodes of each child become
     * the element's children, in order: attribute nodes become its attributes,
     * and element, text and raw nodes become its content. Empty children, such
     * as `()` or `None`, add nothing.
     */
    def apply(children: Html*): Dom = Dom.element(
      name,
      children.collect {
        case Html.Single(element)    => Seq(element)
        case Html.Multiple(children) => children
      }.flatten: _*,
    )

  }

}
