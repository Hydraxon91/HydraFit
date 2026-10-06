# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod,Signature

-keep class kotlin.Metadata { *; }

# Koin ships its own consumer rules (it adds `-dontwarn org.koin.**`), and definitions/ViewModels
# resolve at compile time (reified `singleOf`/`get`), so no broad `-keep class org.koin.**` is needed.

# Ktor discovers its engine through META-INF/services when HttpClient() has no explicit engine
# (see createGeminiHttpClient). Keep the OkHttp container/engine so discovery still resolves.
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
-keep class io.ktor.client.engine.okhttp.OkHttpEngine { *; }

# LiteRT-LM binds native methods by name (Java_com_google_ai_edge_litertlm_LiteRtLmJni_*);
# renaming these classes/methods breaks the JNI lookup.
-keep class com.google.ai.edge.litertlm.** { *; }

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}