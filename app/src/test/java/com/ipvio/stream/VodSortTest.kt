package com.ipvio.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class VodSortTest {
    private fun movie(url: String, title: String) = Item(url.hashCode().toString(), Kind.MOVIE, title, null, url, null, "G")

    @Test fun sortsByProviderRatingAndAdded() {
        val c = VodMeta.Collector()
        c.add("http://h/movie/u/p/1.mp4", 5.0, 100L)
        c.add("http://h/movie/u/p/2.mp4", 8.0, 50L)
        c.add("http://h/movie/u/p/3.mp4", 6.5, 300L)
        val l = listOf(movie("http://h/movie/u/p/1.mp4", "Beta"), movie("http://h/movie/u/p/2.mp4", "Alpha"), movie("http://h/movie/u/p/3.mp4", "Gamma"))
        assertEquals(listOf("Alpha", "Beta", "Gamma"), VodPrefs.sorted(l, 1).map { it.title })
        assertEquals(listOf("Alpha", "Gamma", "Beta"), VodPrefs.sorted(l, 2).map { it.title })
        assertEquals(listOf("Gamma", "Beta", "Alpha"), VodPrefs.sorted(l, 3).map { it.title })
        assertEquals(listOf("Beta", "Alpha", "Gamma"), VodPrefs.sorted(l, 0).map { it.title })
    }
}
