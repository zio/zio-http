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

package zio.http.template2

/**
 * Trait containing all HTML element definitions. This trait can be mixed into
 * package objects or other contexts. Elements are sorted alphabetically for
 * easy maintenance.
 */
trait HtmlElements {

  /** Returns an element with the tag `<a>` (a hyperlink (anchor)). Call it with attributes and children to fill it. */
  val a: Dom.Element             = Dom.element("a")
  /** Returns an element with the tag `<abbr>` (an abbreviation). Call it with attributes and children to fill it. */
  val abbr: Dom.Element          = Dom.element("abbr")
  /** Returns an element with the tag `<address>` (contact information). Call it with attributes and children to fill it. */
  val address: Dom.Element       = Dom.element("address")
  /** Returns an element with the tag `<area>` (an image map area). This is a void element: it renders as `<area/>`, with its attributes if any, and renders no children. */
  val area: Dom.Element          = Dom.element("area")
  /** Returns an element with the tag `<article>` (a self-contained article). Call it with attributes and children to fill it. */
  val article: Dom.Element       = Dom.element("article")
  /** Returns an element with the tag `<aside>` (content aside from the main content). Call it with attributes and children to fill it. */
  val aside: Dom.Element         = Dom.element("aside")
  /** Returns an element with the tag `<audio>` (an audio player). Call it with attributes and children to fill it. */
  val audio: Dom.Element         = Dom.element("audio")
  /** Returns an element with the tag `<b>` (bold text). Call it with attributes and children to fill it. */
  val b: Dom.Element             = Dom.element("b")
  /** Returns an element with the tag `<base>` (the base URL of the document). This is a void element: it renders as `<base/>`, with its attributes if any, and renders no children. */
  val base: Dom.Element          = Dom.element("base")
  /** Returns an element with the tag `<bdi>` (text isolated for bidirectional formatting). Call it with attributes and children to fill it. */
  val bdi: Dom.Element           = Dom.element("bdi")
  /** Returns an element with the tag `<bdo>` (text with an overridden text direction). Call it with attributes and children to fill it. */
  val bdo: Dom.Element           = Dom.element("bdo")
  /** Returns an element with the tag `<blockquote>` (a block quotation). Call it with attributes and children to fill it. */
  val blockquote: Dom.Element    = Dom.element("blockquote")
  /** Returns an element with the tag `<body>` (the document body). Call it with attributes and children to fill it. */
  val body: Dom.Element          = Dom.element("body")
  /** Returns an element with the tag `<br>` (a line break). This is a void element: it renders as `<br/>`, with its attributes if any, and renders no children. */
  val br: Dom.Element            = Dom.element("br")
  /** Returns an element with the tag `<button>` (a button). Call it with attributes and children to fill it. */
  val button: Dom.Element        = Dom.element("button")
  /** Returns an element with the tag `<canvas>` (a drawing canvas). Call it with attributes and children to fill it. */
  val canvas: Dom.Element        = Dom.element("canvas")
  /** Returns an element with the tag `<caption>` (a table caption). Call it with attributes and children to fill it. */
  val caption: Dom.Element       = Dom.element("caption")
  /** Returns an element with the tag `<cite>` (the title of a cited work). Call it with attributes and children to fill it. */
  val cite: Dom.Element          = Dom.element("cite")
  /** Returns an element with the tag `<code>` (a fragment of code). Call it with attributes and children to fill it. */
  val code: Dom.Element          = Dom.element("code")
  /** Returns an element with the tag `<col>` (a table column). This is a void element: it renders as `<col/>`, with its attributes if any, and renders no children. */
  val col: Dom.Element           = Dom.element("col")
  /** Returns an element with the tag `<colgroup>` (a group of table columns). Call it with attributes and children to fill it. */
  val colgroup: Dom.Element      = Dom.element("colgroup")
  /** Returns an element with the tag `<datalist>` (a list of predefined options for an input). Call it with attributes and children to fill it. */
  val datalist: Dom.Element      = Dom.element("datalist")
  /** Returns an element with the tag `<dd>` (a description in a description list). Call it with attributes and children to fill it. */
  val dd: Dom.Element            = Dom.element("dd")
  /** Returns an element with the tag `<del>` (deleted text). Call it with attributes and children to fill it. */
  val del: Dom.Element           = Dom.element("del")
  /** Returns an element with the tag `<details>` (a disclosure widget). Call it with attributes and children to fill it. */
  val details: Dom.Element       = Dom.element("details")
  /** Returns an element with the tag `<dfn>` (the defining instance of a term). Call it with attributes and children to fill it. */
  val dfn: Dom.Element           = Dom.element("dfn")
  /** Returns an element with the tag `<dialog>` (a dialog box). Call it with attributes and children to fill it. */
  val dialog: Dom.Element        = Dom.element("dialog")
  /**
   * Returns a `<div>` element, a generic block container. Call it with
   * attributes and children to fill it: strings become text children, and
   * attributes such as `id` and `class` become attributes of the tag. Calling
   * `render` writes the element as an HTML string.
   *
   * {{{
   * val card = div(`class` := ("card", "wide"), id := "main", p("Hello"), "text")
   * println(card.render)
   * // <div class="card wide" id="main"><p>Hello</p>text</div>
   * }}}
   *
   * @see
   *   [[https://ziohttp.com/reference/template/ Template DSL]]
   */
  val div: Dom.Element           = Dom.element("div")
  /** Returns an element with the tag `<dl>` (a description list). Call it with attributes and children to fill it. */
  val dl: Dom.Element            = Dom.element("dl")
  /** Returns an element with the tag `<dt>` (a term in a description list). Call it with attributes and children to fill it. */
  val dt: Dom.Element            = Dom.element("dt")
  /** Returns an element with the tag `<em>` (emphasized text). Call it with attributes and children to fill it. */
  val em: Dom.Element            = Dom.element("em")
  /** Returns an element with the tag `<embed>` (external content embedded in the page). This is a void element: it renders as `<embed/>`, with its attributes if any, and renders no children. */
  val embed: Dom.Element         = Dom.element("embed")
  /** Returns an element with the tag `<fieldset>` (a group of form controls). Call it with attributes and children to fill it. */
  val fieldset: Dom.Element      = Dom.element("fieldset")
  /** Returns an element with the tag `<footer>` (a footer). Call it with attributes and children to fill it. */
  val footer: Dom.Element        = Dom.element("footer")
  /** Returns an element with the tag `<form>` (a form). Call it with attributes and children to fill it. */
  val form: Dom.Element          = Dom.element("form")
  /** Returns an element with the tag `h1` (a level 1 heading). Call it with attributes and children to fill it. */
  val h1: Dom.Element            = Dom.element("h1")
  /** Returns an element with the tag `h2` (a level 2 heading). Call it with attributes and children to fill it. */
  val h2: Dom.Element            = Dom.element("h2")
  /** Returns an element with the tag `h3` (a level 3 heading). Call it with attributes and children to fill it. */
  val h3: Dom.Element            = Dom.element("h3")
  /** Returns an element with the tag `h4` (a level 4 heading). Call it with attributes and children to fill it. */
  val h4: Dom.Element            = Dom.element("h4")
  /** Returns an element with the tag `h5` (a level 5 heading). Call it with attributes and children to fill it. */
  val h5: Dom.Element            = Dom.element("h5")
  /** Returns an element with the tag `h6` (a level 6 heading). Call it with attributes and children to fill it. */
  val h6: Dom.Element            = Dom.element("h6")
  /** Returns an element with the tag `<head>` (the document head). Call it with attributes and children to fill it. */
  val head: Dom.Element          = Dom.element("head")
  /** Returns an element with the tag `<header>` (a header). Call it with attributes and children to fill it. */
  val header: Dom.Element        = Dom.element("header")
  /** Returns an element with the tag `<hr>` (a thematic break (horizontal rule)). This is a void element: it renders as `<hr/>`, with its attributes if any, and renders no children. */
  val hr: Dom.Element            = Dom.element("hr")
  /** Returns an element with the tag `<html>` (the root element of a document). Call it with attributes and children to fill it. */
  val html: Dom.Element          = Dom.element("html")
  /** Returns an element with the tag `<i>` (italic text). Call it with attributes and children to fill it. */
  val i: Dom.Element             = Dom.element("i")
  /** Returns an element with the tag `<iframe>` (an inline frame). Call it with attributes and children to fill it. */
  val iframe: Dom.Element        = Dom.element("iframe")
  /** Returns an element with the tag `<img>` (an image). This is a void element: it renders as `<img/>`, with its attributes if any, and renders no children. */
  val img: Dom.Element           = Dom.element("img")
  /** Returns an element with the tag `<input>` (a form input). This is a void element: it renders as `<input/>`, with its attributes if any, and renders no children. */
  val input: Dom.Element         = Dom.element("input")
  /** Returns an element with the tag `<ins>` (inserted text). Call it with attributes and children to fill it. */
  val ins: Dom.Element           = Dom.element("ins")
  /** Returns an element with the tag `<kbd>` (keyboard input). Call it with attributes and children to fill it. */
  val kbd: Dom.Element           = Dom.element("kbd")
  /** Returns an element with the tag `<label>` (a label for a form control). Call it with attributes and children to fill it. */
  val label: Dom.Element         = Dom.element("label")
  /** Returns an element with the tag `<legend>` (a caption for a fieldset). Call it with attributes and children to fill it. */
  val legend: Dom.Element        = Dom.element("legend")
  /** Returns an element with the tag `<li>` (a list item). Call it with attributes and children to fill it. */
  val li: Dom.Element            = Dom.element("li")
  /** Returns an element with the tag `<link>` (a link to an external resource). This is a void element: it renders as `<link/>`, with its attributes if any, and renders no children. */
  val link: Dom.Element          = Dom.element("link")
  /** Returns an element with the tag `<main>` (the main content of the document). Call it with attributes and children to fill it. */
  val main: Dom.Element          = Dom.element("main")
  /** Returns an element with the tag `<map>` (an image map). Call it with attributes and children to fill it. */
  val map: Dom.Element           = Dom.element("map")
  /** Returns an element with the tag `<mark>` (highlighted text). Call it with attributes and children to fill it. */
  val mark: Dom.Element          = Dom.element("mark")
  /** Returns an element with the tag `<math>` (a MathML container). Call it with attributes and children to fill it. */
  val math: Dom.Element          = Dom.element("math")
  /** Returns an element with the tag `<meta>` (document metadata). This is a void element: it renders as `<meta/>`, with its attributes if any, and renders no children. */
  val meta: Dom.Element          = Dom.element("meta")
  /** Returns an element with the tag `<meter>` (a scalar measurement within a range). Call it with attributes and children to fill it. */
  val meter: Dom.Element         = Dom.element("meter")
  /** Returns an element with the tag `<nav>` (a navigation section). Call it with attributes and children to fill it. */
  val nav: Dom.Element           = Dom.element("nav")
  /** Returns an element with the tag `<object>` (an embedded external resource). Call it with attributes and children to fill it. */
  val `object`: Dom.Element      = Dom.element("object")
  /** Returns an element with the tag `<ol>` (an ordered list). Call it with attributes and children to fill it. */
  val ol: Dom.Element            = Dom.element("ol")
  /** Returns an element with the tag `<optgroup>` (a group of options in a select). Call it with attributes and children to fill it. */
  val optgroup: Dom.Element      = Dom.element("optgroup")
  /** Returns an element with the tag `<option>` (an option in a select or datalist). Call it with attributes and children to fill it. */
  val option: Dom.Element        = Dom.element("option")
  /** Returns an element with the tag `<output>` (the result of a calculation). Call it with attributes and children to fill it. */
  val output: Dom.Element        = Dom.element("output")
  /** Returns an element with the tag `<p>` (a paragraph). Call it with attributes and children to fill it. */
  val p: Dom.Element             = Dom.element("p")
  /** Returns an element with the tag `<param>` (a parameter for an object element). This is a void element: it renders as `<param/>`, with its attributes if any, and renders no children. */
  val param: Dom.Element         = Dom.element("param")
  /** Returns an element with the tag `<pre>` (preformatted text). Call it with attributes and children to fill it. */
  val pre: Dom.Element           = Dom.element("pre")
  /** Returns an element with the tag `<progress>` (a progress indicator). Call it with attributes and children to fill it. */
  val progress: Dom.Element      = Dom.element("progress")
  /** Returns an element with the tag `<q>` (an inline quotation). Call it with attributes and children to fill it. */
  val q: Dom.Element             = Dom.element("q")
  /** Returns an element with the tag `<rp>` (fallback parentheses for ruby annotations). Call it with attributes and children to fill it. */
  val rp: Dom.Element            = Dom.element("rp")
  /** Returns an element with the tag `<rt>` (a ruby text annotation). Call it with attributes and children to fill it. */
  val rt: Dom.Element            = Dom.element("rt")
  /** Returns an element with the tag `<ruby>` (a ruby annotation). Call it with attributes and children to fill it. */
  val ruby: Dom.Element          = Dom.element("ruby")
  /** Returns an element with the tag `<s>` (text with a strikethrough). Call it with attributes and children to fill it. */
  val s: Dom.Element             = Dom.element("s")
  /** Returns an element with the tag `<samp>` (sample output from a program). Call it with attributes and children to fill it. */
  val samp: Dom.Element          = Dom.element("samp")
  /** Returns a `<script>` element ([[Dom.Element.Script]]). Besides modifiers, calling it accepts a [[Js]] value whose text is added as a child. */
  val script: Dom.Element.Script = Dom.script
  /** Returns an element with the tag `<section>` (a section of a document). Call it with attributes and children to fill it. */
  val section: Dom.Element       = Dom.element("section")
  /** Returns an element with the tag `<select>` (a drop-down or list selection control). Call it with attributes and children to fill it. */
  val select: Dom.Element        = Dom.element("select")
  /** Returns an element with the tag `<slot>` (a placeholder inside a web component). Call it with attributes and children to fill it. */
  val slot: Dom.Element          = Dom.element("slot")
  /** Returns an element with the tag `<small>` (side-comment text). Call it with attributes and children to fill it. */
  val small: Dom.Element         = Dom.element("small")
  /** Returns an element with the tag `<source>` (a media resource for audio, video or picture). This is a void element: it renders as `<source/>`, with its attributes if any, and renders no children. */
  val source: Dom.Element        = Dom.element("source")
  /** Returns an element with the tag `<span>` (a generic inline container). Call it with attributes and children to fill it. */
  val span: Dom.Element          = Dom.element("span")
  /** Returns an element with the tag `<strong>` (text of strong importance). Call it with attributes and children to fill it. */
  val strong: Dom.Element        = Dom.element("strong")
  /** Returns a `<style>` element ([[Dom.Element.Style]]). Besides modifiers, calling it accepts a [[Css]] value whose text is added as a child. */
  val style: Dom.Element.Style   = Dom.style
  /** Returns an element with the tag `<sub>` (subscript text). Call it with attributes and children to fill it. */
  val sub: Dom.Element           = Dom.element("sub")
  /** Returns an element with the tag `<summary>` (the summary of a details element). Call it with attributes and children to fill it. */
  val summary: Dom.Element       = Dom.element("summary")
  /** Returns an element with the tag `<sup>` (superscript text). Call it with attributes and children to fill it. */
  val sup: Dom.Element           = Dom.element("sup")
  /** Returns an element with the tag `<svg>` (an SVG container). Call it with attributes and children to fill it. */
  val svg: Dom.Element           = Dom.element("svg")
  /** Returns an element with the tag `<table>` (a table). Call it with attributes and children to fill it. */
  val table: Dom.Element         = Dom.element("table")
  /** Returns an element with the tag `<tbody>` (a table body). Call it with attributes and children to fill it. */
  val tbody: Dom.Element         = Dom.element("tbody")
  /** Returns an element with the tag `<td>` (a table data cell). Call it with attributes and children to fill it. */
  val td: Dom.Element            = Dom.element("td")
  /** Returns an element with the tag `<template>` (a content template). Call it with attributes and children to fill it. */
  val template: Dom.Element      = Dom.element("template")
  /** Returns an element with the tag `<textarea>` (a multi-line text input). Call it with attributes and children to fill it. */
  val textarea: Dom.Element      = Dom.element("textarea")
  /** Returns an element with the tag `<tfoot>` (a table footer). Call it with attributes and children to fill it. */
  val tfoot: Dom.Element         = Dom.element("tfoot")
  /** Returns an element with the tag `<th>` (a table header cell). Call it with attributes and children to fill it. */
  val th: Dom.Element            = Dom.element("th")
  /** Returns an element with the tag `<thead>` (a table header). Call it with attributes and children to fill it. */
  val thead: Dom.Element         = Dom.element("thead")
  /** Returns an element with the tag `<time>` (a date or time). Call it with attributes and children to fill it. */
  val time: Dom.Element          = Dom.element("time")
  /** Returns an element with the tag `<title>` (the document title). Call it with attributes and children to fill it. */
  val title: Dom.Element         = Dom.element("title")
  /** Returns an element with the tag `<tr>` (a table row). Call it with attributes and children to fill it. */
  val tr: Dom.Element            = Dom.element("tr")
  /** Returns an element with the tag `<track>` (a text track for audio or video). This is a void element: it renders as `<track/>`, with its attributes if any, and renders no children. */
  val track: Dom.Element         = Dom.element("track")
  /** Returns an element with the tag `<u>` (underlined text). Call it with attributes and children to fill it. */
  val u: Dom.Element             = Dom.element("u")
  /** Returns an element with the tag `<ul>` (an unordered list). Call it with attributes and children to fill it. */
  val ul: Dom.Element            = Dom.element("ul")
  /** Returns an element with the tag `<var>` (a variable). Call it with attributes and children to fill it. */
  val `var`: Dom.Element         = Dom.element("var")
  /** Returns an element with the tag `<video>` (a video player). Call it with attributes and children to fill it. */
  val video: Dom.Element         = Dom.element("video")
  /** Returns an element with the tag `<wbr>` (a possible line-break position). This is a void element: it renders as `<wbr/>`, with its attributes if any, and renders no children. */
  val wbr: Dom.Element           = Dom.element("wbr")

  /** Returns a [[Dom.Text]] node for `content`. Rendering escapes the HTML special characters in the text. */
  def text(content: String): Dom.Text        = Dom.text(content)
  /** Returns a [[Dom.RawHtml]] node for `content`. Rendering writes the string as it is, without escaping it. */
  def raw(content: String): Dom.RawHtml      = Dom.raw(content)
  /** A [[Dom]] that renders as an empty string. */
  val empty: Dom                             = Dom.empty
  /** Returns a [[Dom.Fragment]] of `children`. Rendering writes the children one after another, without a wrapping element. */
  def fragment(children: Dom*): Dom.Fragment = Dom.fragment(children: _*)
}

/**
 * Trait containing all HTML attribute definitions. This trait can be mixed into
 * package objects or other contexts. Attributes are sorted alphabetically for
 * easy maintenance.
 */
trait HtmlAttributes {

  /** Returns the `accept` attribute. Give it a value with `:=` or by calling it, for example `accept := "..."`. */
  val accept: Dom.PartialAttribute               = Dom.attr("accept")
  /** Returns the `accesskey` attribute. Give it a value with `:=` or by calling it, for example `accesskey := "..."`. */
  val accesskey: Dom.PartialAttribute            = Dom.attr("accesskey")
  /** Returns the `action` attribute. Give it a value with `:=` or by calling it, for example `action := "..."`. */
  val action: Dom.PartialAttribute               = Dom.attr("action")
  /** Returns the `alt` attribute. Give it a value with `:=` or by calling it, for example `alt := "..."`. */
  val alt: Dom.PartialAttribute                  = Dom.attr("alt")
  /** Returns the `aria-activedescendant` attribute. Give it a value with `:=` or by calling it, for example `ariaActivedescendant := "..."`. */
  val ariaActivedescendant: Dom.PartialAttribute = Dom.attr("aria-activedescendant")
  /** Returns the `aria-autocomplete` attribute. Give it a value with `:=` or by calling it, for example `ariaAutocomplete := "..."`. */
  val ariaAutocomplete: Dom.PartialAttribute     = Dom.attr("aria-autocomplete")
  /** Returns the `aria-checked` attribute. Give it a value with `:=` or by calling it, for example `ariaChecked := "..."`. */
  val ariaChecked: Dom.PartialAttribute          = Dom.attr("aria-checked")
  /** Returns the `aria-colcount` attribute. Give it a value with `:=` or by calling it, for example `ariaColcount := "..."`. */
  val ariaColcount: Dom.PartialAttribute         = Dom.attr("aria-colcount")
  /** Returns the `aria-colindex` attribute. Give it a value with `:=` or by calling it, for example `ariaColindex := "..."`. */
  val ariaColindex: Dom.PartialAttribute         = Dom.attr("aria-colindex")
  /** Returns the `aria-colspan` attribute. Give it a value with `:=` or by calling it, for example `ariaColspan := "..."`. */
  val ariaColspan: Dom.PartialAttribute          = Dom.attr("aria-colspan")
  /** Returns the `aria-controls` attribute, built from one or more values, which render joined by a space, for example `ariaControls := ("a", "b")`. */
  val ariaControls: Dom.PartialMultiAttribute    = Dom.multiAttr("aria-controls")
  /** Returns the `aria-describedby` attribute, built from one or more values, which render joined by a space, for example `ariaDescribedby := ("a", "b")`. */
  val ariaDescribedby: Dom.PartialMultiAttribute = Dom.multiAttr("aria-describedby")
  /** Returns the `aria-disabled` attribute. Give it a value with `:=` or by calling it, for example `ariaDisabled := "..."`. */
  val ariaDisabled: Dom.PartialAttribute         = Dom.attr("aria-disabled")
  /** Returns the `aria-dropeffect` attribute. Give it a value with `:=` or by calling it, for example `ariaDropeffect := "..."`. */
  val ariaDropeffect: Dom.PartialAttribute       = Dom.attr("aria-dropeffect")
  /** Returns the `aria-expanded` attribute. Give it a value with `:=` or by calling it, for example `ariaExpanded := "..."`. */
  val ariaExpanded: Dom.PartialAttribute         = Dom.attr("aria-expanded")
  /** Returns the `aria-flowto` attribute, built from one or more values, which render joined by a space, for example `ariaFlowto := ("a", "b")`. */
  val ariaFlowto: Dom.PartialMultiAttribute      = Dom.multiAttr("aria-flowto")
  /** Returns the `aria-grabbed` attribute. Give it a value with `:=` or by calling it, for example `ariaGrabbed := "..."`. */
  val ariaGrabbed: Dom.PartialAttribute          = Dom.attr("aria-grabbed")
  /** Returns the `aria-haspopup` attribute. Give it a value with `:=` or by calling it, for example `ariaHaspopup := "..."`. */
  val ariaHaspopup: Dom.PartialAttribute         = Dom.attr("aria-haspopup")
  /** Returns the `aria-hidden` attribute. Give it a value with `:=` or by calling it, for example `ariaHidden := "..."`. */
  val ariaHidden: Dom.PartialAttribute           = Dom.attr("aria-hidden")
  /** Returns the `aria-invalid` attribute. Give it a value with `:=` or by calling it, for example `ariaInvalid := "..."`. */
  val ariaInvalid: Dom.PartialAttribute          = Dom.attr("aria-invalid")
  /** Returns the `aria-label` attribute. Give it a value with `:=` or by calling it, for example `ariaLabel := "..."`. */
  val ariaLabel: Dom.PartialAttribute            = Dom.attr("aria-label")
  /** Returns the `aria-labelledby` attribute, built from one or more values, which render joined by a space, for example `ariaLabelledby := ("a", "b")`. */
  val ariaLabelledby: Dom.PartialMultiAttribute  = Dom.multiAttr("aria-labelledby")
  /** Returns the `aria-level` attribute. Give it a value with `:=` or by calling it, for example `ariaLevel := "..."`. */
  val ariaLevel: Dom.PartialAttribute            = Dom.attr("aria-level")
  /** Returns the `aria-live` attribute. Give it a value with `:=` or by calling it, for example `ariaLive := "..."`. */
  val ariaLive: Dom.PartialAttribute             = Dom.attr("aria-live")
  /** Returns the `aria-multiline` attribute. Give it a value with `:=` or by calling it, for example `ariaMultiline := "..."`. */
  val ariaMultiline: Dom.PartialAttribute        = Dom.attr("aria-multiline")
  /** Returns the `aria-multiselectable` attribute. Give it a value with `:=` or by calling it, for example `ariaMultiselectable := "..."`. */
  val ariaMultiselectable: Dom.PartialAttribute  = Dom.attr("aria-multiselectable")
  /** Returns the `aria-orientation` attribute. Give it a value with `:=` or by calling it, for example `ariaOrientation := "..."`. */
  val ariaOrientation: Dom.PartialAttribute      = Dom.attr("aria-orientation")
  /** Returns the `aria-owns` attribute, built from one or more values, which render joined by a space, for example `ariaOwns := ("a", "b")`. */
  val ariaOwns: Dom.PartialMultiAttribute        = Dom.multiAttr("aria-owns")
  /** Returns the `aria-posinset` attribute. Give it a value with `:=` or by calling it, for example `ariaPosinset := "..."`. */
  val ariaPosinset: Dom.PartialAttribute         = Dom.attr("aria-posinset")
  /** Returns the `aria-pressed` attribute. Give it a value with `:=` or by calling it, for example `ariaPressed := "..."`. */
  val ariaPressed: Dom.PartialAttribute          = Dom.attr("aria-pressed")
  /** Returns the `aria-readonly` attribute. Give it a value with `:=` or by calling it, for example `ariaReadonly := "..."`. */
  val ariaReadonly: Dom.PartialAttribute         = Dom.attr("aria-readonly")
  /** Returns the `aria-required` attribute. Give it a value with `:=` or by calling it, for example `ariaRequired := "..."`. */
  val ariaRequired: Dom.PartialAttribute         = Dom.attr("aria-required")
  /** Returns the `aria-rowcount` attribute. Give it a value with `:=` or by calling it, for example `ariaRowcount := "..."`. */
  val ariaRowcount: Dom.PartialAttribute         = Dom.attr("aria-rowcount")
  /** Returns the `aria-rowindex` attribute. Give it a value with `:=` or by calling it, for example `ariaRowindex := "..."`. */
  val ariaRowindex: Dom.PartialAttribute         = Dom.attr("aria-rowindex")
  /** Returns the `aria-rowspan` attribute. Give it a value with `:=` or by calling it, for example `ariaRowspan := "..."`. */
  val ariaRowspan: Dom.PartialAttribute          = Dom.attr("aria-rowspan")
  /** Returns the `aria-selected` attribute. Give it a value with `:=` or by calling it, for example `ariaSelected := "..."`. */
  val ariaSelected: Dom.PartialAttribute         = Dom.attr("aria-selected")
  /** Returns the `aria-setsize` attribute. Give it a value with `:=` or by calling it, for example `ariaSetsize := "..."`. */
  val ariaSetsize: Dom.PartialAttribute          = Dom.attr("aria-setsize")
  /** Returns the `aria-sort` attribute. Give it a value with `:=` or by calling it, for example `ariaSort := "..."`. */
  val ariaSort: Dom.PartialAttribute             = Dom.attr("aria-sort")
  /** Returns the `aria-valuemax` attribute. Give it a value with `:=` or by calling it, for example `ariaValuemax := "..."`. */
  val ariaValuemax: Dom.PartialAttribute         = Dom.attr("aria-valuemax")
  /** Returns the `aria-valuemin` attribute. Give it a value with `:=` or by calling it, for example `ariaValuemin := "..."`. */
  val ariaValuemin: Dom.PartialAttribute         = Dom.attr("aria-valuemin")
  /** Returns the `aria-valuenow` attribute. Give it a value with `:=` or by calling it, for example `ariaValuenow := "..."`. */
  val ariaValuenow: Dom.PartialAttribute         = Dom.attr("aria-valuenow")
  /** Returns the `aria-valuetext` attribute. Give it a value with `:=` or by calling it, for example `ariaValuetext := "..."`. */
  val ariaValuetext: Dom.PartialAttribute        = Dom.attr("aria-valuetext")
  /** A boolean `async` attribute. It renders as the bare name `async` when enabled, and renders nothing when disabled. */
  val async: Dom.BooleanAttribute                = Dom.boolAttr("async")
  /** Returns the `autocomplete` attribute. Give it a value with `:=` or by calling it, for example `autocomplete := "..."`. */
  val autocomplete: Dom.PartialAttribute         = Dom.attr("autocomplete")
  /** A boolean `autofocus` attribute. It renders as the bare name `autofocus` when enabled, and renders nothing when disabled. */
  val autofocus: Dom.BooleanAttribute            = Dom.boolAttr("autofocus")
  /** Returns the `autoplay` attribute. Give it a value with `:=` or by calling it, for example `autoplay := "..."`. */
  val autoplay: Dom.PartialAttribute             = Dom.attr("autoplay")
  /** Returns the `charset` attribute. Give it a value with `:=` or by calling it, for example `charset := "..."`. */
  val charset: Dom.PartialAttribute              = Dom.attr("charset")
  /** A boolean `checked` attribute. It renders as the bare name `checked` when enabled, and renders nothing when disabled. */
  val checked: Dom.BooleanAttribute              = Dom.boolAttr("checked")
  /** Returns the `class` attribute, built from one or more values, which render joined by a space, for example ``class` := ("a", "b")`. */
  val `class`: Dom.PartialMultiAttribute         = Dom.multiAttr("class")
  /** Returns the `class` attribute, built from one or more values, which render joined by a space, for example `className := ("a", "b")`. */
  val className: Dom.PartialMultiAttribute       = Dom.multiAttr("class")
  /** Returns the `cols` attribute. Give it a value with `:=` or by calling it, for example `cols := "..."`. */
  val cols: Dom.PartialAttribute                 = Dom.attr("cols")
  /** Returns the `content` attribute. Give it a value with `:=` or by calling it, for example `content := "..."`. */
  val content: Dom.PartialAttribute              = Dom.attr("content")
  /** Returns the `contenteditable` attribute. Give it a value with `:=` or by calling it, for example `contenteditable := "..."`. */
  val contenteditable: Dom.PartialAttribute      = Dom.attr("contenteditable")
  /** Returns the `controls` attribute. Give it a value with `:=` or by calling it, for example `controls := "..."`. */
  val controls: Dom.PartialAttribute             = Dom.attr("controls")
  /** A boolean `defer` attribute. It renders as the bare name `defer` when enabled, and renders nothing when disabled. */
  val defer: Dom.BooleanAttribute                = Dom.boolAttr("defer")
  /** Returns the `dir` attribute. Give it a value with `:=` or by calling it, for example `dir := "..."`. */
  val dir: Dom.PartialAttribute                  = Dom.attr("dir")
  /** A boolean `disabled` attribute. It renders as the bare name `disabled` when enabled, and renders nothing when disabled. */
  val disabled: Dom.BooleanAttribute             = Dom.boolAttr("disabled")
  /** Returns the `draggable` attribute. Give it a value with `:=` or by calling it, for example `draggable := "..."`. */
  val draggable: Dom.PartialAttribute            = Dom.attr("draggable")
  /** Returns the `enctype` attribute. Give it a value with `:=` or by calling it, for example `enctype := "..."`. */
  val enctype: Dom.PartialAttribute              = Dom.attr("enctype")
  /** Returns the `for` attribute. Give it a value with `:=` or by calling it, for example ``for` := "..."`. */
  val `for`: Dom.PartialAttribute                = Dom.attr("for")
  /** A boolean `formnovalidate` attribute. It renders as the bare name `formnovalidate` when enabled, and renders nothing when disabled. */
  val formNoValidate: Dom.BooleanAttribute       = Dom.boolAttr("formnovalidate")
  /** Returns the `height` attribute. Give it a value with `:=` or by calling it, for example `height := "..."`. */
  val height: Dom.PartialAttribute               = Dom.attr("height")
  /** A boolean `hidden` attribute. It renders as the bare name `hidden` when enabled, and renders nothing when disabled. */
  val hidden: Dom.BooleanAttribute               = Dom.boolAttr("hidden")
  /** Returns the `href` attribute. Give it a value with `:=` or by calling it, for example `href := "..."`. */
  val href: Dom.PartialAttribute                 = Dom.attr("href")
  /** Returns the `id` attribute. Give it a value with `:=` or by calling it, for example `id := "..."`. */
  val id: Dom.PartialAttribute                   = Dom.attr("id")
  /** A boolean `itemscope` attribute. It renders as the bare name `itemscope` when enabled, and renders nothing when disabled. */
  val itemScope: Dom.BooleanAttribute            = Dom.boolAttr("itemscope")
  /** Returns the `lang` attribute. Give it a value with `:=` or by calling it, for example `lang := "..."`. */
  val lang: Dom.PartialAttribute                 = Dom.attr("lang")
  /** Returns the `list` attribute. Give it a value with `:=` or by calling it, for example `list := "..."`. */
  val list: Dom.PartialAttribute                 = Dom.attr("list")
  /** Returns the `loop` attribute. Give it a value with `:=` or by calling it, for example `loop := "..."`. */
  val loop: Dom.PartialAttribute                 = Dom.attr("loop")
  /** Returns the `max` attribute. Give it a value with `:=` or by calling it, for example `max := "..."`. */
  val max: Dom.PartialAttribute                  = Dom.attr("max")
  /** Returns the `maxlength` attribute. Give it a value with `:=` or by calling it, for example `maxlength := "..."`. */
  val maxlength: Dom.PartialAttribute            = Dom.attr("maxlength")
  /** Returns the `media` attribute. Give it a value with `:=` or by calling it, for example `media := "..."`. */
  val media: Dom.PartialAttribute                = Dom.attr("media")
  /** Returns the `method` attribute. Give it a value with `:=` or by calling it, for example `method := "..."`. */
  val method: Dom.PartialAttribute               = Dom.attr("method")
  /** Returns the `min` attribute. Give it a value with `:=` or by calling it, for example `min := "..."`. */
  val min: Dom.PartialAttribute                  = Dom.attr("min")
  /** Returns the `minlength` attribute. Give it a value with `:=` or by calling it, for example `minlength := "..."`. */
  val minlength: Dom.PartialAttribute            = Dom.attr("minlength")
  /** A boolean `multiple` attribute. It renders as the bare name `multiple` when enabled, and renders nothing when disabled. */
  val multiple: Dom.BooleanAttribute             = Dom.boolAttr("multiple")
  /** Returns the `muted` attribute. Give it a value with `:=` or by calling it, for example `muted := "..."`. */
  val muted: Dom.PartialAttribute                = Dom.attr("muted")
  /** Returns the `name` attribute. Give it a value with `:=` or by calling it, for example `name := "..."`. */
  val name: Dom.PartialAttribute                 = Dom.attr("name")
  /** A boolean `novalidate` attribute. It renders as the bare name `novalidate` when enabled, and renders nothing when disabled. */
  val noValidate: Dom.BooleanAttribute           = Dom.boolAttr("novalidate")
  /** A boolean `open` attribute. It renders as the bare name `open` when enabled, and renders nothing when disabled. */
  val open: Dom.BooleanAttribute                 = Dom.boolAttr("open")
  /** Returns the `pattern` attribute. Give it a value with `:=` or by calling it, for example `pattern := "..."`. */
  val pattern: Dom.PartialAttribute              = Dom.attr("pattern")
  /** Returns the `placeholder` attribute. Give it a value with `:=` or by calling it, for example `placeholder := "..."`. */
  val placeholder: Dom.PartialAttribute          = Dom.attr("placeholder")
  /** Returns the `poster` attribute. Give it a value with `:=` or by calling it, for example `poster := "..."`. */
  val poster: Dom.PartialAttribute               = Dom.attr("poster")
  /** Returns the `preload` attribute. Give it a value with `:=` or by calling it, for example `preload := "..."`. */
  val preload: Dom.PartialAttribute              = Dom.attr("preload")
  /** A boolean `readonly` attribute. It renders as the bare name `readonly` when enabled, and renders nothing when disabled. */
  val readOnly: Dom.BooleanAttribute             = Dom.boolAttr("readonly")
  /** Returns the `rel` attribute, built from one or more values, which render joined by a space, for example `rel := ("a", "b")`. */
  val rel: Dom.PartialMultiAttribute             = Dom.multiAttr("rel")
  /** A boolean `required` attribute. It renders as the bare name `required` when enabled, and renders nothing when disabled. */
  val required: Dom.BooleanAttribute             = Dom.boolAttr("required")
  /** A boolean `reversed` attribute. It renders as the bare name `reversed` when enabled, and renders nothing when disabled. */
  val reversed: Dom.BooleanAttribute             = Dom.boolAttr("reversed")
  /** Returns the `role` attribute. Give it a value with `:=` or by calling it, for example `role := "..."`. */
  val role: Dom.PartialAttribute                 = Dom.attr("role")
  /** Returns the `rows` attribute. Give it a value with `:=` or by calling it, for example `rows := "..."`. */
  val rows: Dom.PartialAttribute                 = Dom.attr("rows")
  /** A boolean `scoped` attribute. It renders as the bare name `scoped` when enabled, and renders nothing when disabled. */
  val scoped: Dom.BooleanAttribute               = Dom.boolAttr("scoped")
  /** A boolean `seamless` attribute. It renders as the bare name `seamless` when enabled, and renders nothing when disabled. */
  val seamless: Dom.BooleanAttribute             = Dom.boolAttr("seamless")
  /** A boolean `selected` attribute. It renders as the bare name `selected` when enabled, and renders nothing when disabled. */
  val selected: Dom.BooleanAttribute             = Dom.boolAttr("selected")
  /** Returns the `size` attribute. Give it a value with `:=` or by calling it, for example `size := "..."`. */
  val size: Dom.PartialAttribute                 = Dom.attr("size")
  /** Returns the `spellcheck` attribute. Give it a value with `:=` or by calling it, for example `spellcheck := "..."`. */
  val spellcheck: Dom.PartialAttribute           = Dom.attr("spellcheck")
  /** Returns the `src` attribute. Give it a value with `:=` or by calling it, for example `src := "..."`. */
  val src: Dom.PartialAttribute                  = Dom.attr("src")
  /** Returns the `step` attribute. Give it a value with `:=` or by calling it, for example `step := "..."`. */
  val step: Dom.PartialAttribute                 = Dom.attr("step")
  /** Returns the `style` attribute. Give it a value with `:=` or by calling it, for example `styleAttr := "..."`. */
  val styleAttr: Dom.PartialAttribute            = Dom.attr("style")
  /** Returns the `tabindex` attribute. Give it a value with `:=` or by calling it, for example `tabindex := "..."`. */
  val tabindex: Dom.PartialAttribute             = Dom.attr("tabindex")
  /** Returns the `target` attribute. Give it a value with `:=` or by calling it, for example `target := "..."`. */
  val target: Dom.PartialAttribute               = Dom.attr("target")
  /** Returns the `title` attribute. Give it a value with `:=` or by calling it, for example `titleAttr := "..."`. */
  val titleAttr: Dom.PartialAttribute            = Dom.attr("title")
  /** Returns the `translate` attribute. Give it a value with `:=` or by calling it, for example `translate := "..."`. */
  val translate: Dom.PartialAttribute            = Dom.attr("translate")
  /** Returns the `type` attribute. Give it a value with `:=` or by calling it, for example ``type` := "..."`. */
  val `type`: Dom.PartialAttribute               = Dom.attr("type")
  /** Returns the `value` attribute. Give it a value with `:=` or by calling it, for example `value := "..."`. */
  val value: Dom.PartialAttribute                = Dom.attr("value")
  /** Returns the `width` attribute. Give it a value with `:=` or by calling it, for example `width := "..."`. */
  val width: Dom.PartialAttribute                = Dom.attr("width")
  /** Returns the `wrap` attribute. Give it a value with `:=` or by calling it, for example `wrap := "..."`. */
  val wrap: Dom.PartialAttribute                 = Dom.attr("wrap")
  /** Returns the `xmlns` attribute. Give it a value with `:=` or by calling it, for example `xmlns := "..."`. */
  val xmlns: Dom.PartialAttribute                = Dom.attr("xmlns")

  /** Returns the attribute `aria-<name>`, for example `aria("label")` gives `aria-label`. Give it a value with `:=`. */
  def aria(name: String): Dom.PartialAttribute   = Dom.attr(s"aria-$name")
  /** Returns an attribute with exactly the given `name`. Give it a value with `:=`. */
  def custom(name: String): Dom.PartialAttribute = Dom.attr(name)
  /** Returns the attribute `data-<name>`, for example `data("user-id")` gives `data-user-id`. Give it a value with `:=`. */
  def data(name: String): Dom.PartialAttribute   = Dom.attr(s"data-$name")
}
