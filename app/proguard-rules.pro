-keep class com.example.applauncher.** { *; }
-keep interface com.example.applauncher.** { *; }

# Keep Android system classes
-keep class android.** { *; }
-keep class androidx.** { *; }

# Print configuration
-printmapping build/outputs/mapping/release/mapping.txt
