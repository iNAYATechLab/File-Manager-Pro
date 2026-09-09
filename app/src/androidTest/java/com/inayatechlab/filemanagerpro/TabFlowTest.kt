package com.inayatechlab.filemanagerpro

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matchers.allOf
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI flow tests over the bottom navigation (1.0.0 hardening, #22).
 *
 * All assertions are permission-free: they exercise fragment/tab wiring and
 * the vault overview chrome, not storage listings.
 */
@RunWith(AndroidJUnit4::class)
class TabFlowTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun vaultTab_showsCreateFab_andReturnsToStorage() {
        onView(withId(R.id.nav_vault)).perform(click())
        // Vault overview: create button is always present, regardless of the
        // storage permission state.
        onView(withId(R.id.fabCreate)).check(matches(isDisplayed()))
        onView(withId(R.id.fabCreate)).check(matches(isEnabled()))

        onView(withId(R.id.nav_storage)).perform(click())
        onView(allOf(withId(R.id.recycler), isDisplayed())).check(matches(isDisplayed()))
        onView(withId(R.id.nav_storage)).check(matches(isDisplayed()))
    }

    @Test
    fun allTabs_roundTripWithoutCrash() {
        val tabs = intArrayOf(
            R.id.nav_categories,
            R.id.nav_vault,
            R.id.nav_storage
        )
        for (tab in tabs) {
            onView(withId(tab)).perform(click())
            onView(withId(tab)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun categories_tabShowsChipRow() {
        onView(withId(R.id.nav_categories)).perform(click())
        onView(withId(R.id.chipRecycler)).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.recycler), isDisplayed())).check(matches(isDisplayed()))
    }
}
