plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.distrava.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.distrava.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            // MapLibre traz bibliotecas nativas; limita às ABIs de aparelhos reais.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        debug {
            // Instala ao lado do app real, com dados próprios (prints e testes sem misturar percursos).
            applicationIdSuffix = ".debug"
        }
        release {
            // TODO: criar chave de release própria antes de distribuir. Por ora assina com a chave de
            // debug só para medir desempenho da build otimizada no aparelho.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }

    androidResources {
        // Glifos (.pbf) e mapas (.pmtiles) já são comprimidos; sem recompressão no APK.
        noCompress += listOf("pbf", "pmtiles")
    }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/bundledMap"))
}

// Mapa padrão embutido no APK (instalado na primeira abertura). Arquivo fora do git, em maps/.
// Outro mapa: ./gradlew assembleRelease -PdefaultMap=pernambuco
val defaultMapName = (findProperty("defaultMap") as String?) ?: "mata-sul-recife"
val defaultMapFile = rootProject.file("maps/$defaultMapName.pmtiles")
val copyDefaultMap by tasks.registering(Copy::class) {
    from(defaultMapFile)
    into(layout.buildDirectory.dir("generated/bundledMap/maps"))
    onlyIf {
        if (!defaultMapFile.exists()) {
            logger.warn("distrava: ${defaultMapFile.path} não existe; APK sem mapa embutido (veja docs/mapas.md).")
        }
        defaultMapFile.exists()
    }
}
tasks.named("preBuild") { dependsOn(copyDefaultMap) }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation("org.maplibre.gl:android-sdk:11.13.5")

    testImplementation("junit:junit:4.13.2")
}
