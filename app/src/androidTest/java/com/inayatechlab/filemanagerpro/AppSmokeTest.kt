package com.inayatechlab.filemanagerpro

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
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
 * rendered, drawer sections switch, and rotation does not crash.
 *
 * Storage permissions cannot be granted on API 30+ emulators, so assertions
 * that need real listings are gated on StorageUtils.isStoragePermitted.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private fun openDrawer() {
        onView(withContentDescription(R.string.nav_drawer_open)).perform(click())
    }

    private fun clickDrawerRow(rowId: Int) {
        openDrawer()
        onView(withId(rowId)).perform(scrollTo(), click())
    }

    @Test
    fun launch_showsStorageHomeListing() {
        // Default screen is the Storage home: the browse list (or its empty
        // state) renders.
        onView(allOf(withId(R.id.recycler), isDisplayed())).check(matches(isDisplayed()))

        // When the device granted storage access, volume cards are rendered.
        if (storagePermitted()) {
            onView(allOf(withId(R.id.recycler), isDisplayed())).check(
                matches(
                    androidx.test.espresso.matcher.ViewMatchers.hasDescendant(
                        withText("Internal storage")
                    )
                )
            )
        }
    }

    @Test
    fun drawerToCategories_rendersChipsAndList() {
        clickDrawerRow(R.id.drowImages)
        // Category chips + item list are on screen after switching.
        onView(withId(R.id.chipRecycler)).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.recycler), isDisplayed())).check(matches(isDisplayed()))

        clickDrawerRow(R.id.drowHome)
        onView(allOf(withId(R.id.recycler), isDisplayed())).check(matches(isDisplayed()))
    }

    @Test
    fun rotation_doesNotCrash() {
        // The activity does not persist the selected section, so after a
        // recreate the launch screen (Storage home) is shown again; re-open
        // the section to make the assertions independent of restore behaviour.
        clickDrawerRow(R.id.drowImages)
        onView(withId(R.id.chipRecycler)).check(matches(isDisplayed()))
        activityRule.scenario.recreate()
        clickDrawerRow(R.id.drowImages)
        onView(withId(R.id.chipRecycler)).check(matches(isDisplayed()))

        clickDrawerRow(R.id.drowHome)
        onView(allOf(withId(R.id.recycler), isDisplayed())).check(matches(isDisplayed()))
        activityRule.scenario.recreate()
        onView(allOf(withId(R.id.recycler), isDisplayed())).check(matches(isDisplayed()))
    }

    private fun storagePermitted(): Boolean {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        return StorageUtils.isStoragePermitted(ctx)
    }
}
