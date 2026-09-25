# Components are kept through the manifest; nothing else needs keeping.
-repackageclasses
-allowaccessmodification

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
