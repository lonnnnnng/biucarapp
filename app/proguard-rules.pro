# long：Media3 自带 consumer rules 已覆盖运行时反射入口；保留整个库会阻断 R8 对旧车机 APK 的裁剪。
-dontwarn org.conscrypt.**
