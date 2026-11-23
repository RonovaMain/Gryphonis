-keepattributes *Annotation*, SourceFile, LineNumberTable
-keep class org.apache.commons.net.** { *; }
-dontwarn org.apache.commons.net.**
-dontwarn javax.**
-dontwarn java.lang.invoke.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn androidx.media3.**

# commons-compress: необязательные зависимости (zstd, brotli, asm и т.п.) мы не подключаем
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.codec.**
-dontwarn org.apache.commons.io.**
-dontwarn org.apache.commons.lang3.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.tukaani.xz.**
-dontwarn org.osgi.**
# ContentProvider для стриминга файлов из архивов и FTP
-keep class dev.ronova.gryphonis.stream.StreamProvider { *; }
