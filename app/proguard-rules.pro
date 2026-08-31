# Nazo — R8/ProGuard rules for the minified release build.
#
# This app needs no extra keep rules:
#   * no reflection (no Class.forName / getDeclaredConstructor / newInstance)
#   * no JNI (no System.loadLibrary)
#   * no serialization framework — JSON is parsed explicitly against org.json
#     at named call sites (org.json ships with the platform)
#   * no string-based resource lookups (no getResources().getIdentifier), so
#     resource shrinking cannot remove anything the app reaches
#   * Compose, Material3, WorkManager and Coil all ship their own
#     consumer ProGuard rules
#
# If a future dependency or feature starts using reflection, add keep rules
# HERE — do not weaken isMinifyEnabled to fix a missing-class error.
