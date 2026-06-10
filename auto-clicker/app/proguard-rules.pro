# ProGuard rules for Auto Clicker
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keepclassmembers class com.autoclicker.app.macro.** { *; }
-keep class com.autoclicker.app.macro.MacroProfile { *; }
-keep class com.autoclicker.app.macro.MacroAction { *; }

# Keep accessibility service
-keep class com.autoclicker.app.service.AutoClickerService { *; }
