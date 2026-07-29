package com.readmata.app.feed

/** Loads a source and maps it to the common feed representation. */
fun interface SourceAdapter {
    suspend fun load(sourceUrl: String): List<FeedItem>
}
