package com.alan.app.util

/**
 * Pure helpers for git import with optional private-repo token auth.
 *
 * The token itself is intentionally absent here: it is passed in memory only
 * from the import form to JGit and is never logged, persisted, or embedded
 * in a URL.
 */
object GitImportHelper {

    private val USERINFO_RE = Regex("^(https?://)[^/\\s]*@")

    /**
     * Strip `user[:password]@` credentials from an http(s) URL so it is safe
     * to show in toasts, error cards, and log lines.
     * Public URLs (and non-http URLs) pass through unchanged.
     */
    fun stripCredentialsFromUrl(raw: String): String =
        USERINFO_RE.replace(raw, "$1")

    /**
     * Split an optional `#branch` suffix off a repo URL.
     *
     * Splits on the LAST `#` only when a non-empty branch follows and the
     * left part still looks like an http(s) URL; otherwise returns the
     * trimmed input with a null branch. A trailing `#` (empty branch) is
     * stripped and also yields a null branch.
     *
     * NOTE: `#` inside userinfo (e.g. a password containing `#`) is out of
     * scope — such a URL would mis-split, so passwords with `#` are not
     * supported here. The token flow passes credentials via
     * CredentialsProvider, never in the URL, so this does not affect it.
     */
    fun splitBranchSuffix(raw: String): Pair<String, String?> {
        val trimmed = raw.trim()
        val hashIdx = trimmed.lastIndexOf('#')
        if (hashIdx == -1) return trimmed to null
        val left = trimmed.substring(0, hashIdx).trim()
        val branch = trimmed.substring(hashIdx + 1).trim()
        val leftIsHttp = left.startsWith("http://") || left.startsWith("https://")
        if (branch.isEmpty()) {
            // Trailing '#': strip it when the left part is a URL, else pass through.
            return if (leftIsHttp) left to null else trimmed to null
        }
        return if (leftIsHttp) left to branch else trimmed to null
    }

    /** True when the failure looks like rejected/missing credentials (401/403). */
    fun isAuthFailure(cause: Throwable?): Boolean {
        var current = cause
        var guard = 0
        while (current != null && guard++ < 10) {
            val msg = current.message?.lowercase().orEmpty()
            if (msg.contains("401") ||
                msg.contains("403") ||
                msg.contains("unauthorized") ||
                msg.contains("not authorized") ||
                msg.contains("authentication") ||
                msg.contains("auth fail") ||
                msg.contains("forbidden")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    /**
     * True when the failure looks like a missing remote branch for [branch].
     * Matches JGit's RefNotFound-style failures ("Remote branch
     * 'refs/heads/X' not found", "Ref X cannot be resolved", ...) without
     * misfiring on unrelated "not found" errors: the message must mention
     * the branch (or refs/heads/<branch>), carry a missing/invalid-ref
     * signal, or be a RefNotFound exception type.
     */
    private fun isBranchNotFound(cause: Throwable?, branch: String): Boolean {
        val wanted = branch.lowercase()
        var current = cause
        var guard = 0
        while (current != null && guard++ < 10) {
            val simpleName = current.javaClass.simpleName.lowercase()
            if (simpleName.contains("refnotfound")) return true
            val msg = current.message.orEmpty()
            val lower = msg.lowercase()
            val mentionsBranch = lower.contains(wanted) || lower.contains("refs/heads/$wanted")
            val looksMissing = lower.contains("not found") ||
                lower.contains("no such") ||
                lower.contains("unknown branch") ||
                lower.contains("cannot resolve") ||
                lower.contains("could not resolve") ||
                lower.contains("couldn't find") ||
                lower.contains("cannot find") ||
                lower.contains("invalid ref") ||
                lower.contains("bad ref")
            if (mentionsBranch && looksMissing) return true
            if (lower.contains("remote branch") && lower.contains("not found")) return true
            current = current.cause
        }
        return false
    }

    /**
     * User-facing clone error text. Never contains the token: JGit receives it
     * via CredentialsProvider (not the URL), and any credential-looking URL
     * fragment in the exception message is redacted.
     *
     * When [branch] is non-null (the `#branch` suffix form), branch-not-found
     * failures surface an honest message naming the branch.
     */
    fun describeCloneError(cause: Throwable?, repoUrl: String, branch: String? = null): String {
        if (branch != null && isBranchNotFound(cause, branch)) {
            return "Branch '$branch' not found in this repo — check the name after #"
        }
        if (isAuthFailure(cause)) {
            return "Token rejected or no access to this repo — " +
                "check token scope (Contents: read) and repo name"
        }
        val redacted = cause?.message
            ?.let { stripCredentialsFromUrl(it) }
            ?.takeIf { it.isNotBlank() }
            ?: "check the link and try again"
        // Include the (redacted) URL only as a fallback hint, never raw.
        return if (cause?.message.isNullOrBlank()) {
            "Clone failed: $redacted (${stripCredentialsFromUrl(repoUrl)})"
        } else {
            "Clone failed: $redacted"
        }
    }
}
