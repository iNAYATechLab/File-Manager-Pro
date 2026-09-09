package com.inayatechlab.filemanagerpro

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI flow tests over the drawer navigation (UI/Design redesign).
 *
 * All assertions are permission-free: they exercise drawer/fragment wiring
 * and the vault overview chrome, not storage listings.
 */
@RunWith(AndroidJUnit4::class)
class TabFlowTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private fun openDrawer() {
        onView(withContentDescription(R.string.nav_drawer_open)).perform(click())
    }

    /** Opens the drawer and taps the given drawer row. */
    private fun clickDrawerRow(rowId: Int) {
        openDrawer()
        onView(withId(rowId)).perform(scrollTo(), click())
    }

    @Test
    fun vaultRow_showsCreateFab_andReturnsHome() {
        clickDrawerRow(R.id.drowVault)
        // Vault overview: create button is always present, regardless of the
        // storage permission state.
        onView(withId(R.id.fabCreate)).check(matches(isDisplayed()))
        onView(withId(R.id.fabCreate)).check(matches(isEnabled()))

        clickDrawerRow(R.id.drowHome)
        onView(allOf(withId(R.id.recycler), isDisplayed())).check(matches(isDisplayed()))
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
    }

    @Test
    fun allSections_roundTripWithoutCrash() {
        val sections = listOf(
            R.id.drowImages to R.id.chipRecycler,
            R.id.drowFavorites to R.id.chipFavorites,
            R.id.drowVault to R.id.fabCreate,
            R.id.drowHome to R.id.recycler
        )
        for ((row, marker) in sections) {
            clickDrawerRow(row)
            // marker ids (esp. recycler) exist in every hidden fragment too,
            // so require the visible instance of the current section.
            onView(allOf(withId(marker), isDisplayed())).check(matches(isDisplayed()))
        }
    }

    @Test
    fun libraryRow_showsQuickAccessChips() {
        clickDrawerRow(R.id.drowFavorites)
        onView(withId(R.id.chipFavorites)).check(matches(isDisplayed()))
        onView(withId(R.id.chipRecents)).check(matches(isDisplayed()))
        onView(withId(R.id.chipDownloads)).check(matches(isDisplayed()))

        clickDrawerRow(R.id.drowHome)
        onView(allOf(withId(R.id.recycler), isDisplayed())).check(matches(isDisplayed()))
    }

    @Test
    fun categoriesRow_showsChipRow() {
        clickDrawerRow(R.id.drowImages)
        onView(withId(R.id.chipRecycler)).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.recycler), isDisplayed())).check(matches(isDisplayed()))
    }
}
