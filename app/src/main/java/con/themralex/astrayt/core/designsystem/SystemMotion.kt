package con.themralex.astrayt.core.designsystem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Refresh on system changes and return from Settings, without polling from composition. */
@Composable internal fun rememberMotionOptions(preference:Boolean):MotionOptions {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val manager=remember(context){context.getSystemService(PowerManager::class.java)}
    fun scale()=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
    var systemScale by remember(context){mutableFloatStateOf(scale())}
    var powerSave by remember(context){mutableStateOf(manager.isPowerSaveMode)}
    DisposableEffect(context,lifecycle) {
        fun refresh(){systemScale=scale();powerSave=manager.isPowerSaveMode}
        val observer=object:ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange:Boolean){refresh()}
        }
        val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){refresh()}}
        val events=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)refresh()}
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        ContextCompat.registerReceiver(context,receiver,IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycle.addObserver(events)
        refresh()
        onDispose{context.contentResolver.unregisterContentObserver(observer);context.unregisterReceiver(receiver);lifecycle.removeObserver(events)}
    }
    return MotionPolicy.resolve(preference,systemScale,powerSave)
}
