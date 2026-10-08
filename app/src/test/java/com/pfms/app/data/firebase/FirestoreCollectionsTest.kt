package com.pfms.app.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FirestoreCollections].
 *
 * Account deletion (FR-06) walks USER_SUBCOLLECTIONS, so forgetting to
 * register a new subcollection would silently leave orphaned data.
 */
class FirestoreCollectionsTest {

    @Test
    fun `users collection name is correct`() {
        assertEquals("users", FirestoreCollections.USERS)
    }

    @Test
    fun `subcollections list contains categories`() {
        assertTrue(FirestoreCollections.USER_SUBCOLLECTIONS.contains(FirestoreCollections.CATEGORIES))
    }

    @Test
    fun `subcollections list contains income sources`() {
        assertTrue(FirestoreCollections.USER_SUBCOLLECTIONS.contains(FirestoreCollections.INCOME_SOURCES))
    }

    @Test
    fun `subcollections list contains income`() {
        assertTrue(FirestoreCollections.USER_SUBCOLLECTIONS.contains(FirestoreCollections.INCOME))
    }

    @Test
    fun `subcollections list contains expenses`() {
        assertTrue(FirestoreCollections.USER_SUBCOLLECTIONS.contains(FirestoreCollections.EXPENSES))
    }

    @Test
    fun `subcollections list contains savings goals`() {
        assertTrue(FirestoreCollections.USER_SUBCOLLECTIONS.contains(FirestoreCollections.SAVINGS_GOALS))
    }

    @Test
    fun `subcollections list contains goal contributions`() {
        assertTrue(FirestoreCollections.USER_SUBCOLLECTIONS.contains(FirestoreCollections.GOAL_CONTRIBUTIONS))
    }

    @Test
    fun `subcollections list contains recurring templates`() {
        assertTrue(FirestoreCollections.USER_SUBCOLLECTIONS.contains(FirestoreCollections.RECURRING_TEMPLATES))
    }

    @Test
    fun `subcollections list contains reminders`() {
        assertTrue(FirestoreCollections.USER_SUBCOLLECTIONS.contains(FirestoreCollections.REMINDERS))
    }

    @Test
    fun `subcollections list has exactly eight entries`() {
        assertEquals(8, FirestoreCollections.USER_SUBCOLLECTIONS.size)
    }

    @Test
    fun `subcollections list has no duplicates`() {
        val list = FirestoreCollections.USER_SUBCOLLECTIONS
        assertEquals(list.size, list.distinct().size)
    }
}
