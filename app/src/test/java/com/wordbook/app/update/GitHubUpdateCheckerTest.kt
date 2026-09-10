package com.wordbook.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCheckerTest {
    @Test
    fun comparesSemanticVersions() {
        assertTrue(VersionComparator.isNewer("0.4.3", "0.4.2"))
        assertTrue(VersionComparator.isNewer("1.0", "0.9.9"))
        assertFalse(VersionComparator.isNewer("0.4.3", "0.4.3"))
        assertFalse(VersionComparator.isNewer("0.4.2", "0.4.3"))
        assertFalse(VersionComparator.isNewer("invalid", "0.4.3"))
    }

    @Test
    fun parsesStableNewRelease() {
        val update = GitHubUpdateChecker.parseRelease(
            """
            {
              "tag_name": "v0.4.4",
              "name": "生词本 0.4.4",
              "body": "更新说明",
              "html_url": "https://github.com/xsy070709/wordbook/releases/tag/v0.4.4",
              "draft": false,
              "prerelease": false
            }
            """.trimIndent(),
            currentVersion = "0.4.3",
        )

        assertEquals("0.4.4", update?.version)
        assertEquals("更新说明", update?.notes)
    }

    @Test
    fun ignoresCurrentPrereleaseAndUntrustedUrl() {
        assertNull(
            GitHubUpdateChecker.parseRelease(
                """{"tag_name":"v0.4.3","html_url":"https://github.com/example","draft":false,"prerelease":false}""",
                currentVersion = "0.4.3",
            ),
        )
        assertNull(
            GitHubUpdateChecker.parseRelease(
                """{"tag_name":"v0.5.0","html_url":"https://github.com/example","draft":false,"prerelease":true}""",
                currentVersion = "0.4.3",
            ),
        )
        val update = GitHubUpdateChecker.parseRelease(
            """{"tag_name":"v0.5.0","html_url":"https://example.com/fake","draft":false,"prerelease":false}""",
            currentVersion = "0.4.3",
        )
        assertEquals("https://github.com/xsy070709/wordbook/releases", update?.releaseUrl)
    }
}
