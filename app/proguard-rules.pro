-keep class com.chaquo.python.** { *; }

# Kotlin -> Python bridge.
-keep class com.buddyapp.Buddy.api.PythonBridge { *; }

# Read from Python (from com.buddyapp.Buddy import BuildConfig) for the User-Agent.
# R8 would otherwise inline the constants and remove the class.
-keep class com.buddyapp.Buddy.BuildConfig { public static final java.lang.String VERSION_NAME; }
