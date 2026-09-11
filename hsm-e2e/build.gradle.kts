// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library") version "8.12.0"
    id("org.jetbrains.kotlin.android") version "2.2.21"
}

// Android instrumented test of the wallet HSM operations against a live `just up`
// ecosystem. Not part of the Maven build. See hsm-e2e/README.md.

val gatewayBaseUrl: String =
    (project.findProperty("gateway.base.url") as String?)
        ?: "http://10.0.2.2:8082/wallet-client-gateway"

android {
    namespace = "se.digg.wallet.ecosystem.hsm"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["gatewayBaseUrl"] = gatewayBaseUrl
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        animationsDisabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    androidTestImplementation("se.digg.wallet:access-mechanism:0.0.2-SNAPSHOT")
    androidTestImplementation("net.java.dev.jna:jna:5.18.1@aar") // access-mechanism's UniFFI bindings

    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    androidTestImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    androidTestImplementation("com.nimbusds:nimbus-jose-jwt:10.9.1") // transitive via access-mechanism; used directly

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
