# Proguard rules for Hermes Companion App
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Gson models
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
