pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// 语音转文字依赖 JitPack：com.github.Mencaje:mengchuangjianghe-asr:android:1.3.0
// 本地开发时可取消下一行注释并克隆 ASR 到上级目录：includeBuild("../mengchuangjianghe-asr")
rootProject.name = "My Application"
include(":app")
 