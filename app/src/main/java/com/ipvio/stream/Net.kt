package com.ipvio.stream

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Ein gemeinsamer HTTP-Client für die ganze App. Abgeleitete Clients (andere Zeitlimits) teilen
 * Verbindungs-Pool und Threads, statt dass jeder Bereich eigene anlegt – spart Speicher auf schwachen Geräten.
 */
object Net {
    // Grundlage: Namensauflösung über [SecureDns], Zeitlimits wie bisher
    private val root: OkHttpClient = OkHttpClient.Builder()
        .dns(SecureDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Allgemein > User-Agent (TiviMate): eigener User-Agent für alle Abrufe, sonst der bisherige
    val base: OkHttpClient = root.newBuilder()
        .addInterceptor { ch ->
            val custom = Tm.s(Tm.GEN_UA).trim()
            ch.proceed(if (custom.isEmpty()) ch.request() else ch.request().newBuilder().header("User-Agent", custom).build())
        }
        .build()

    /** Für den Player: User-Agent setzt die Datenquelle je Wiedergabeliste; folgt Weiterleitungen auch von http nach https. */
    val player: OkHttpClient = root.newBuilder().followRedirects(true).followSslRedirects(true).build()

    fun withTimeouts(connectSec: Long, readSec: Long): OkHttpClient =
        base.newBuilder().connectTimeout(connectSec, TimeUnit.SECONDS).readTimeout(readSec, TimeUnit.SECONDS).build()
}

/**
 * Sichere DNS-Abfrage (Wunsch des Nutzers, 2026-11-11; Allgemein > „Sichere DNS-Abfrage“, Vorgabe aus dem Panel):
 * Namen über DNS-over-HTTPS (Cloudflare, ersatzweise Quad9) auflösen statt über den DNS des Internetanbieters.
 * Aus = immer normal; An = immer verschlüsselt (bei Ausfall normal); Automatisch = normal, nur wenn das scheitert verschlüsselt.
 */
object SecureDns : Dns {
    const val OFF = 0
    const val ON = 1
    const val AUTO = 2

    /** Gewählte Einstellung; -1 = Vorgabe aus dem Panel. */
    fun mode(): Int = Tm.i(Tm.GEN_DNS, -1).let { if (it in OFF..AUTO) it else Presets.secureDns }

    private val boot by lazy { OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build() }
    private fun doh(url: String, vararg ips: String) = DnsOverHttps.Builder().client(boot).url(url.toHttpUrl())
        .bootstrapDnsHosts(ips.map { InetAddress.getByName(it) }).includeIPv6(false).build()
    private val resolvers by lazy {
        listOf(doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1"), doh("https://dns.quad9.net/dns-query", "9.9.9.9", "149.112.112.112"))
    }

    private fun viaDoh(host: String): List<InetAddress>? {
        for (r in resolvers) runCatching { r.lookup(host) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

    private val IP = Regex("""^[0-9.]+$|:""")

    override fun lookup(hostname: String): List<InetAddress> {
        // Feste IP-Adressen, lokale Namen und die DoH-Server selbst nie über DoH
        if (IP.containsMatchIn(hostname) || !hostname.contains('.') || hostname.endsWith(".local")) return Dns.SYSTEM.lookup(hostname)
        return when (mode()) {
            ON -> viaDoh(hostname) ?: Dns.SYSTEM.lookup(hostname)
            AUTO -> try { Dns.SYSTEM.lookup(hostname) } catch (e: UnknownHostException) { viaDoh(hostname) ?: throw e }
            else -> Dns.SYSTEM.lookup(hostname)
        }
    }
}
