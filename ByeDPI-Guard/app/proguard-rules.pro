# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class io.github.romanvht.byedpi.core.ByeDpiProxy { *; }

-keep,allowoptimization class io.github.romanvht.byedpi.core.TProxyService { *; }
-keep,allowoptimization class io.github.romanvht.byedpi.activities.** { *; }
-keep,allowoptimization class io.github.romanvht.byedpi.services.** { *; }
-keep,allowoptimization class io.github.romanvht.byedpi.receiver.** { *; }

-keep,allowoptimization class io.github.romanvht.byedpi.data.** {
    <fields>;
}

# Saved as JSON in preferences (the command line generator's last result): keep the field
# names, or a later release with a different mapping can't read them back.
-keep,allowoptimization class io.github.romanvht.byedpi.ui.GenResult { <fields>; }
-keep,allowoptimization class io.github.romanvht.byedpi.ui.GenAlternative { <fields>; }

-keepattributes Signature
-keepattributes *Annotation*

-repackageclasses 'ru.romanvht'
-renamesourcefileattribute ''
-keepattributes SourceFile,InnerClasses,EnclosingMethod,Signature,RuntimeVisibleAnnotations,*Annotation*,*Parcelable*
-allowaccessmodification
-overloadaggressively
-optimizationpasses 5
-verbose
-dontusemixedcaseclassnames
-adaptclassstrings
-adaptresourcefilecontents **.xml,**.json
-adaptresourcefilenames **.xml,**.json