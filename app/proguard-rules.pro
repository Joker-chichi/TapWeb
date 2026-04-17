# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Jsoup
-keep class org.jsoup.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
