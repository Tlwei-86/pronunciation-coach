# Add project specific ProGuard rules here.
-keep class com.pronunciationcoach.app.core.PronunciationCoreBridge { *; }
-keepclassmembers class com.pronunciationcoach.app.core.PronunciationCoreBridge {
    native <methods>;
}
