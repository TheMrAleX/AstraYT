package con.themralex.astrayt.feature

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import con.themralex.astrayt.R
import con.themralex.astrayt.data.db.VideoEntity
import con.themralex.astrayt.data.prefs.Preferences
import con.themralex.astrayt.download.*
import con.themralex.astrayt.feature.player.Playback
import con.themralex.astrayt.youtube.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject

data class SubtitleDialogState(val video:VideoEntity?=null,val loading:Boolean=false,val tracks:List<SubtitleOption> = emptyList(),val selected:String?=null,val remember:Boolean=false,val downloading:Boolean=false,val error:Boolean=false,val file:File?=null,val timelineMissing:Boolean=false)
@HiltViewModel class VideoExtrasModel @Inject constructor(private val backend:YoutubeBackend,private val storage:MediaStorage,private val subtitles:SubtitleDownloads,private val prefs:Preferences,private val playback:Playback,private val timelines:con.themralex.astrayt.timeline.TimelineStore,@ApplicationContext private val context:Context):ScreenModel() {
    private val _refreshing=MutableStateFlow(setOf<Long>());val refreshing=_refreshing.asStateFlow()
    private val _subtitle=MutableStateFlow(SubtitleDialogState());val subtitle=_subtitle.asStateFlow()
    private var request:Job?=null
    fun refreshMetadata(video:VideoEntity){if(video.id in _refreshing.value)return;_refreshing.value+=video.id
        work{try{storage.repairMetadata(video.id,backend.metadata(video.ytId));playback.refreshMetadata(video.id);effects.send(if(video.removedDurationMs>0 && subtitles.timeline(video.ytId).isEmpty())R.string.timeline_cut_missing else if(timelines.read(video).sponsorFailed)R.string.timeline_partial else R.string.metadata_updated)}finally{_refreshing.value-=video.id}}
    }
    fun openSubtitles(video:VideoEntity){request?.cancel();_subtitle.value=SubtitleDialogState(video=video,loading=true)
        request=work{try{val tracks=backend.metadata(video.ytId).subtitles;val settings=prefs.flow.first();val language=settings.subtitleLanguage ?: settings.preferredAudioLanguage
            _subtitle.value=_subtitle.value.copy(loading=false,tracks=tracks,selected=language?.let{SubtitlePolicy.select(tracks,it)?.id} ?: tracks.firstOrNull()?.id)
        }catch(e:CancellationException){throw e}catch(_:Exception){_subtitle.value=_subtitle.value.copy(loading=false,error=true)}}
    }
    fun chooseSubtitle(id:String){_subtitle.value=_subtitle.value.copy(selected=id)}
    fun rememberSubtitle(value:Boolean){_subtitle.value=_subtitle.value.copy(remember=value)}
    fun closeSubtitles(){request?.cancel();_subtitle.value=SubtitleDialogState()}
    fun downloadSubtitle(){val old=_subtitle.value;val video=old.video ?: return;val track=old.tracks.firstOrNull{it.id==old.selected} ?: return;if(old.downloading)return
        _subtitle.value=old.copy(downloading=true,error=false)
        request=work{try{
            if(video.removedDurationMs>0 && subtitles.timeline(video.ytId).isEmpty())throw java.io.IOException("SUBTITLE_TIMELINE")
            val file=subtitles.download(video.ytId,track);if(old.remember)prefs.update{it.copy(subtitleLanguage=track.language)}
            _subtitle.value=_subtitle.value.copy(downloading=false,file=file);effects.send(R.string.subtitle_saved)
        }catch(e:CancellationException){throw e}catch(e:Exception){_subtitle.value=_subtitle.value.copy(downloading=false,error=true,timelineMissing=e.message=="SUBTITLE_TIMELINE")}}
    }
    fun exportSubtitle(uri:Uri)=work{val file=_subtitle.value.file ?: return@work
        withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{out->file.inputStream().use{it.copyTo(out)}} ?: throw java.io.IOException("STORAGE")};effects.send(R.string.subtitle_exported)
    }
}
