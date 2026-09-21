package com.alan.app

import com.alan.app.engine.RouteTarget
import com.alan.app.engine.resolveRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProxyRouterTest {

    @Test
    fun blankHostMatchesAnyHost() {
        val routes = listOf(RouteTarget("", "/", 3001))
        assertEquals(3001, resolveRoute("anything.example.com", "/", routes))
        assertEquals(3001, resolveRoute("192.168.1.5:8080", "/page", routes))
    }

    @Test
    fun exactHostRuleWinsOverCatchAll() {
        val routes = listOf(
            RouteTarget("", "/", 3001),
            RouteTarget("blog.example.com", "/", 3002)
        )
        assertEquals(3002, resolveRoute("blog.example.com", "/", routes))
        assertEquals(3001, resolveRoute("other.example.com", "/", routes))
    }

    @Test
    fun longestPrefixWins() {
        val routes = listOf(
            RouteTarget("", "/", 3001),
            RouteTarget("", "/blog", 3002),
            RouteTarget("", "/blog/2024", 3003)
        )
        assertEquals(3001, resolveRoute("x.test", "/", routes))
        assertEquals(3002, resolveRoute("x.test", "/blog", routes))
        assertEquals(3002, resolveRoute("x.test", "/blog/hello", routes))
        assertEquals(3003, resolveRoute("x.test", "/blog/2024/jan", routes))
    }

    @Test
    fun prefixMustBePathSegment() {
        val routes = listOf(RouteTarget("", "/blog", 3002))
        // "/blogroll" is not under "/blog" — no match.
        assertNull(resolveRoute("x.test", "/blogroll", routes))
        assertEquals(3002, resolveRoute("x.test", "/blog", routes))
    }

    @Test
    fun hostMatchIsCaseInsensitiveAndIgnoresPort() {
        val routes = listOf(RouteTarget("App.Example.COM", "/", 3005))
        assertEquals(3005, resolveRoute("app.example.com:8080", "/", routes))
    }

    @Test
    fun noMatchReturnsNull() {
        val routes = listOf(RouteTarget("a.test", "/docs", 3001))
        assertNull(resolveRoute("b.test", "/", routes))
        assertNull(resolveRoute("a.test", "/other", routes))
        assertNull(resolveRoute("x.test", "/", emptyList()))
    }

    @Test
    fun samePrefixPrefersNamedHost() {
        val routes = listOf(
            RouteTarget("", "/app", 3001),
            RouteTarget("app.test", "/app", 3002)
        )
        assertEquals(3002, resolveRoute("app.test", "/app", routes))
        assertEquals(3001, resolveRoute("other.test", "/app", routes))
    }
}
