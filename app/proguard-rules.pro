# Правила R8 добавляются здесь по мере необходимости.

# Vosk работает через JNA: классы и методы ищутся из нативного кода по именам.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
