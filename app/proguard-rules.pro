# Keep line numbers so crash stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
# Retrofit reads generic return types (ApiResponse<T>) from method signatures.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-renamesourcefileattribute SourceFile

# Moshi models use generated adapters (@JsonClass(generateAdapter = true));
# keep model class names so the generated *JsonAdapter lookups resolve.
-keep class com.fuwanime.data.remote.** { *; }
# Backup file models (also @JsonClass): the backup format must survive minification.
-keep class com.fuwanime.data.backup.** { *; }
-keep class com.fuwanime.data.local.*Entity* { *; }

# Retrofit service interfaces are accessed via reflection.
-keep,allowobfuscation interface com.fuwanime.data.remote.AnimeApiService
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# MangaDex DTOs (@JsonClass): keep so generated Moshi adapters resolve after R8.
-keep class com.fuwanime.data.manga.** { *; }
