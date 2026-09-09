package com.inayatechlab.filemanagerpro

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inayatechlab.filemanagerpro.util.StorageUtils
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test (#16): the app launches to the Storage home with the listing
 * rendered, tabs switch, and rotation does not crash the activity.
 *
 * Storage permissions cannot be granted on API 30+ emulators, so assertions
 * that need real listings are gated on StorageUtils.isStoragePermitted.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)


    @Test
    fun launch_showsStorageHomeListing() {
        // Default tab is Storage: the browse list (or its empty state) renders.
        onView(withId(R.id.recycler)).check(matches(isDisplayed()))
        onView(withId(R.id.nav_storage)).check(matches(isDisplayed()))

        // When the device granted storage access, volume cards are rendered.
        if (storagePermitted()) {
            androidx.test.espresso.Espresso.onView(
                allOf(withId(R.id.recycler), isDisplayed())
            ).check(
                matches(
                    androidx.test.espresso.matcher.ViewMatchers.hasDescendant(
                        withText("Internal storage")
                    )
                )
            )
        }
    }

    @Test
    fun tabSwitchToCategories_rendersChipsAndList() {
        onView(withId(R.id.nav_categories)).perform(androidx.test.espresso.action.ViewActions.click())
        // Category chips + item list are on screen after switching.
        onView(withId(R.id.chipRecycler)).check(matches(isDisplayed()))
        onView(withId(R.id.recycler)).check(matches(isDisplayed()))

        onView(withId(R.id.nav_storage)).perform(androidx.test.espresso.action.ViewActions.click())
        onView(withId(R.id.recycler)).check(matches(isDisplayed()))
    }

    @Test
    fun rotation_doesNotCrash() {
        // Open Categories, rotate, come back to Storage.
        onView(withId(R.id.nav_categories)).perform(androidx.test.espresso.action.ViewActions.click())
        activityRule.scenario.recreate()
        onView(withId(R.id.chipRecycler)).check(matches(isDisplayed()))

        onView(withId(R.id.nav_storage)).perform(androidx.test.espresso.action.ViewActions.click())
        activityRule.scenario.recreate()
        onView(withId(R.id.recycler)).check(matches(isDisplayed()))
    }

    private fun storagePermitted(): Boolean {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        return StorageUtils.isStoragePermitted(ctx)
    }
}
