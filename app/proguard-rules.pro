# Keep rules for the release build.
#
# Only what R8 cannot see for itself. Every rule below has a reason next to it, because an
# unexplained keep rule is impossible to remove later: nobody can tell whether it is load-bearing
# or left over from a problem that was fixed elsewhere.

# --- kotlinx.serialization ----------------------------------------------------------------
# The plugin generates a serialiser per @Serializable class at compile time, so there is no
# reflection over the class itself — but the generated `Companion.serializer()` and the synthetic
# `$serializer` class are only ever reached through generated code that R8 reads as dead.
#
# This matters concretely: RenderSettings *is* the preset format. If a serialiser were stripped, the
# symptom would not be a crash on startup — it would be presets.json failing to decode at runtime,
# which the decode-per-entry fallback would then quietly report as an empty library.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$serializer {
    *** INSTANCE;
}

# Enum entries reached by name during deserialisation. The effect chain's stored order is a list of
# EffectId *names*, and every params class carries at least one mode enum, so an obfuscated enum
# name means a stored preset no longer decodes.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- libmpv is not used here; no native interop to keep ------------------------------------

# --- Compose ------------------------------------------------------------------------------
# The Compose compiler and the AGP-supplied rules handle the runtime. Nothing extra is needed, and
# a blanket `-keep class androidx.compose.**` would undo most of what R8 is here to do.

# --- Diagnostics --------------------------------------------------------------------------
# Line numbers in a stack trace, without exposing the original file names.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
