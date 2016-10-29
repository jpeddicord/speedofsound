# strip debug/verbose logs
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# keep play services junk (current SDK is buggy and will strip otherwise)
-keep public class com.google.android.gms.* { public *; }
-dontwarn com.google.android.gms.**