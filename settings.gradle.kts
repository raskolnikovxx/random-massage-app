// Bu blok, Gradle'ın kendisi için gereken eklentileri (plugin) nerede arayacağını söyler.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// BU BLOK EN ÖNEMLİSİ:
// Bu blok, projenizin "implementation" ile eklediğiniz kütüphaneleri (dependency)
// nerede arayacağını söyler.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Hatanın çözümü olan satır budur. Projeye JitPack'i de kontrol etmesini söyler.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "HakanBs"
include(":app")