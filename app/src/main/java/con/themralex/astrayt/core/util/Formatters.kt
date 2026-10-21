package con.themralex.astrayt.core.util
import java.text.NumberFormat
import java.util.Locale
object Formatters {
    fun bytes(bytes: Long,locale: Locale=Locale.getDefault()):String {val units=arrayOf("B","KB","MB","GB","TB");var value=bytes.coerceAtLeast(0).toDouble();var i=0;while(value>=1024 && i<units.lastIndex){value/=1024;i++};val n=NumberFormat.getNumberInstance(locale).apply{maximumFractionDigits=if(i==0)0 else 1};return "${n.format(value)} ${units[i]}"}
    fun duration(ms: Long):String {val sec=ms.coerceAtLeast(0)/1000;return if(sec>=3600)"%d:%02d:%02d".format(sec/3600,sec/60%60,sec%60)else "%d:%02d".format(sec/60,sec%60)}
    fun views(value: Long,locale: Locale=Locale.getDefault()):String {val n=NumberFormat.getNumberInstance(locale).apply{maximumFractionDigits=1};return when{value>=1_000_000->n.format(value/1_000_000.0)+" M";value>=1_000->n.format(value/1_000.0)+" K";else->n.format(value)}}
}
