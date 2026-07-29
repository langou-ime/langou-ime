/*
 * SPDX-FileCopyrightText: 2026 Langou Input Method contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.langou.update

import com.osfans.trime.langou.network.ReleaseManifest

enum class ReleaseDecision {
    Current,
    Optional,
    Mandatory,
    Rejected,
}

object ReleaseUpdatePolicy {
    fun evaluate(
        currentVersion: String,
        manifest: ReleaseManifest,
    ): ReleaseDecision {
        if (manifest.platform != "android") return ReleaseDecision.Rejected
        val current = SemVer.parse(currentVersion) ?: return ReleaseDecision.Rejected
        val latest = SemVer.parse(manifest.version) ?: return ReleaseDecision.Rejected
        val minimum =
            SemVer.parse(manifest.minimumSupportedVersion) ?: return ReleaseDecision.Rejected
        return when {
            latest <= current -> ReleaseDecision.Current
            manifest.mandatory || current < minimum -> ReleaseDecision.Mandatory
            else -> ReleaseDecision.Optional
        }
    }
}

private data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)

    companion object {
        private val pattern = Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$""")

        fun parse(value: String): SemVer? {
            val match = pattern.matchEntire(value) ?: return null
            return runCatching {
                SemVer(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                )
            }.getOrNull()
        }
    }
}
