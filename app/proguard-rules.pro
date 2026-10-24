# CopyTranslate 混淆规则（当前 minifyEnabled=false，规则保留备用）

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# 保留翻译源实现类（反射不使用，但防止裁剪）
-keep class com.copytranslate.app.translate.provider.** { *; }
