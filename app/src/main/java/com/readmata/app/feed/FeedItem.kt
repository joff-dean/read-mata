package com.readmata.app.feed

/** A source-independent item that can be added to the reading queue. */
data class FeedItem(
    val id: String,
    val title: String,
    val link: String,
    val summary: String,
    /** The feed's date text, deliberately left unparsed. */
    val publishedAt: String?,
)
