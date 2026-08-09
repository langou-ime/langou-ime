/*
 * SPDX-FileCopyrightText: 2026 Langou contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectExtensionsTest {
    @Test
    fun `runCmd returns fallback when command exits non-zero`() {
        val project = ProjectBuilder.builder().build()

        val actual = project.runCmd("git config user.langou-missing", "fallback")

        assertEquals("fallback", actual)
    }
}
