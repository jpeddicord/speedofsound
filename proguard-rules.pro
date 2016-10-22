# strip debug/verbose logs
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}