package con.themralex.astrayt.timeline

import con.themralex.astrayt.download.Cut
import con.themralex.astrayt.download.CutTimeline
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class Chapter(val startMs:Long,val title:String)
@Serializable data class SponsorSegment(val startMs:Long,val endMs:Long,val category:String)
@Serializable data class VideoTimeline(val mediaUri:String="",val language:String="",val chapters:List<Chapter> = emptyList(),val sponsors:List<SponsorSegment> = emptyList(),val sponsorFailed:Boolean=false)
data class TimelineCapture(val chapters:List<Chapter>,val sponsors:List<SponsorSegment>,val language:String,val sponsorFailed:Boolean=false,val chaptersRequested:Boolean=true,val sponsorsRequested:Boolean=false)
object TimelinePolicy {
    val colors=mapOf("sponsor" to 0xFF00D400,"selfpromo" to 0xFFFFFF00,"interaction" to 0xFFCC00FF,"intro" to 0xFF00FFFF,"outro" to 0xFF0202ED,"preview" to 0xFF008FD6,"music_offtopic" to 0xFFFF9900,"filler" to 0xFF7300FF)
    fun language(device:String)=if(device.substringBefore('-').equals("en",true))"en"else"es"
    fun chapters(input:List<Chapter>,duration:Long)=input.filter{it.startMs>=0 && it.startMs<duration && it.title.isNotBlank()}.sortedBy{it.startMs}.distinctBy{it.startMs}.take(1000).map{it.copy(title=it.title.trim().take(300))}
    fun fromDescription(description:String,duration:Long):List<Chapter> {
        val text=description.replace(Regex("(?i)<br\\s*/?>|</p>|</div>"),"\n").replace(Regex("<[^>]*>"),"")
        val pattern=Regex("(?m)^\\s*(?:(\\d{1,3}):)?(\\d{1,2}):(\\d{2})\\s+[-–—:]?\\s*(.+)$")
        val found=pattern.findAll(text).mapNotNull{m->
            val h=m.groupValues[1].toLongOrNull() ?: 0;val min=m.groupValues[2].toLongOrNull() ?: return@mapNotNull null;val sec=m.groupValues[3].toLongOrNull() ?: return@mapNotNull null
            if(sec>=60 || (m.groupValues[1].isNotEmpty() && min>=60))null else Chapter((h*3600+min*60+sec)*1000,m.groupValues[4].replace("&amp;","&"))
        }.toList()
        val result=chapters(found,duration)
        return result.takeIf{it.size>=3 && it.first().startMs==0L && it.zipWithNext().all{(a,b)->b.startMs-a.startMs>=10000}} ?: emptyList()
    }
    fun chapterAt(chapters:List<Chapter>,position:Long)=chapters.indexOfLast{it.startMs<=position}
    fun parseSponsors(text:String,duration:Long=Long.MAX_VALUE):List<SponsorSegment> = Json.parseToJsonElement(text).jsonArray.take(10000).mapNotNull { raw->
        val item=raw as? JsonObject ?: return@mapNotNull null
        val category=(item["category"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        if(category !in colors || (item["actionType"] as? JsonPrimitive)?.contentOrNull?.let{it!="skip"}==true)return@mapNotNull null
        val range=item["segment"] as? JsonArray ?: return@mapNotNull null
        val a=(range.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null;val b=(range.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
        if(!a.isFinite() || !b.isFinite() || a<0 || b<=a || b>Long.MAX_VALUE/1000.0)return@mapNotNull null
        val start=(a*1000).toLong();val end=minOf((b*1000).toLong(),duration)
        SponsorSegment(start,end,category).takeIf{end>start}
    }.distinct().sortedBy{it.startMs}
    // Coalesce per category, then split only at intersecting chapter boundaries.
    // Drawing never performs a chapters × segments cross-product on each frame.
    fun railSegments(chapters:List<Chapter>,duration:Long,input:List<SponsorSegment>):List<SponsorSegment> {
        if(duration<=0)return emptyList()
        val boundaries=(listOf(0L)+chapters.map{it.startMs}.filter{it in 1 until duration}+duration).distinct().sorted()
        return input.filter{it.category in colors && it.startMs>=0 && it.startMs<duration && it.endMs>it.startMs}.groupBy{it.category}.flatMap{(_,segments)->
            val merged=mutableListOf<SponsorSegment>()
            segments.sortedBy{it.startMs}.forEach{s->val last=merged.lastOrNull();val bounded=s.copy(endMs=minOf(s.endMs,duration))
                if(last!=null && bounded.startMs<=last.endMs)merged[merged.lastIndex]=last.copy(endMs=maxOf(last.endMs,bounded.endMs))else merged.add(bounded)}
            merged.flatMap{s->
                val at=boundaries.binarySearch(s.startMs)
                var index=if(at>=0)at else (-at-2).coerceAtLeast(0)
                val parts=mutableListOf<SponsorSegment>()
                while(index<boundaries.lastIndex && boundaries[index]<s.endMs){
                    val start=maxOf(boundaries[index],s.startMs);val end=minOf(boundaries[index+1],s.endMs)
                    if(end>start)parts.add(s.copy(startMs=start,endMs=end));index++
                };parts
            }
        }
    }
    fun skipAt(segments:List<SponsorSegment>,selected:Set<String>,position:Long,duration:Long):SponsorSegment? {
        val active=segments.filter{it.category in selected && it.endMs>it.startMs && it.startMs>=0 && it.startMs<duration}
        val first=active.firstOrNull{position>=it.startMs && position<minOf(it.endMs,duration)} ?: return null
        var end=minOf(first.endMs,duration)
        do {val old=end;active.filter{it.startMs<=end && it.endMs>position}.forEach{end=maxOf(end,minOf(it.endMs,duration))}}while(end!=old)
        return first.copy(endMs=end)
    }
    // Map starts inside removed material to its boundary; drop chapters entirely removed.
    fun retime(capture:TimelineCapture,cuts:List<Cut>,sourceDuration:Long,uri:String):VideoTimeline {
        val normalized=CutTimeline.normalize(cuts,sourceDuration*1000)
        fun map(time:Long):Long {val us=time*1000;return (us-normalized.sumOf{(us-it.startUs).coerceIn(0,it.endUs-it.startUs)})/1000}
        val original=chapters(capture.chapters,sourceDuration)
        val mapped=original.mapIndexedNotNull{index,c->val end=original.getOrNull(index+1)?.startMs ?: sourceDuration
            if(map(end)<=map(c.startMs))null else c.copy(startMs=map(c.startMs))}.distinctBy{it.startMs}
        val sponsors=capture.sponsors.flatMap{s->
            var spans=listOf(s.startMs to minOf(s.endMs,sourceDuration))
            normalized.forEach{cut->val a=cut.startUs/1000;val b=cut.endUs/1000
                spans=spans.flatMap{(x,y)->if(y<=a || x>=b)listOf(x to y)else listOf(x to minOf(y,a),maxOf(x,b) to y).filter{it.second>it.first}}
            }
            spans.map{(a,b)->s.copy(startMs=map(a),endMs=map(b))}.filter{it.endMs>it.startMs}
        }
        return VideoTimeline(uri,capture.language,mapped,sponsors,capture.sponsorFailed)
    }
}
