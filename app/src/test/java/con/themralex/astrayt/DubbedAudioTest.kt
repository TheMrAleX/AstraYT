package con.themralex.astrayt

import con.themralex.astrayt.data.prefs.Settings
import con.themralex.astrayt.download.DownloadOptions
import con.themralex.astrayt.youtube.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.services.youtube.protos.video.Xtags.XTags
import org.schabi.newpipe.extractor.services.youtube.protos.video.Xtags.KeyValuePair
import java.util.Base64

class DubbedAudioTest {
    private val english=AudioTrackOption("en.0","en","ORIGINAL","English (original)",listOf(MediaStream("140","https://example.test/en","m4a","mp4a.40.2",128000)),true)
    private val spanish=AudioTrackOption("es.4","es","DUBBED","Español",listOf(MediaStream("140","https://example.test/es","m4a","mp4a.40.2",128000)))
    private val progressive=MediaStream("18","https://example.test/video","mp4","avc1, mp4a.40.2",height=360)
    private val hd=MediaStream("137","https://example.test/hd","mp4","avc1",height=1080,videoOnly=true)
    private fun details(videos:List<MediaStream>,tracks:List<AudioTrackOption>)=VideoDetails("abcdefghijk","Title","channel","Creator","","",60000,0,0,"",videos,tracks)
    private fun xtags(type:String)=Base64.getUrlEncoder().withoutPadding().encodeToString(XTags.newBuilder().addXtags(KeyValuePair.newBuilder().setKey("acont").setValue(type)).build().toByteArray())
    private fun response(formats:String)=Json.parseToJsonElement("""{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"abcdefghijk"},"streamingData":{"adaptiveFormats":$formats}}""").jsonObject

    @Test fun progressiveVideoReplacesEmbeddedOriginalWithTheSelectedSpanishAudio() {
        val d=details(listOf(progressive),listOf(english,spanish))
        val plan=StreamPlanner.options(d,spanish).single()
        assertEquals(progressive,plan.video)
        assertEquals("https://example.test/es",plan.audio?.url)
        assertEquals("mp4",plan.container)
    }
    @Test fun choosingOriginalAlsoReplacesAnUnidentifiedProgressiveTrackInAMultilingualVideo() {
        val d=details(listOf(progressive),listOf(english,spanish.copy(default=true)))
        assertEquals("https://example.test/en",StreamPlanner.options(d,english).single().audio?.url)
    }
    @Test fun pinnedProgressiveVideoDoesNotChangeWhenAdaptiveHdBecomesAvailable() {
        val d=details(listOf(progressive,hd),listOf(english,spanish))
        val original=StreamPlanner.options(d,null,pinnedVideoId="18").single()
        assertEquals("18",original.video?.id);assertNull(original.audio)
        val dubbed=StreamPlanner.options(d,spanish,pinnedVideoId="18").single()
        assertEquals("18",dubbed.video?.id);assertEquals("https://example.test/es",dubbed.audio?.url)
    }
    @Test fun missingDubCannotFallBackToEmbeddedOriginalEvenWhenItIsTheYoutubeDefault() {
        val missing=spanish.copy(streams=emptyList(),default=true)
        val d=details(listOf(progressive,hd),listOf(english,missing))
        assertTrue(StreamPlanner.options(d,missing).isEmpty())
        assertTrue(StreamPlanner.options(d,missing,true).isEmpty())
    }
    @Test fun incompatibleDubCodecCannotBeReplacedWithProgressiveOriginal() {
        val opus=spanish.copy(streams=listOf(MediaStream("251","https://example.test/es","webm","opus")))
        assertTrue(StreamPlanner.options(details(listOf(progressive,hd),listOf(english,opus)),opus).isEmpty())
    }
    @Test fun rememberedSpanishSurvivesSettingsSerializationAndAppliesToAnotherChannelAndBatch() {
        val saved=Json.decodeFromString<Settings>(Json.encodeToString(Settings(preferredAudioLanguage="en",rememberedAudioLanguage="es")))
        for(batch in listOf(false,true)) {
            val selection=AudioTrackSelector.select(listOf(english,spanish),saved.preferredAudioLanguage,remembered=saved.rememberedAudioLanguage,batch=batch)
            assertEquals(spanish,selection.track);assertFalse(selection.prompt)
        }
    }
    @Test fun anUnavailableRememberedLanguageStillOffersTheAvailableLanguages() {
        val selection=AudioTrackSelector.select(listOf(english,spanish),"es",remembered="fr")
        assertEquals(spanish,selection.track);assertTrue(selection.prompt)
    }
    @Test fun refreshCanRestoreAnAliasButNeverAnAudioTrackInAnotherLanguage() {
        val saved=Json.decodeFromString<DownloadOptions>(Json.encodeToString(DownloadOptions(trackId=spanish.id,audioLanguage=spanish.locale,audioType=spanish.type,pinnedAudioId="140",planPinned=true)))
        val refreshed=spanish.copy(id="es-alias")
        assertEquals(refreshed,AudioTrackSelector.restore(listOf(english,refreshed),saved.trackId!!,saved.audioLanguage,saved.audioType))
        assertNull(AudioTrackSelector.restore(listOf(english.copy(id=spanish.id)),saved.trackId,saved.audioLanguage,saved.audioType))
        assertNull(AudioTrackSelector.restore(listOf(english),saved.trackId,saved.audioLanguage,saved.audioType))
    }
    @Test fun newlyRecoveredSpanishReplacesAnAutomaticEnglishDefaultAndOpensThePicker() {
        val available=listOf(english,spanish)
        val automatic=AudioTrackSelector.select(available,"es")
        val refreshed=AudioTrackSelector.afterRefresh(available,automatic,english,explicit=false,wasPrompt=false)
        assertEquals(spanish,refreshed.track);assertTrue(refreshed.prompt)
    }
    @Test fun anExplicitEnglishChoiceIsKeptWhenMoreLanguagesAreDiscovered() {
        val available=listOf(english,spanish)
        val refreshed=AudioTrackSelector.afterRefresh(available,AudioTrackSelector.select(available,"es"),english,explicit=true)
        assertEquals(english,refreshed.track)
    }
    @Test fun missingExplicitSpanishRequiresAnotherChoiceInsteadOfEnglish() {
        val refreshed=AudioTrackSelector.afterRefresh(listOf(english),AudioTrackSelector.select(listOf(english),"es"),spanish,explicit=true)
        assertNull(refreshed.track);assertTrue(refreshed.prompt)
    }
    @Test fun regionalVariantsAreNeverInterchangedDuringResume() {
        assertNull(AudioTrackSelector.restore(listOf(spanish.copy(locale="es-ES",id="other")),spanish.id,"es-419","DUBBED"))
    }
    @Test fun hdOriginalOnlyStillNeedsAudioDiscovery() {
        val d=details(listOf(hd),listOf(english))
        assertFalse(StreamRecovery.needsAlternate(d));assertTrue(StreamRecovery.needsAudioDiscovery(d,"es"))
        assertFalse(StreamRecovery.needsAudioDiscovery(d.copy(tracks=listOf(english,spanish)),"es"))
    }
    @Test fun localizedDefaultIsNotMistakenForOriginalAndSameItagLanguagesRemainSeparate() {
        val formats="""[{"itag":140,"url":"https://example.test/en","mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","xtags":"${xtags("original")}","audioTrack":{"id":"en.0","displayName":"English","audioIsDefault":false}},{"itag":140,"url":"https://example.test/es","mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","xtags":"${xtags("dubbed")}","audioTrack":{"id":"es.4","displayName":"Español","audioIsDefault":true}}]"""
        val d=InnertubeFallback.parse("abcdefghijk",response(formats),null){it}
        assertEquals(2,d.tracks.size)
        assertEquals("ORIGINAL",d.tracks.first().type);assertFalse(d.tracks.first().default)
        assertEquals("DUBBED",d.tracks.last().type);assertTrue(d.tracks.last().default)
        assertEquals("https://example.test/es",StreamPlanner.options(d,d.tracks.last(),true).single().audio?.url)
    }
    @Test fun descriptiveXtagsAreNeverAutoSelectedEvenWithLocalizedNames() {
        val d=InnertubeFallback.parse("abcdefghijk",response("""[{"itag":140,"url":"https://example.test/es","mimeType":"audio/mp4","xtags":"${xtags("descriptive")}","audioTrack":{"id":"es.4","displayName":"Español","audioIsDefault":true}}]"""),null){it}
        assertEquals("DESCRIPTIVE",d.tracks.single().type)
        assertNull(AudioTrackSelector.select(d.tracks,"es",autoDub=true).track)
    }
    @Test fun unknownDefaultMetadataDoesNotInventAnOriginalLanguage() {
        val d=InnertubeFallback.parse("abcdefghijk",response("""[{"itag":140,"url":"https://example.test/es","mimeType":"audio/mp4","audioTrack":{"id":"es.4","displayName":"Español","audioIsDefault":true}}]"""),null){it}
        assertEquals("UNKNOWN",d.tracks.single().type)
    }
}
