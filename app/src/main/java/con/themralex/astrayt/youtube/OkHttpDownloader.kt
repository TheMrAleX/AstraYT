package con.themralex.astrayt.youtube
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.*
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
internal class OkHttpDownloader(private val client:OkHttpClient):Downloader() {
override fun execute(request:Request):Response {
                val builder=okhttp3.Request.Builder().url(request.url())
                request.headers().forEach{(key,values)->values.forEach{builder.addHeader(key,it)}}
                if(request.headers().keys.none{it.equals("User-Agent",true)})builder.header("User-Agent","Mozilla/5.0 (X11; Linux x86_64; rv:153.0) Gecko/20100101 Firefox/153.0")
                val method=request.httpMethod();val data=request.dataToSend();builder.method(method,if(method=="GET"||method=="HEAD")null else (data ?: byteArrayOf()).toRequestBody())
                client.newCall(builder.build()).execute().use { response->
                    if(response.code==429)throw ReCaptchaException("Rate limited",request.url())
                    if(response.code>=500)throw java.io.IOException("HTTP ${response.code}")
                    return Response(response.code,response.message,response.headers.toMultimap(),response.body.string(),response.request.url.toString())
                }
            }
}
