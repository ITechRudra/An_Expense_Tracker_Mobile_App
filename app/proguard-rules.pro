# Room and Hilt generate code that is referenced reflectively.
-keep class com.rudra.expensetracker.data.local.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }

# kotlinx.serialization keeps generated serializers on the companion.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.rudra.expensetracker.data.export.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
