package com.farrah.sproutvision.analyze

/**
 * Compile-only stand-in for :app's MetaAnalyzer (which is Android-bound).
 * SamClient references only the BASE_URL const (inlined at compile time),
 * so the tool links this instead. The value MUST match the real one.
 */
object MetaAnalyzer {
    const val BASE_URL = "https://api.meta.ai/v1"
}
