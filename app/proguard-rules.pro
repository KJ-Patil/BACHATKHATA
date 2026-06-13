# ProGuard rules for BachatKhata Release builds

# Preserve line numbers and source file names for crash reporting
-keepattributes SourceFile,LineNumberTable

# Firebase & Play Services
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Firestore / POJOs serialization
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
-keep class com.example.bachatkhata.Transaction { *; }
-keep class com.example.bachatkhata.Category { *; }
-keep class com.example.bachatkhata.Budget { *; }
-keep class com.example.bachatkhata.SavingsGoal { *; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# Glide
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl { *; }

# Lottie
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# AndroidX Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# AndroidX Biometric
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**