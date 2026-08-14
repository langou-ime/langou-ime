// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.task
import org.jetbrains.kotlin.com.google.common.hash.Hashing
import org.jetbrains.kotlin.com.google.common.io.ByteSource
import java.io.File
import java.nio.charset.Charset
import kotlin.collections.set

/**
 * Add task generateDataChecksums
 */
class DataChecksumsPlugin : Plugin<Project> {
    companion object {
        const val TASK = "generateDataChecksums"
        const val CLEAN_TASK = "cleanDatacheksums"
        const val FILE_NAME = "checksums.json"
    }

    override fun apply(target: Project) {
        target.tasks.register<DataChecksumsTask>(TASK) {
            inputDirs.from(
                target.assetsDir,
                target.layout.buildDirectory.dir("generated/rimeAssets"),
            )
            outputFile.set(target.assetsDir.resolve(FILE_NAME))
        }
        target.tasks.register<Delete>(CLEAN_TASK) {
            delete(target.assetsDir.resolve(FILE_NAME))
        }.also {
            target.tasks.findByName("clean")?.dependsOn(it)
        }
    }

    abstract class DataChecksumsTask : DefaultTask() {
        @Serializable
        data class DataChecksums(
            val sha256: String,
            val files: Map<String, String>,
        )

        @get:PathSensitive(PathSensitivity.RELATIVE)
        @get:InputFiles
        abstract val inputDirs: ConfigurableFileCollection

        @get:OutputFile
        abstract val outputFile: RegularFileProperty

        private val file by lazy { outputFile.get().asFile }

        private fun serialize(files: Map<String, String>) {
            val checksums =
                DataChecksums(
                    Hashing
                        .sha256()
                        .hashString(
                            files.entries.joinToString { it.key + it.value },
                            Charset.defaultCharset(),
                        ).toString(),
                    files,
                )
            file.writeText(json.encodeToString(checksums))
        }

        companion object {
            fun sha256(file: File): String = ByteSource.wrap(file.readBytes()).hash(Hashing.sha256()).toString()
        }

        @TaskAction
        fun execute() {
            val map = mutableMapOf<String, String>()
            val output = file.canonicalFile

            inputDirs.files.filter(File::isDirectory).sortedBy(File::getAbsolutePath).forEach { root ->
                root.walkTopDown().filter(File::isFile).sortedBy(File::getAbsolutePath).forEach assetLoop@{ asset ->
                    if (asset.canonicalFile == output) return@assetLoop
                    val key = asset.relativeTo(root).invariantSeparatorsPath
                    val hash = sha256(asset)
                    val previous = map.putIfAbsent(key, hash)
                    require(previous == null || previous == hash) {
                        "Conflicting asset path '$key' in checksum inputs"
                    }
                }
            }

            map.keys.toList().forEach { relativePath ->
                generateSequence(File(relativePath).parentFile) { it.parentFile }
                    .map { it.invariantSeparatorsPath }
                    .filter(String::isNotBlank)
                    .forEach { map.putIfAbsent(it, "") }
            }
            serialize(map.toSortedMap())
        }
    }
}
