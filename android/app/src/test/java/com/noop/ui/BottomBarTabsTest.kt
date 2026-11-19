package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarTabsTest {

    private val barTabs = primaryBarTabs

    /** The bar and the More sheet must be disjoint, which is the invariant the drawer's own note claims. */
    @Test
    fun noBarTabIsAlsoListedInTheMoreSheet() {
        val inDrawer = drawerGroups.flatMap { it.items }.toSet()
        val both = barTabs.map { it.dest }.filter { it in inDrawer }
        assertTrue("a bar tab must not also appear in the More sheet, found $both", both.isEmpty())
    }

    @Test
    fun theBarCarriesTheFourPrimaryDestinations() {
        assertEquals(
            listOf(Destination.Today, Destination.Sleep, Destination.Live, Destination.Trends),
            barTabs.map { it.dest },
        )
    }

    /** A destination can occupy only one slot. */
    @Test
    fun theBarHasNoDuplicateDestinations() {
        assertEquals(barTabs.map { it.dest }.distinct().size, barTabs.size)
    }

    /** Every tab carries a label, so no slot can render an empty word or an empty a11y description. */
    @Test
    fun everyTabHasALabelResource() {
        assertTrue(barTabs.all { it.labelRes != 0 })
    }
}
