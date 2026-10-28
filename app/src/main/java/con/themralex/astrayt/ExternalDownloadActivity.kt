package con.themralex.astrayt

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import con.themralex.astrayt.core.designsystem.*
import con.themralex.astrayt.data.prefs.*
import con.themralex.astrayt.feature.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/** ACTION_SEND with a package target is Morphe's external-downloader contract. */
@AndroidEntryPoint class ExternalDownloadActivity:ComponentActivity() {
    private var incoming by mutableStateOf("")
    private var delivery by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge();receive(intent);if(isFinishing)return
        setContent {
            val capture:CaptureModel=viewModel()
            val preferences:ExternalPreferencesModel=viewModel()
            val settings by preferences.state.collectAsStateWithLifecycle()
            val state by capture.state.collectAsStateWithLifecycle()
            val motion=rememberMotionOptions(settings.animationsEnabled)
            var opened by remember{mutableStateOf(false)}
            LaunchedEffect(delivery){if(!capture.state.value.modalOpen || capture.state.value.url!=incoming)capture.input(incoming,true)}
            LaunchedEffect(state.modalOpen,opened){if(state.modalOpen)opened=true else if(opened){kotlinx.coroutines.delay(if(motion.transitions)220L else 0L);finish()}}
            LaunchedEffect(Unit){capture.effects.receiveAsFlow().collect{android.widget.Toast.makeText(this@ExternalDownloadActivity,it,android.widget.Toast.LENGTH_SHORT).show()}}
            AstraTheme(settings.amoledPureBlack,settings.accentPreset,motion.transitions,motion.ambient,motion.progress) {
                CaptureModal(capture,settings)
            }
        }
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);receive(intent)}
    private fun receive(intent:Intent?) {
        incoming=intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if(incoming.isBlank()){finish();return}
        delivery++
    }
}

@HiltViewModel class ExternalPreferencesModel @Inject constructor(prefs:Preferences):ScreenModel() {
    val state=prefs.flow.stateIn(viewModelScope,SharingStarted.Eagerly,Settings())
}
