package com.ipvio.stream

/**
 * TiviMate: Mit mehreren aktiven Wiedergabelisten zeigt die Gruppenübersicht jede Liste als eigenen Abschnitt
 * („XY Backup“ mit seinen Ordnern, darunter „XY Radio“ mit seinen). Schlüssel:
 * „P:<Liste>“ = alle Sender der Liste, „G:<Liste>\u0001<Gruppe>“ = eine Gruppe dieser Liste.
 */
object PlGroups {
    const val P = "P:"
    const val G = "G:"
    private const val SEP = '\u0001'

    class Section(val playlist: String, val count: Int, val groups: List<Pair<String, Int>>)

    fun keyP(pl: String) = P + pl
    fun keyG(pl: String, group: String) = G + pl + SEP + group
    fun isPl(key: String) = key.startsWith(P) || key.startsWith(G)

    /** Abschnitte in der Reihenfolge der Wiedergabelisten, Gruppen in der Reihenfolge der Liste. */
    fun sections(live: List<Item>): List<Section> {
        val byPl = LinkedHashMap<String, MutableList<Item>>()
        for (it in live) byPl.getOrPut(PlaylistStore.playlistOf(it.id) ?: "") { ArrayList() }.add(it)
        val order = PlaylistStore.activePlaylistNames()
        val names = order.filter { it in byPl } + byPl.keys.filter { it !in order }
        return names.map { pl ->
            val l = byPl[pl].orEmpty()
            Section(pl, l.size, l.groupBy { it.group }.map { (g, items) -> g to items.size })
        }
    }

    /** Sender zu einem Schlüssel, oder null, wenn es kein Wiedergabelisten-Schlüssel ist. */
    fun resolve(key: String, live: List<Item>): List<Item>? = when {
        key.startsWith(P) -> { val pl = key.removePrefix(P); live.filter { PlaylistStore.playlistOf(it.id) == pl } }
        key.startsWith(G) -> {
            val pl = key.removePrefix(G).substringBefore(SEP); val g = key.substringAfter(SEP)
            live.filter { it.group == g && PlaylistStore.playlistOf(it.id) == pl }
        }
        else -> null
    }

    /** Anzeigename (Liste bzw. Gruppe) oder null. */
    fun label(key: String): String? = when {
        key.startsWith(P) -> key.removePrefix(P)
        key.startsWith(G) -> key.substringAfter(SEP)
        else -> null
    }

    /** Wiedergabeliste eines Schlüssels oder null. */
    fun playlistOfKey(key: String): String? = when {
        key.startsWith(P) -> key.removePrefix(P)
        key.startsWith(G) -> key.removePrefix(G).substringBefore(SEP)
        else -> null
    }
}
