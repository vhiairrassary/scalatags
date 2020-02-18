package scalatags
import java.util.Objects

import org.scalajs.dom

import scala.language.implicitConversions
import scala.scalajs.js
import org.scalajs.dom.{Element, html, raw, svg}

import scala.annotation.unchecked.uncheckedVariance
import scalatags.generic.Namespace
import scalatags.stylesheet.{StyleSheetFrag, StyleTree}


/**
 * A Scalatags module that generates `dom.Element`s when the tags are rendered.
 * This provides some additional flexibility over the [[Text]] backend, as you
 * can bind structured objects to the attributes of your `dom.Element` without
 * serializing them first into strings.
 */
object JsDom extends generic.Bundle[dom.Node, dom.Element] with LowPriorityImplicits{
  type Builder = dom.Element
  object attrs extends JsDom.Cap with Attrs
  object tags extends JsDom.Cap with jsdom.Tags[TypedTag] with Tags
  object tags2 extends JsDom.Cap with jsdom.Tags2[TypedTag] with Tags2
  object styles extends JsDom.Cap with Styles
  object styles2 extends JsDom.Cap with Styles2
  object svgTags extends JsDom.Cap with jsdom.SvgTags[TypedTag] with SvgTags
  object svgAttrs extends JsDom.Cap with SvgAttrs


  object all
    extends Cap
    with Attrs
    with Styles
    with jsdom.Tags[TypedTag]
    with DataConverters

    with Tags

//  object short
//    extends Cap
//    with jsdom.Tags
//    with DataConverters
//    with Aggregate
//    with AbstractShort
//    with LowPriorityImplicits{
//
//    object * extends Cap with Attrs with Styles
//  }


//  trait Aggregate extends generic.Aggregate[dom.Element, dom.Element, dom.Node]{

  implicit def ClsModifier(s: stylesheet.Cls): Modifier = new Modifier{
    def applyTo(t: dom.Element) = t.classList.add(s.name)
  }
  implicit class StyleFrag(s: StylePair[_]) extends StyleSheetFrag{
    def applyTo(c: StyleTree) = {
      val b = dom.document.createElement("div")
      s.applyTo(b)
      val Array(style, value) = Option(b.getAttribute("style")).map(_.split(":", 2))
        .getOrElse(throw new IllegalArgumentException(s"Cannot apply style $s. Does it contain a syntax error?"))
      c.copy(styles = c.styles.updated(style, value))
    }
  }


  def genericAttr[T] = new JsDom.GenericAttr[T]
  def genericStyle[T] = new JsDom.GenericStyle[T]
  def genericPixelStyle[T](implicit ev: StyleValue[T]): PixelStyleValue[T] = new JsDom.GenericPixelStyle[T](ev)
  def genericPixelStylePx[T](implicit ev: StyleValue[String]): PixelStyleValue[T] = new JsDom.GenericPixelStylePx[T](ev)

  implicit def stringFrag(v: String): StringFrag = new JsDom.StringFrag(v)

  def raw(s: String) = RawFrag(s)



//  }
  implicit def UnitFrag(u: Unit): JsDom.StringFrag = new JsDom.StringFrag("")
  trait Cap extends Util with jsdom.TagFactory[TypedTag]{ self =>
    type ConcreteHtmlTag[T <: dom.Element] = TypedTag[T]

    protected[this] implicit def stringAttrX = new GenericAttr[String]
    protected[this] implicit def stringStyleX = new GenericStyle[String]
    protected[this] implicit def stringPixelStyleX = new GenericPixelStyle[String](stringStyleX)

    type HtmlTag = JsDom.TypedTag[html.Element]
    val HtmlTag = JsDom.TypedTag
    type SvgTag = JsDom.TypedTag[svg.Element]
    val SvgTag = JsDom.TypedTag

    val Tag = JsDom.TypedTag
//    type Tag = JsDom.TypedTag[dom.Element]

    def frag(frags: JsDom.super.Frag*): Frag  = SeqFrag(frags)
    def modifier(mods: Modifier*): Modifier = SeqNode(mods)
    def css(s: String): Style = Style(camelCase(s), s)
    def tag(s: String, void: Boolean = false): TypedTag[dom.Element] = TypedTag(s, Nil, void, implicitly)
    def typedTag[T <: dom.Element](s: String, void: Boolean = false)
                                  (implicit ns: scalatags.generic.Namespace): TypedTag[T] = {

      makeAbstractTypedTag[T](s, void, ns)
    }
    def makeAbstractTypedTag[T <: dom.Element](tag: String, void: Boolean, namespaceConfig: Namespace): TypedTag[T] = {
      TypedTag(tag, Nil, void, namespaceConfig)
    }

    implicit class SeqFrag[A](xs: Seq[A])(implicit ev: A => JsDom.super.Frag) extends Frag{
      Objects.requireNonNull(xs)
      override def applyTo(t: dom.Element): Unit = xs.foreach(_.applyTo(t))
      def render: dom.Node = {
        val frag = org.scalajs.dom.document.createDocumentFragment()
        xs.map(ev(_).asInstanceOf[Frag].render).foreach(frag.appendChild)
        frag
      }
    }
    implicit class GeneratorFrag[A](xs: geny.Generator[A])(implicit ev: A => JsDom.super.Frag) extends Frag{
      Objects.requireNonNull(xs)
      override def applyTo(t: dom.Element): Unit = xs.foreach(_.applyTo(t))
      def render: dom.Node = {
        val frag = org.scalajs.dom.document.createDocumentFragment()
        xs.map(ev(_).asInstanceOf[Frag].render).foreach(frag.appendChild)
        frag
      }
    }
  }

  object StringFrag extends Companion[StringFrag]
  case class StringFrag(v: String) extends Frag{
    Objects.requireNonNull(v)
    def render: dom.Text = dom.document.createTextNode(v)
  }

  object RawFrag extends Companion[RawFrag]
  case class RawFrag(v: String) extends Modifier{
    Objects.requireNonNull(v)
    def applyTo(elem: dom.Element): Unit = {
      elem.insertAdjacentHTML("beforeend", v)
    }
  }

  class GenericAttr[T] extends AttrValue[T]{
    def apply(t: dom.Element, a: Attr, v: T): Unit = {
      a.namespace match {
        case None =>
          if (!a.raw) t.setAttribute(a.name, v.toString)
          else {

            // Ugly workaround for https://www.w3.org/Bugs/Public/show_bug.cgi?id=27228
            val tmpElm = dom.document.createElement("p")
            tmpElm.innerHTML = s"""<p ${a.name}="${v.toString}"><p>"""
            val newAttr = tmpElm.children(0).attributes(0).cloneNode(true)
            t.setAttributeNode(newAttr.asInstanceOf[org.scalajs.dom.raw.Attr])

          }
        case Some(namespace) =>
          t.setAttributeNS(namespace.uri, a.name, v.toString)
      }
    }
  }

  class GenericStyle[T] extends StyleValue[T]{
    def apply(t: dom.Element, s: Style, v: T): Unit = {
      t.asInstanceOf[html.Html]
       .style
       .setProperty(s.cssName, v.toString)
    }
  }
  class GenericPixelStyle[T](ev: StyleValue[T]) extends PixelStyleValue[T]{
    def apply(s: Style, v: T) = StylePair(s, v, ev)
  }
  class GenericPixelStylePx[T](ev: StyleValue[String]) extends PixelStyleValue[T]{
    def apply(s: Style, v: T) = StylePair(s, v + "px", ev)
  }
  case class TypedTag[+Output <: dom.Element](tag: String = "",
                                              modifiers: List[Seq[Modifier]],
                                              void: Boolean = false,
                                              namespace: Namespace)
                                              extends super.TypedTag[Output]
                                              with Frag{

    protected[this] type Self = TypedTag[Output]

    def render: Output = {
      val elem = dom.document.createElementNS(namespace.uri, tag)
      build(elem)
      elem.asInstanceOf[Output]
    }
    /**
     * Trivial override, not strictly necessary, but it makes IntelliJ happy...
     */
    def apply(xs: Modifier*): TypedTag[Output] = {
      this.copy(tag = tag, void = void, modifiers = xs :: modifiers)
    }
    override def toString = render.outerHTML
  }


  trait Frag extends super.Frag{
    def render: dom.Node
    def applyTo(b: dom.Element) = b.appendChild(this.render)
  }

}

trait LowPriorityImplicits{ this: JsDom.type =>
  implicit object bindJsAny extends AttrValue[js.Any]{
    def apply(t: dom.Element, a: Attr, v: js.Any): Unit = {
      t.asInstanceOf[js.Dynamic].updateDynamic(a.name)(v)
    }
  }
  implicit def bindJsAnyLike[T](implicit ev: T => js.Any) = new AttrValue[ T]{
    def apply(t: dom.Element, a: Attr, v: T): Unit = {
      t.asInstanceOf[js.Dynamic].updateDynamic(a.name)(v)
    }
  }
  implicit class bindNode(e: dom.Node) extends Frag {
    override def applyTo(t: Element) = t.appendChild(e)
    def render = e
  }
}
