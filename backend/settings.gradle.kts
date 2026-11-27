// 플러그인도 Google 미러를 먼저 쓴다 (배포 빌드에서 Maven Central이 429로 막을 때가 있음)
pluginManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
    }
}

rootProject.name = "yangstagram"
