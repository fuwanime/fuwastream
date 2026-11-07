package com.fuwanime

import com.fuwanime.data.manga.DohAnswer
import com.fuwanime.data.manga.DohResponse
import com.fuwanime.data.manga.MangaDexDns
import com.fuwanime.data.manga.dohAddresses
import com.fuwanime.data.manga.isMangaDexHost
import java.net.InetAddress
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaDexDnsTest {
    private val blocked = object : Dns { // internetpositif.id
        override fun lookup(hostname: String) = listOf(InetAddress.getByName("36.86.63.185"))
    }
    private val answer = DohResponse(0, listOf(DohAnswer(5, 300, "dxlb.mangadex.org."), DohAnswer(1, 2, "45.129.229.1"), DohAnswer(1, 2, "45.129.229.2")))

    @Test fun mangaDexHosts() {
        assertTrue(isMangaDexHost("api.mangadex.org"))
        assertTrue(isMangaDexHost("uploads.mangadex.org"))
        assertTrue(isMangaDexHost("abc123.xyz.mangadex.network"))
        assertFalse(isMangaDexHost("xyz-api.animein.net"))
        assertFalse(isMangaDexHost("evilmangadex.org"))
    }

    @Test fun addressesSkipCname() = assertEquals(listOf("45.129.229.1", "45.129.229.2"), dohAddresses(answer))

    @Test fun failedStatusHasNoAddresses() = assertEquals(emptyList<String>(), dohAddresses(answer.copy(status = 2)))

    @Test fun mangaDexResolvedOverDohNotIspDns() {
        val dns = MangaDexDns(fallback = blocked, query = { answer })
        assertEquals(listOf("45.129.229.1", "45.129.229.2"), dns.lookup("api.mangadex.org").map { it.hostAddress })
    }

    @Test fun otherHostsUseSystemDns() {
        var asked = false
        val dns = MangaDexDns(fallback = blocked, query = { asked = true; answer })
        assertEquals("36.86.63.185", dns.lookup("xyz-api.animein.net").single().hostAddress)
        assertFalse(asked)
    }

    @Test fun dohFailureFallsBack() {
        val dns = MangaDexDns(fallback = blocked, query = { error("offline") })
        assertEquals("36.86.63.185", dns.lookup("api.mangadex.org").single().hostAddress)
    }

    @Test fun cachedUntilTtl() {
        var calls = 0
        var t = 0L
        val dns = MangaDexDns(fallback = blocked, now = { t }, query = { calls++; answer })
        dns.lookup("api.mangadex.org"); dns.lookup("api.mangadex.org")
        assertEquals(1, calls)
        t += 61_000
        dns.lookup("api.mangadex.org")
        assertEquals(2, calls)
    }
}
