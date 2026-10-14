plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.xai.adslibrary"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // GMA Next-Gen SDK (stable) – replaces old :sdk:0.3.0-alpha01
    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.5.0")

    // Optional: host may already include shimmer
    compileOnly("com.facebook.shimmer:shimmer:0.5.0")

    // Optional: host provides analytics via AnalyticsListener
    compileOnly("com.google.firebase:firebase-analytics-ktx:22.4.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = project.findProperty("GROUP_ID") as String? ?: "com.xai.adslibrary"
                artifactId = project.findProperty("ARTIFACT_ID") as String? ?: "ads-library"
                version = project.findProperty("VERSION_NAME") as String? ?: "1.0.3"

                pom {
                    name.set("Ads Library")
                    description.set("Reusable Android Ads SDK supporting Interstitial, Rewarded, Native, Banner and App-Open ads with premium handling, caching and main-thread callbacks.")
                    url.set("https://github.com/Ahsan-techzango/ads-sdk")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("Ahsan-techzango")
                            name.set("Ads SDK Team")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/Ahsan-techzango/ads-sdk.git")
                        developerConnection.set("scm:git:ssh://github.com/Ahsan-techzango/ads-sdk.git")
                        url.set("https://github.com/Ahsan-techzango/ads-sdk")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/Ahsan-techzango/ads-sdk")
                credentials {
                    username = project.findProperty("gpr.user") as String?
                        ?: System.getenv("GITHUB_ACTOR")
                    password = project.findProperty("gpr.key") as String?
                        ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
