package con.themralex.astrayt.feature.player

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI

data class DescriptionLink(val start:Int,val end:Int,val url:String)
data class FormattedDescription(val text:String,val links:List<DescriptionLink>)
/** Turns both HTML and plain descriptions into readable paragraphs without executing markup. */
object DescriptionFormatter {
    fun format(source:String):FormattedDescription {
        val document=Jsoup.parseBodyFragment(source.replace("\r\n","\n"))
        val text=StringBuilder();val links=mutableListOf<DescriptionLink>()
        fun breakLine(){if(text.isNotEmpty() && text.last()!='\n')text.append('\n')}
        fun visit(node:Node) {
            when(node) {
                is TextNode->text.append(node.wholeText)
                is Element->{
                    val tag=node.normalName()
                    if(tag in setOf("script","style","iframe","object","noscript"))return
                    if(tag=="br"){text.append('\n');return}
                    val block=tag in setOf("p","div","li","ul","ol","blockquote","h1","h2","h3","h4","section")
                    if(block)breakLine()
                    if(tag=="li")text.append("• ")
                    val start=text.length
                    node.childNodes().forEach(::visit)
                    if(tag=="a" && text.length>start) {
                        val url=node.attr("href").trim()
                        val uri=runCatching{URI(when{url.startsWith("//")->"https:$url";url.startsWith("/")->"https://www.youtube.com$url";else->url})}.getOrNull()
                        if(uri?.scheme?.lowercase(java.util.Locale.ROOT) in setOf("https","http") && !uri?.host.isNullOrBlank())links.add(DescriptionLink(start,text.length,uri.toString()))
                    }
                    if(block)breakLine()
                }
            }
        }
        document.body().childNodes().forEach(::visit)
        // Keep link offsets stable: trim only trailing whitespace, not the beginning or middle.
        val result=text.toString().trimEnd()
        return FormattedDescription(result,links.mapNotNull{link->if(link.start<result.length)link.copy(end=minOf(link.end,result.length))else null})
    }
}
