package con.themralex.astrayt.download

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

/** Microsecond timeline shared by video and audio; unknown/invalid intervals are discarded. */
@kotlinx.serialization.Serializable data class Cut(val startUs:Long,val endUs:Long)
object CutTimeline {
    fun normalize(cuts:List<Cut>,durationUs:Long):List<Cut> {
        val result=mutableListOf<Cut>()
        cuts.map{Cut(it.startUs.coerceAtLeast(0),it.endUs.coerceAtMost(durationUs))}.filter{it.endUs>it.startUs}.sortedBy{it.startUs}.forEach { cut->
            val last=result.lastOrNull()
            if(last!=null && cut.startUs<=last.endUs)result[result.lastIndex]=Cut(last.startUs,maxOf(last.endUs,cut.endUs)) else result.add(cut)
        }
        return result
    }
    fun outputTime(timeUs:Long,cuts:List<Cut>):Long? {
        var removed=0L
        for(cut in cuts) {
            if(timeUs<cut.startUs)break
            if(timeUs<cut.endUs)return null
            removed+=cut.endUs-cut.startUs
        }
        return timeUs-removed
    }
}
class SponsorBlockFailure(cause:Throwable):IOException("SPONSORBLOCK",cause)
internal class SponsorBlock(private val client:OkHttpClient) {
    suspend fun segments(videoId:String,categories:Set<String>):List<Cut> = withContext(Dispatchers.IO) {
        try {
            val allowed=categories.intersect(setOf("sponsor","intro","outro","selfpromo","interaction","music_offtopic","preview","filler"))
            if(allowed.isEmpty())return@withContext emptyList()
            val url="https://sponsor.ajay.app/api/skipSegments".toHttpUrl().newBuilder().addQueryParameter("videoID",videoId)
                .addQueryParameter("categories",JsonArray(allowed.sorted().map{JsonPrimitive(it)}).toString()).build()
            val call=client.newCall(Request.Builder().url(url).build()).apply{timeout().timeout(8,java.util.concurrent.TimeUnit.SECONDS)}
            val cancellation=currentCoroutineContext().job.invokeOnCompletion { if(it!=null)call.cancel() }
            try {call.execute().use { response->
                if(response.code==404)return@withContext emptyList()
                if(!response.isSuccessful)throw IOException("HTTP ${response.code}")
                Json.parseToJsonElement(response.body.string()).jsonArray.mapNotNull { element->
                    val item=element.jsonObject
                    if(item["category"]?.jsonPrimitive?.content !in allowed || item["actionType"]?.jsonPrimitive?.contentOrNull?.let{it!="skip"}==true)return@mapNotNull null
                    val range=item["segment"] as? JsonArray ?: return@mapNotNull null
                    val start=range.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val end=range.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    if(!start.isFinite()||!end.isFinite()||end<=start)return@mapNotNull null
                    Cut((start*1_000_000).toLong(),(end*1_000_000).toLong())
                }
            }}finally{cancellation.dispose()}
        }catch(e:CancellationException){throw e}catch(e:Exception){throw SponsorBlockFailure(e)}
    }
}
