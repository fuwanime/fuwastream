package com.fuwanime.ui.components

/** What TalkBack reads for one poster card, e.g. "Frieren, Episode 5, Baru, 2 baru, dipilih". */
fun posterDescription(title: String, caption: String?, badge: String?, isNew: Boolean, airing: Boolean, selected: Boolean): String =
    listOfNotNull(
        title,
        caption?.takeIf { it.isNotBlank() },
        "Baru".takeIf { isNew },
        "Sedang tayang".takeIf { airing && !isNew },
        badge?.takeIf { it.isNotBlank() },
        "dipilih".takeIf { selected },
    ).joinToString(", ")
