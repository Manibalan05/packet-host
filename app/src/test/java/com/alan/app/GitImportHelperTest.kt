package com.alan.app

import com.alan.app.util.GitImportHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitImportHelperTest {

    @Test
    fun publicUrlPassesThroughUnchanged() {
        assertEquals(
            "https://github.com/you/site.git",
            GitImportHelper.stripCredentialsFromUrl("https://github.com/you/site.git")
        )
    }

    @Test
    fun tokenAndUserPasswordUserinfoAreStripped() {
        assertEquals(
            "https://github.com/you/site.git",
            GitImportHelper.stripCredentialsFromUrl("https://ghp_abc123@github.com/you/site.git")
        )
        assertEquals(
            "https://github.com/you/site.git",
            GitImportHelper.stripCredentialsFromUrl("https://user:s3cret@github.com/you/site.git")
        )
    }

    @Test
    fun branchSuffixSplitsBasicUrl() {
        assertEquals(
            Pair("https://github.com/owner/repo", "live"),
            GitImportHelper.splitBranchSuffix("https://github.com/owner/repo#live")
        )
    }

    @Test
    fun branchSuffixSplitsDotGitUrl() {
        assertEquals(
            Pair("https://github.com/owner/repo.git", "live"),
            GitImportHelper.splitBranchSuffix("https://github.com/owner/repo.git#live")
        )
    }

    @Test
    fun urlWithoutSuffixYieldsNullBranch() {
        assertEquals(
            Pair("https://github.com/owner/repo", null),
            GitImportHelper.splitBranchSuffix("https://github.com/owner/repo")
        )
    }

    @Test
    fun trailingHashStripsHashAndYieldsNullBranch() {
        assertEquals(
            Pair("https://github.com/owner/repo", null),
            GitImportHelper.splitBranchSuffix("https://github.com/owner/repo#")
        )
    }

    @Test
    fun authFailureMapsToHelpfulMessageWithoutLeakingSecrets() {
        val cause = RuntimeException(
            "https://user:s3cret@github.com/you/site.git: 401 Unauthorized"
        )
        val msg = GitImportHelper.describeCloneError(
            cause,
            "https://user:s3cret@github.com/you/site.git"
        )
        assertTrue(msg.contains("Token rejected or no access to this repo"))
        assertTrue(msg.contains("Contents: read"))
        assertFalse(msg.contains("s3cret"))
    }
}
