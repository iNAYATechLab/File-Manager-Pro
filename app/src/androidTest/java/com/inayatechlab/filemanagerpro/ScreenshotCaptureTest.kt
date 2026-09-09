package com.inayatechlab.filemanagerpro

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.inayatechlab.filemanagerpro.search.SearchActivity
import com.inayatechlab.filemanagerpro.settings.AboutActivity
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Captures real emulator screenshots (not mockups) of the main app pages.
 *
 * Run from CI on an API 29 emulator with demo files pre-pushed to
 * /sdcard/Download (see .github/workflows/screenshots.yml). PNGs are written
 * to the app's external files dir so the workflow can `adb pull` them.
 */
@RunWith(JUnit4::class)
class ScreenshotCaptureTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val pkg = ctx.packageName
    private val outDir: File = File(ctx.getExternalFilesDir(null), "screenshots")

    private fun grant() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        runCatching {
            ui.grantRuntimePermission(pkg, Manifest.permission.READ_EXTERNAL_STORAGE)
            ui.grantRuntimePermission(pkg, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    @Suppress("DEPRECATION")
    private fun shot(name: String) {
        outDir.mkdirs()
        val bmp: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        FileOutputStream(File(outDir, "$name.png")).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()
        android.util.Log.i("Screenshots", "saved $name.png -> ${outDir.absolutePath}")
    }

    private fun wait(ms: Long) = Thread.sleep(ms)

    /** Each step is best-effort so one flaky step cannot kill the whole capture. */
    private fun step(name: String, block: () -> Unit) {
        try {
            block()
            android.util.Log.i("Screenshots", "step OK: $name")
        } catch (t: Throwable) {
            android.util.Log.e("Screenshots", "step FAILED: $name", t)
        }
    }

    @Test
    fun captureAllPages() {
        // Only runs when the screenshots workflow passes the flag; the normal
        // CI emulator suite must stay fast and seed-free.
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue(
            "capture disabled outside .github/workflows/screenshots.yml",
            args.getString("screenshots") == "true"
        )
        grant()

        // ---- 1. Storage home (quick access chips + volume cards) ------------
        ActivityScenario.launch(MainActivity::class.java).use {
            wait(4000)
            shot("01-storage-home")
        }

        // ---- 2/3/5. Browse Downloads folder, text viewer, library -----------
        val downloadPath = "/storage/emulated/0/Download"
        ActivityScenario.launch<MainActivity>(
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PATH, downloadPath)
        ).use {
            wait(3500)
            shot("02-folder-downloads")

            // tap the guide.md row -> in-app text viewer
            step("text viewer shot") {
                onView(ViewMatchers.withText("guide.md")).perform(ViewActions.click())
                wait(2500)
                shot("03-text-viewer")
                Espresso.pressBack()
                wait(1200)
            }

            step("library downloads shot") {
                onView(ViewMatchers.withId(R.id.nav_library)).perform(ViewActions.click())
                wait(1800)
                onView(ViewMatchers.withId(R.id.chipDownloads)).perform(ViewActions.click())
                wait(2200)
                shot("05-library-downloads")
                onView(ViewMatchers.withId(R.id.nav_storage)).perform(ViewActions.click())
            }
        }

        // ---- 4. Categories tab ----------------------------------------------
        step("categories shot") {
            ActivityScenario.launch(MainActivity::class.java).use {
                wait(3000)
                onView(ViewMatchers.withId(R.id.nav_categories)).perform(ViewActions.click())
                wait(2500)
                shot("04-categories")
            }
        }

        // ---- 6. Search results ----------------------------------------------
        step("search shot") {
            ActivityScenario.launch<SearchActivity>(
                Intent(ctx, SearchActivity::class.java)
                    .putExtra(SearchActivity.EXTRA_ROOT, downloadPath)
            ).use {
                wait(1500)
                onView(ViewMatchers.withId(R.id.etQuery)).perform(ViewActions.replaceText("guide"))
                Espresso.closeSoftKeyboard()
                wait(2500)
                shot("06-search")
            }
        }

        // ---- 7. About / company page ----------------------------------------
        step("about shot") {
            ActivityScenario.launch(AboutActivity::class.java).use {
                wait(2500)
                shot("07-about")
            }
        }

        assertTrue(
            "no screenshots captured",
            outDir.listFiles()?.isNotEmpty() == true
        )
    }
}
