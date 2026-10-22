plugins { `java-library` }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
val upstream by configurations.creating { isTransitive = false }
dependencies {
    upstream(libs.extractor)
    compileOnly(libs.extractor)
    api("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    api("org.jsoup:jsoup:1.22.2")
    api("com.google.code.findbugs:jsr305:3.0.2")
    api("com.google.protobuf:protobuf-javalite:4.35.1")
    api("org.mozilla:rhino:1.8.1")
    api("org.mozilla:rhino-engine:1.8.1")
}
check(libs.versions.extractor.get() == "v0.26.5") { "Review/remove the YouTube hotfix before changing the base extractor" }
tasks.jar {
    from(provider { zipTree(upstream.singleFile) }) {
        exclude("org/schabi/newpipe/extractor/services/youtube/YoutubeJavaScriptPlayerManager.class")
        exclude("org/schabi/newpipe/extractor/services/youtube/YoutubeThrottlingParameterUtils.class")
        exclude("org/schabi/newpipe/extractor/utils/JavaScript.class")
        exclude("META-INF/MANIFEST.MF")
    }
}
