import io.github.z4kn4fein.semver.toVersion
import io.github.z4kn4fein.semver.toVersionOrNull
import ir.amirab.git_version.core.semanticVersionRegex
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    ir.amirab.`git-version-plugin`
    /**
     * retrieve latest versions of dependencies
     */
    com.github.`ben-manes`.versions
}

val defaultSemVersion = "1.0.0"
val fallBackVersion = "$defaultSemVersion-untagged"

gitVersion {
    on {
        branch(".+") {
            "$defaultSemVersion-${it.refInfo.shortenName}-snapshot"
        }
        tag("v?${semanticVersionRegex}") {
            it.matchResult.groups.get("version")!!.value
        }
        commit {
            "$defaultSemVersion-sha.${it.refInfo.commitHash.take(5)}"
        }
    }
}
version = (gitVersion.getVersion() ?: fallBackVersion).toVersion()
logger.lifecycle("version: $version")

tasks.dependencyUpdates {
    revision = "release"
    outputFormatter = "html"
    rejectVersionIf {
        val candidateVersion = candidate.version.toVersionOrNull() ?: return@rejectVersionIf true
        !candidateVersion.isStable
    }
}


// The upstream project currently contains a large set of legacy Kotlin compiler warnings.
// Keep CI output clean while retaining Gradle failures for actual compilation errors.
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions.suppressWarnings.set(true)
}
