# kotlinx.serialization ships its own consumer rules; keep our serializable DTOs' companions.
-keepclassmembers @kotlinx.serialization.Serializable class io.github.nullwrx.openmacro.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# ZXing (F-Droid flavor) uses reflection-free code; nothing extra needed.
# ML Kit (Play flavor) ships consumer rules.
