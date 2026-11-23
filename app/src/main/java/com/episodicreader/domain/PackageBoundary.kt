package com.episodicreader.domain

/**
 * Architectural boundary marker for pure Kotlin business logic (slicing, parsing, indexing, recaps, habits).
 * Classes in this package remain free of Android UI dependencies so they are unit-testable on the JVM.
 */
object DomainPackageBoundary
