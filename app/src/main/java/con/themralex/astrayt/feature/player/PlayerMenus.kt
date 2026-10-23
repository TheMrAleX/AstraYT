package con.themralex.astrayt.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import con.themralex.astrayt.R
import con.themralex.astrayt.core.designsystem.*
import con.themralex.astrayt.feature.*

@Composable internal fun PlayerMenu(page:String,state:PlaybackState,onPage:(String)->Unit,onDismiss:()->Unit,onSpeed:(Float)->Unit,onTrack:(PlayerTrack)->Unit,onDisableText:()->Unit,onImport:()->Unit,onAudio:()->Unit,onMetadata:()->Unit,onSubtitles:()->Unit,onDelete:()->Unit,refreshing:Boolean){
    SingularitySheet(onDismiss){
        SheetHeading(stringResource(when(page){"speed"->R.string.speed;"audio"->R.string.audio_tracks;"text"->R.string.subtitles;else->R.string.player_settings}),onDismiss,if(page!="menu")({onPage("menu")})else null)
        when(page){
            "speed"->{
                val speeds=listOf(.25f,.5f,.75f,1f,1.25f,1.5f,1.75f,2f)
                ChoiceGrid(speeds.map{if(it==1f)stringResource(R.string.speed_normal)else "$it×"},speeds.indexOf(state.speed)){onSpeed(speeds[it]);onDismiss()}
            }
            "audio"->state.audioTracks.forEach{track->OptionRow(Icons.Rounded.Headphones,track.displayName,{onTrack(track);onDismiss()},selected=track.selected)}
            "text"->{
                OptionRow(Icons.Rounded.ClosedCaptionOff,stringResource(R.string.subtitles_off),{onDisableText();onDismiss()},selected=state.subtitleTracks.none{it.selected})
                state.subtitleTracks.forEach{track->OptionRow(Icons.Rounded.ClosedCaption,track.displayName,{onTrack(track);onDismiss()},selected=track.selected)}
                Spacer(Modifier.height(4.dp))
                OptionRow(Icons.Rounded.Subtitles,stringResource(R.string.subtitle_download),{onDismiss();onSubtitles()})
                OptionRow(Icons.Rounded.FolderOpen,stringResource(R.string.open_subtitle),onImport)
            }
            else->{
                OptionRow(Icons.Rounded.Speed,stringResource(R.string.speed),{onPage("speed")},value=if(state.speed==1f)stringResource(R.string.speed_normal)else "${state.speed}×")
                if(state.audioTracks.isNotEmpty())OptionRow(Icons.Rounded.Headphones,stringResource(R.string.audio_tracks),{onPage("audio")},value=state.audioTracks.firstOrNull{it.selected}?.displayName.orEmpty())
                OptionRow(Icons.Rounded.ClosedCaption,stringResource(R.string.subtitles),{onPage("text")},value=if(state.subtitleTracks.any{it.selected})stringResource(R.string.on)else stringResource(R.string.subtitles_off))
                if(state.video?.kind=="VIDEO")OptionRow(if(state.audioOnly)Icons.Rounded.Videocam else Icons.Rounded.Headphones,stringResource(if(state.audioOnly)R.string.show_video else R.string.audio_only),{onAudio();onDismiss()})
                OptionRow(Icons.Rounded.Refresh,stringResource(if(refreshing)R.string.metadata_loading else R.string.metadata_download),{onDismiss();onMetadata()},enabled=!refreshing)
                OptionRow(Icons.Rounded.DeleteOutline,stringResource(R.string.delete),onDelete)
            }
        }
    }
}
