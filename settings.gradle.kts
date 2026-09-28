rootProject.name = "HydraFit"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":shared")
include(":core:domain")
include(":core:navigation")
include(":core:userdata")
include(":core:database")
include(":core:network")
include(":feature:equipment")
include(":feature:splitbuilder")
include(":feature:fatigueheatmap")
include(":feature:logger")
include(":feature:settings")