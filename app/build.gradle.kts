plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 版本号由 CI 注入：gradle assembleRelease -PversionName=x.y.z -PversionCode=N
// 本地默认值仅用于 IDE 索引，正式版本以 git tag（semver）为准
val appVersionName = (project.findProperty("versionName") as String?) ?: "0.1.0"
val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 100

// CI 固定签名（GitHub Secrets 注入），保证各版本 APK 签名一致、可覆盖安装；
// 本地构建无环境变量时回退默认 debug 密钥
// 注意：变量名不能叫 keyAlias/keyPassword —— 在 signingConfigs DSL 里会遮蔽 receiver 属性导致自赋值 null
val ciKeystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
val ciKeystorePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
val ciKeyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "flowervault"
// key 密码未配置时回退为 store 密码（两者相同的常见场景）
val ciKeyPassword = System.getenv("SIGNING_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: ciKeystorePassword
val hasCiKeystore = !ciKeystorePath.isNullOrBlank() && !ciKeystorePassword.isNullOrBlank()

android {
    namespace = "com.tools.flowervault"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.tools.flowervault"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasCiKeystore) {
            create("ci") {
                storeFile = file(ciKeystorePath!!)
                storePassword = ciKeystorePassword
                keyAlias = ciKeyAlias
                keyPassword = ciKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasCiKeystore) signingConfig = signingConfigs.getByName("ci")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasCiKeystore) signingConfig = signingConfigs.getByName("ci")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // 应用内更新检测与 APK 下载
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
