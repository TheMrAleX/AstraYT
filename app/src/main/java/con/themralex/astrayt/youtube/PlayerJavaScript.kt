package con.themralex.astrayt.youtube

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.utils.JavaScript
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Evaluate the current YouTube player using Android's maintained JavaScript engine.
 * No bridge, navigation, network, file or content access is exposed to player code. */
internal object PlayerJavaScript {
    fun install(context:Context) {
        val application=context.applicationContext
        JavaScript.setRunner { source,name,parameters ->
            check(Looper.myLooper()!=Looper.getMainLooper())
            val main=Handler(Looper.getMainLooper())
            val latch=CountDownLatch(1)
            val result=AtomicReference<String>()
            val failure=AtomicReference<Throwable>()
            val view=AtomicReference<WebView>()
            val closed=java.util.concurrent.atomic.AtomicBoolean(false)
            main.post {
                if(closed.get())return@post
                try {
                    val web=WebView(application);view.set(web)
                    if(closed.get()){view.set(null);web.destroy();return@post}
                    web.settings.apply {
                        javaScriptEnabled=true;blockNetworkLoads=true;allowFileAccess=false;allowContentAccess=false
                        domStorageEnabled=false;databaseEnabled=false
                    }
                    val args=JSONArray(parameters.toList()).toString()
                    // Shadow browser globals: the extractor helper assigns location/window;
                    // on a real Window those assignments would navigate away during evaluation.
                    require(name.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*")))
                    val executable=source+"\nreturn String("+name+".apply(null,"+args+"));"
                    val script="""
                        (function(){
                          try {
                            var sandbox=Object.create(null);
                            ["Object","Array","String","Number","Boolean","Math","JSON","Reflect","Symbol","Map","Set","WeakMap","WeakSet","Date","RegExp","Promise","Proxy","Error","TypeError","RangeError","ArrayBuffer","DataView","Uint8Array","Int8Array","Uint16Array","Int16Array","Uint32Array","Int32Array","Float32Array","Float64Array","BigInt","parseInt","parseFloat","isNaN","isFinite","decodeURIComponent","encodeURIComponent","atob","btoa"].forEach(function(k){sandbox[k]=globalThis[k];});
                            sandbox.document=Object.create(null);sandbox.navigator=Object.create(null);
                            sandbox.XMLHttpRequest={prototype:{}};
                            sandbox.location={href:"https://www.youtube.com/",origin:"https://www.youtube.com",hostname:"www.youtube.com",protocol:"https:",pathname:"/",search:"",hash:""};
                            var fn=new Function("globalThis","window","self","document","navigator","location","XMLHttpRequest","URL",${JSONObject.quote(executable)});
                            return JSON.stringify({value:fn.call(sandbox,sandbox,sandbox,sandbox,sandbox.document,sandbox.navigator,sandbox.location,sandbox.XMLHttpRequest,undefined)});
                          }catch(e){return JSON.stringify({error:String(e)});}
                        })()
                    """.trimIndent()
                    web.evaluateJavascript(script) { raw ->
                        try {
                            val decoded=JSONArray("[$raw]").getString(0)
                            val answer=JSONObject(decoded)
                            if(answer.has("error"))throw IllegalStateException("Player JavaScript: "+answer.getString("error").take(180))
                            result.set(answer.getString("value"))
                        }catch(e:Exception){failure.set(e)}finally{latch.countDown()}
                    }
                }catch(e:Exception){failure.set(e);latch.countDown()}
            }
            try {
                check(latch.await(15,TimeUnit.SECONDS)){"Player JavaScript timeout"}
                failure.get()?.let{throw IllegalStateException("Player JavaScript failed",it)}
                result.get() ?: error("Player JavaScript returned no result")
            } finally {
                closed.set(true)
                main.post {view.getAndSet(null)?.let{it.stopLoading();it.destroy()}}
            }
        }
    }
}
