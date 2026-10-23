package con.themralex.astrayt.youtube

import java.net.URLDecoder
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Decodes cipher form fields once, then appends the signature with URL-safe encoding. */
internal object FormatUrl {
    fun resolve(format:JsonObject,signature:(String)->String):String? {
        val direct=(format["url"] as? JsonPrimitive)?.contentOrNull
        if(!direct.isNullOrBlank())return direct.toHttpUrlOrNull()?.toString()
        val cipher=(format["signatureCipher"] ?: format["cipher"])?.jsonPrimitive?.contentOrNull ?: return null
        val fields=cipher.split('&').associate { field->
            val pair=field.split('=',limit=2)
            URLDecoder.decode(pair[0],"UTF-8") to URLDecoder.decode(pair.getOrElse(1){""},"UTF-8")
        }
        val url=fields["url"]?.toHttpUrlOrNull() ?: return null
        val encrypted=fields["s"]?.takeIf{it.isNotBlank()} ?: return null
        val parameter=fields["sp"]?.takeIf{it.isNotBlank()} ?: "signature"
        val decoded=signature(encrypted).takeIf{it.isNotBlank()} ?: return null
        return url.newBuilder().setQueryParameter(parameter,decoded).build().toString()
    }
}
