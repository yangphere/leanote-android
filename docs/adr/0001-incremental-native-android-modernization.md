# 渐进式原生 Android 现代化

客户端继续采用原生 Android，并以 Kotlin、Jetpack Compose、分层数据架构、单向数据流、ViewModel、Coroutines/Flow 和 Hilt 作为目标形态，而不迁移到跨平台框架。迁移按可运行的纵向切片渐进完成，期间保留单一 `:app` 模块；只有出现明确的构建、复用或团队协作需求后才拆分 Gradle 模块，以避免一次性重写同时放大数据、同步和交付风险。
