import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val googleBooksApiKey = localProperties.getProperty("GOOGLE_BOOKS_API_KEY", "")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val homeChatBaseUrl = localProperties.getProperty("HOMECHAT_LLM_BASE_URL", "").trim()
val homeChatModel = localProperties.getProperty("HOMECHAT_LLM_MODEL", "").trim()
val homeChatApiKey = localProperties.getProperty("HOMECHAT_LLM_API_KEY", "")

fun String.asBuildConfigString(): String = replace("\\", "\\\\").replace("\"", "\\\"")

val homeChatUri = homeChatBaseUrl.takeIf(String::isNotBlank)?.let(::URI)
val homeChatHost = homeChatUri?.host
require(homeChatBaseUrl.isBlank() || homeChatHost != null) {
    "HOMECHAT_LLM_BASE_URL must be an absolute URL with a host."
}
val generatedNetworkSecurityResources = layout.buildDirectory.dir("generated/res/networkSecurity/main")
// The Library of Congress SRU catalog endpoint is only available over HTTP.
val libraryOfCongressCatalogHost = "lx2.loc.gov"
val generateNetworkSecurityConfig = tasks.register("generateNetworkSecurityConfig") {
    inputs.property("libraryOfCongressCatalogHost", libraryOfCongressCatalogHost)
    inputs.property("homeChatHost", homeChatHost.orEmpty())
    inputs.property("homeChatUsesCleartext", homeChatUri?.scheme == "http")
    val outputFile = generatedNetworkSecurityResources.map {
        it.file("xml/network_security_config.xml")
    }
    outputs.file(outputFile)
    doLast {
        val catalogPolicy = """
            <domain-config cleartextTrafficPermitted="true">
                <domain includeSubdomains="false">$libraryOfCongressCatalogHost</domain>
            </domain-config>
        """.trimIndent()
        val hostPolicy = if (homeChatUri?.scheme == "http") {
            """
            <domain-config cleartextTrafficPermitted="true">
                <domain includeSubdomains="false">${requireNotNull(homeChatHost)}</domain>
            </domain-config>
            """.trimIndent()
        } else {
            ""
        }
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                <network-security-config>
                    <base-config cleartextTrafficPermitted="false" />
                    $catalogPolicy
                    $hostPolicy
                </network-security-config>
                """.trimIndent(),
            )
        }
    }
}

android {
    namespace = "com.example.bookcatalog"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.example.bookcatalog"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GOOGLE_BOOKS_API_KEY", "\"${googleBooksApiKey.asBuildConfigString()}\"")
        buildConfigField("String", "HOMECHAT_LLM_BASE_URL", "\"${homeChatBaseUrl.asBuildConfigString()}\"")
        buildConfigField("String", "HOMECHAT_LLM_MODEL", "\"${homeChatModel.asBuildConfigString()}\"")
        buildConfigField("String", "HOMECHAT_LLM_API_KEY", "\"${homeChatApiKey.asBuildConfigString()}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    sourceSets.getByName("main").res.srcDir(generatedNetworkSecurityResources)
}

tasks.named("preBuild").configure {
    dependsOn(generateNetworkSecurityConfig)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.moshi.kotlin)
    implementation(libs.coil.compose)
    implementation(libs.play.services.code.scanner)
    implementation(libs.androidx.exifinterface)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
