-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter { *; }
-dontwarn org.mozilla.javascript.tools.**
-assumenosideeffects class android.util.Log { public static *** *(...); }
-keepattributes Signature,InnerClasses,EnclosingMethod
# Android interpreter rules used by upstream NewPipe (app/proguard-rules.pro).
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
# Protobuf Lite resolves generated message fields by name when building playlist
# continuation tokens. Keep these fields through release shrinking/obfuscation.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**
