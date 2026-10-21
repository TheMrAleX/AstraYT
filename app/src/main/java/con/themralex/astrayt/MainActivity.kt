package con.themralex.astrayt
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.*
import android.util.Rational
import android.provider.Settings as SystemSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.*
import androidx.compose.animation.*
import con.themralex.astrayt.core.designsystem.*
import con.themralex.astrayt.feature.*
import con.themralex.astrayt.feature.player.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable private data object Capture
@Serializable private data object Queue
@Serializable private data object Library
@Serializable private data object SettingsRoute
@Serializable private data class ChannelRoute(val id:String)
@Serializable private data object PlayerRoute
@AndroidEntryPoint class MainActivity:ComponentActivity() {
    @Inject lateinit var playback:Playback
    private var shared by mutableStateOf("")
    private var requestedScreen by mutableStateOf("")
    private var downloadedId by mutableLongStateOf(-1)
    private var pip by mutableStateOf(false)
    private var playerVisible=false
    override fun onCreate(savedInstanceState:Bundle?) {
        installSplashScreen();super.onCreate(savedInstanceState);enableEdgeToEdge();receive(intent)
        setContent {AstraApp(shared,requestedScreen,downloadedId,pip,{shared="";requestedScreen="";downloadedId=-1},{playerVisible=it})}
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);receive(intent)}
    private fun receive(intent:Intent?){if(intent?.action==Intent.ACTION_SEND && intent.type=="text/plain")shared=intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty();requestedScreen=intent?.getStringExtra("screen").orEmpty();downloadedId=intent?.getLongExtra("downloadId",-1) ?: -1}
    override fun onUserLeaveHint(){super.onUserLeaveHint();val state=playback.state.value;if(playerVisible && state.playing && state.video?.kind=="VIDEO")runCatching{enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16,9)).build())}}
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode:Boolean,newConfig:Configuration){super.onPictureInPictureModeChanged(isInPictureInPictureMode,newConfig);pip=isInPictureInPictureMode}
}
@Composable private fun AstraApp(shared:String,requestedScreen:String,downloadedId:Long,pip:Boolean,onConsumed:()->Unit,onPlayerVisible:(Boolean)->Unit) {
    val main:MainModel=viewModel();val capture:CaptureModel=viewModel();val queue:QueueModel=viewModel();val library:LibraryModel=viewModel();val settings:SettingsModel=viewModel()
    val prefs by settings.state.collectAsStateWithLifecycle();val all by library.all.collectAsStateWithLifecycle();val playing by main.playback.state.collectAsStateWithLifecycle();val update by settings.update.collectAsStateWithLifecycle()
    val context=LocalContext.current;val nav=rememberNavController();val entry by nav.currentBackStackEntryAsState();val destination=entry?.destination
    val isPlayer=destination?.hasRoute<PlayerRoute>()==true
    var toast by remember{mutableStateOf<Int?>(null)}
    LaunchedEffect(isPlayer){onPlayerVisible(isPlayer)}
    LaunchedEffect(Unit){listOf(main,capture,queue,library,settings).forEach{model->launch{model.effects.receiveAsFlow().collect{toast=it}}}}
    LaunchedEffect(toast){if(toast!=null){delay(3500);toast=null}}
    LaunchedEffect(shared,requestedScreen,downloadedId,prefs.onboardingDone){if(prefs.onboardingDone){if(shared.isNotBlank()){capture.input(shared,true);nav.navigate(Capture){launchSingleTop=true}}else if(requestedScreen=="queue")nav.navigate(Queue){launchSingleTop=true}else if(requestedScreen=="player" && downloadedId>=0){main.playDownloaded(downloadedId);nav.navigate(PlayerRoute){launchSingleTop=true}};if(shared.isNotBlank() || requestedScreen.isNotBlank())onConsumed()}}
    val manager=remember{context.getSystemService(PowerManager::class.java)}
    val animations=prefs.animationsEnabled && !manager.isPowerSaveMode && SystemSettings.Global.getFloat(context.contentResolver,SystemSettings.Global.ANIMATOR_DURATION_SCALE,1f)>0
    AstraTheme(prefs.amoledPureBlack,prefs.accentPreset,animations) {
        VoidBackground {
            if(!prefs.onboardingDone)OnboardingScreen(settings)
            else {
                val bottom=if(isPlayer||pip)0.dp else if(playing.video!=null)192.dp else 100.dp
                Box(Modifier.fillMaxSize().then(if(pip)Modifier else Modifier.statusBarsPadding()).padding(bottom=bottom)) {
                    NavHost(navController=nav,startDestination=Capture,enterTransition={fadeIn()},exitTransition={fadeOut()}) {
                        composable<Capture>{Column{if(update!=null)SingularityButton(stringResource(R.string.update_available),{external(context,update!!)},Modifier.fillMaxWidth(),"secondary");Box(Modifier.weight(1f)){CaptureScreen(capture,prefs,all,{v->main.play(v,all);nav.navigate(PlayerRoute)})}}}
                        composable<Queue>{QueueScreen(queue)}
                        composable<Library>{LibraryScreen(library,{v,list->main.play(v,list);nav.navigate(PlayerRoute)},{nav.navigate(Capture)},{id->nav.navigate(ChannelRoute(id))})}
                        composable<SettingsRoute>{SettingsScreen(settings,all)}
                        composable<ChannelRoute>{backStack->ChannelScreen(backStack.toRoute<ChannelRoute>().id,all,{v,list->main.play(v,list);nav.navigate(PlayerRoute)})}
                        composable<PlayerRoute>{PlayerScreen(main,playing,all,prefs,{value->settings.change{it.copy(autoplayNext=value)}},{id->nav.navigate(ChannelRoute(id))},{nav.popBackStack()},{video->main.close();library.delete(video)},pip)}
                    }
                }
                if(!isPlayer && !pip)Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(playing.video!=null)MiniPlayer(playing,{nav.navigate(PlayerRoute)},{main.toggle()},{main.close()},Modifier.fillMaxWidth())
                    val selected=when{destination?.hasRoute<Queue>()==true->1;destination?.hasRoute<Library>()==true||destination?.hasRoute<ChannelRoute>()==true->2;destination?.hasRoute<SettingsRoute>()==true->3;else->0}
                    FloatingNavBar(listOf(stringResource(R.string.capture),stringResource(R.string.queue),stringResource(R.string.library),stringResource(R.string.settings)),listOf(Icons.Rounded.AddLink,Icons.Rounded.Downloading,Icons.Rounded.VideoLibrary,Icons.Rounded.Settings),selected,{index->val route:Any=when(index){1->Queue;2->Library;3->SettingsRoute;else->Capture};nav.navigate(route){popUpTo<Capture>{saveState=true};launchSingleTop=true;restoreState=true}},Modifier.fillMaxWidth())
                }
            }
            toast?.let{Toast(stringResource(it),Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start=16.dp,end=16.dp,bottom=if(isPlayer)24.dp else 104.dp))}
        }
    }
}
