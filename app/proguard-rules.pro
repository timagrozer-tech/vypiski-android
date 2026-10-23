# 291 Сканер — правила R8 для Release.
# OpenCV: JNI-классы вызываются из нативного кода.
-keep class org.opencv.** { *; }
# Tesseract4Android: JNI.
-keep class com.googlecode.tesseract.android.** { *; }
# PdfBox-Android: шрифты и фильтры загружаются рефлексией.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
# Room/WorkManager поставляют собственные consumer-rules.
