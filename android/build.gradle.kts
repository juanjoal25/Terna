// AGP 9 trae Kotlin integrado (ya no se aplica org.jetbrains.kotlin.android).
// Esta línea fija el compilador de Kotlin que usa AGP a la misma versión que el
// plugin de Compose, de serialización y KSP.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}
