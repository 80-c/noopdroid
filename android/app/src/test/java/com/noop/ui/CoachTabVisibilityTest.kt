package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CoachTabVisibilityTest {
    @Test
    fun coachAppearsOnlyInTheProfileMenuAndOnlyWhenEnabled() {
        val enabled = drawerGroups.flatMap { profileDestinations(it, coachEnabled = true) }
        val disabled = drawerGroups.flatMap { profileDestinations(it, coachEnabled = false) }
        assertEquals(1, enabled.count { it == Destination.Coach })
        assertEquals(enabled.filterNot { it == Destination.Coach }, disabled)
        assertFalse(primaryBarTabs.any { it.dest == Destination.Coach })
    }
}
