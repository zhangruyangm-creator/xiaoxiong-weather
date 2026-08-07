# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.xiaoxiong.weather.**$$serializer { *; }
-keepclassmembers class com.xiaoxiong.weather.** {
    *** Companion;
}
-keepclasseswithmembers class com.xiaoxiong.weather.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.xiaoxiong.weather.data.** { *; }
-keep class com.xiaoxiong.weather.data.remote.** { *; }

# OkHttp / platform
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.*

# Glance / WebView
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, java.lang.String);
}

# WorkManager / DataStore keep defaults via consumer proguard

# Crashlytics (optional)
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
