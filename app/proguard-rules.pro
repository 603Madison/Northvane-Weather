# kotlinx.serialization, OkHttp and MapLibre ship their own consumer rules.

# Strip verbose/debug logging from release builds so nothing incidental
# (URLs, coordinates) ends up in logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
