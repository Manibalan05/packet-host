package com.alan.app

import com.alan.app.tunnel.parsePublicUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JUnit4 tests for the quick-tunnel URL parser. No Android classes.
 */
class CloudflaredParserTest {

    @Test
    fun validLineYieldsUrl() {
        val line = "2026-09-19T12:00:00Z INF Your quick Tunnel has been created! " +
            "https://bright-panda-demo.trycloudflare.com"
        assertEquals("https://bright-panda-demo.trycloudflare.com", parsePublicUrl(line))
    }

    @Test
    fun lineWithPrefixSpacesYieldsUrl() {
        val line = "   INF Thanks! Your tunnel is available at https://demo-8080-trycloudflare-com.trycloudflare.com"
        assertEquals(
            "https://demo-8080-trycloudflare-com.trycloudflare.com",
            parsePublicUrl(line)
        )
    }

    @Test
    fun multipleUrlsPicksFirstHttpsTrycloudflare() {
        val line = "see https://first-abc-123.trycloudflare.com and https://second-xyz-789.trycloudflare.com"
        assertEquals("https://first-abc-123.trycloudflare.com", parsePublicUrl(line))
    }

    @Test
    fun nonMatchingLinesYieldNull() {
        assertNull(parsePublicUrl("INF Waiting for quick Tunnel to come up..."))
        assertNull(parsePublicUrl("ERR Failed to connect to edge, retrying..."))
        assertNull(parsePublicUrl("some random log line without any url"))
        assertNull(parsePublicUrl(""))
        assertNull(parsePublicUrl("   "))
        assertNull(parsePublicUrl(null))
        assertNull(parsePublicUrl("https://example.com/plain-site"))
    }

    @Test
    fun httpNonTlsLookalikeIsRejected() {
        assertNull(parsePublicUrl("INF available at http://sneaky-thing.trycloudflare.com"))
        assertNull(parsePublicUrl("http://demo-8080-trycloudflare-com.trycloudflare.com"))
    }
}
