package com.inayatechlab.filemanagerpro

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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
 * Seed fixtures are pushed to /sdcard/Download by the "App screenshots"
 * CI job before this test runs (see .github/workflows/ci.yml). PNGs are
 * written to the app's external files dir, internal cache, and published
 * through MediaStore to Pictures/FMP-Screenshots so the workflow can
 * `adb pull` them (FUSE hides Android/data from the shell on API 29).
 *
 * Interactions deliberately avoid Espresso: under a slow/loaded emulator
 * the app window can lack input focus for long stretches, which makes
 * Espresso throw RootViewWithoutFocusException. Direct performClick()
 * inside ActivityScenario.onActivity is immune to that.
 */
@RunWith(JUnit4::class)
class ScreenshotCaptureTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val pkg = ctx.packageName
    private val outDir: File = File(ctx.getExternalFilesDir(null), "screenshots")
    private val cacheDir: File = File(ctx.cacheDir, "screenshots")

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
        cacheDir.mkdirs()
        FileOutputStream(File(cacheDir, "$name.png")).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        // Publish to a shell-readable public folder via MediaStore (API 29).
        runCatching {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FMP-Screenshots")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        }.onFailure { android.util.Log.e("Screenshots", "MediaStore publish failed: $name", it) }
        bmp.recycle()
        android.util.Log.i("Screenshots", "saved $name.png")
    }

    private fun sleepMs(ms: Long) = Thread.sleep(ms)

    /** Each step is best-effort so one flaky step cannot kill the whole capture. */
    private fun step(name: String, block: () -> Unit) {
        try {
            block()
            android.util.Log.i("Screenshots", "step OK: $name")
        } catch (t: Throwable) {
            android.util.Log.e("Screenshots", "step FAILED: $name", t)
        }
    }

    // ---- view helpers -------------------------------------------------------

    private fun allViews(root: View, out: MutableList<View>) {
        out.add(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) allViews(root.getChildAt(i), out)
        }
    }

    private fun View.byId(id: Int): View? {
        val all = mutableListOf<View>()
        allViews(this, all)
        return all.firstOrNull { it.id == id }
    }

    private fun View.byText(text: String): View? {
        val all = mutableListOf<View>()
        allViews(this, all)
        return all.firstOrNull { it is TextView && it.text?.toString() == text }
    }

    private fun click(activity: Activity, target: View?) {
        requireNotNull(target) { "target view not found in ${activity.javaClass.simpleName}" }
        target.performClick()
    }

    @Test
    fun captureAllPages() {
        // Only runs when the screenshots workflow passes the flag; the normal
        // CI emulator suite must stay fast and seed-free.
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue(
            "capture disabled outside the app-screenshots CI job",
            args.getString("screenshots") == "true"
        )
        grant()
        android.util.Log.i(
            "Screenshots",
            "pkg=$pkg outDir=${outDir.absolutePath} cacheDir=${cacheDir.absolutePath}"
        )
        val downloadPath = "/storage/emulated/0/Download"

        // ---- 1. Storage home ------------------------------------------------
        step("storage home shot") {
            ActivityScenario.launch(MainActivity::class.java).use {
                sleepMs(4500)
                shot("01-storage-home")
            }
        }

        // ---- 2. Downloads folder --------------------------------------------
        step("downloads folder shot") {
            ActivityScenario.launch<MainActivity>(
                Intent(ctx, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_PATH, downloadPath)
            ).use {
                sleepMs(3500)
                shot("02-folder-downloads")
            }
        }

        // ---- 3. Text viewer on guide.md -------------------------------------
        step("text viewer shot") {
            ActivityScenario.launch<MainActivity>(
                Intent(ctx, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_PATH, downloadPath)
            ).use { sc ->
                sleepMs(4000)
                sc.onActivity { act -> click(act, act.window.decorView.byText("guide.md")) }
                sleepMs(2500)
                shot("03-text-viewer")
            }
        }

        // ---- 4. Categories tab ----------------------------------------------
        step("categories shot") {
            ActivityScenario.launch(MainActivity::class.java).use { sc ->
                sleepMs(3500)
                sc.onActivity { act ->
                    click(act, act.window.decorView.byId(R.id.nav_categories))
                }
                sleepMs(2500)
                shot("04-categories")
            }
        }

        // ---- 5. Library tab + Downloads chip --------------------------------
        step("library downloads shot") {
            ActivityScenario.launch(MainActivity::class.java).use { sc ->
                sleepMs(3500)
                sc.onActivity { act ->
                    click(act, act.window.decorView.byId(R.id.nav_library))
                }
                sleepMs(2200)
                sc.onActivity { act ->
                    click(act, act.window.decorView.byId(R.id.chipDownloads))
                }
                sleepMs(2500)
                shot("05-library-downloads")
            }
        }

        // ---- 6. Search results ----------------------------------------------
        step("search shot") {
            ActivityScenario.launch<SearchActivity>(
                Intent(ctx, SearchActivity::class.java)
                    .putExtra(SearchActivity.EXTRA_ROOT, downloadPath)
            ).use { sc ->
                sleepMs(2500)
                sc.onActivity { act ->
                    val query = act.window.decorView.byId(R.id.etQuery)
                    requireNotNull(query) { "etQuery not found" }
                    if (query is android.widget.EditText) query.setText("guide")
                }
                sleepMs(2500)
                shot("06-search")
            }
        }

        // ---- 7. About / company page ----------------------------------------
        step("about shot") {
            ActivityScenario.launch(AboutActivity::class.java).use {
                sleepMs(2500)
                shot("07-about")
            }
        }

        assertTrue(
            "no screenshots captured",
            outDir.listFiles()?.isNotEmpty() == true
        )
    }
}
