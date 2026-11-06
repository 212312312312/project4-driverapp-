# ==============================================================================
# ПРАВИЛА PROGUARD / R8 ДЛЯ PRODUCTION-РЕЛИЗА (com.taxiapp.driver)
# Защищают сетевые DTO (Gson/Retrofit), Serializable-модели (Intent extras)
# и STOMP/RxJava от поломки при включении minifyEnabled = true
# ==============================================================================

# 1. Сохраняем атрибуты для корректной работы Gson, Coroutines и читаемых краш-логов
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes SourceFile, LineNumberTable

# 2. КРИТИЧЕСКИ ВАЖНО: Сохраняем все сетевые DTO и модели пакета network (многие из них не имеют @SerializedName)
-keep class com.taxiapp.driver.network.** { *; }
-keepnames class com.taxiapp.driver.network.**

# 3. Сохраняем все классы, передаваемые через Intent.putExtra(..., Serializable)
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 4. Правила для Google Gson
-keep class com.google.gson.stream.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 5. Правила для Retrofit 2 и OkHttp 3
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**

# 6. Правила для STOMP WebSocket и RxJava 2
-keep class ua.naiksoftware.stomp.** { *; }
-dontwarn io.reactivex.**
-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
    long producerIndex;
    long consumerIndex;
}