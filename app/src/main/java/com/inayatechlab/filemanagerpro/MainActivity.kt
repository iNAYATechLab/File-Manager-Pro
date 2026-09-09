package com.inayatechlab.filemanagerpro

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.inayatechlab.filemanagerpro.browse.BrowseFragment
import com.inayatechlab.filemanagerpro.browse.CrumbAdapter
import com.inayatechlab.filemanagerpro.categories.CategoriesFragment
import com.inayatechlab.filemanagerpro.databinding.ActivityMainBinding
import com.inayatechlab.filemanagerpro.library.LibraryFragment
import com.inayatechlab.filemanagerpro.library.LibrarySection
import com.inayatechlab.filemanagerpro.model.PathCrumb
import com.inayatechlab.filemanagerpro.settings.SettingsActivity
import com.inayatechlab.filemanagerpro.util.MediaCat
import com.inayatechlab.filemanagerpro.util.StorageUtils
import com.inayatechlab.filemanagerpro.vault.VaultFragment
import java.io.File
import android.os.StatFs
import android.text.format.Formatter

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_PATH = "extra_open_path"
        private const val TAG_BROWSE = "browse"
        private const val TAG_CATEGORIES = "categories"
        private const val TAG_VAULT = "vault"
        private const val TAG_LIBRARY = "library"
        private const val PREFS_UI = "ui"
        private const val KEY_DARK = "dark"
    }

    private lateinit var binding: ActivityMainBinding
    private var browseFragment: BrowseFragment? = null
    private var categoriesFragment: CategoriesFragment? = null
    private var vaultFragment: VaultFragment? = null
    private var libraryFragment: LibraryFragment? = null
    /** Drawer rows with the icon tint used when the row is not selected. */
    private lateinit var drawerRows: List<Pair<View, Int>>
    private var activeDrawerRow: View? = null

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val vault = vaultFragment
            if (vault != null && !vault.isHidden && vault.isVisible) {
                if (!vault.handleBack()) openHome()
                return
            }
            val cats = categoriesFragment
            if (cats != null && !cats.isHidden && cats.isVisible) {
                openHome()
                return
            }
            val library = libraryFragment
            if (library != null && !library.isHidden && library.isVisible) {
                openHome()
                return
            }
            val browse = browseFragment
            if (browse != null && !browse.isHidden && browse.handleBack()) return
            // nothing consumed — fall back to default behaviour (finish activity)
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, backCallback)

        binding.toolbar.title = ""
        binding.btnUp.setOnClickListener { }

        // Fragments
        val fm = supportFragmentManager
        val browse = fm.findFragmentByTag(TAG_BROWSE) as? BrowseFragment
            ?: BrowseFragment.newInstance().also { created ->
                fm.beginTransaction().add(R.id.fragmentContainer, created, TAG_BROWSE).commitNow()
            }
        browseFragment = browse

        val cats = fm.findFragmentByTag(TAG_CATEGORIES) as? CategoriesFragment
            ?: CategoriesFragment.newInstance().also { created ->
                fm.beginTransaction().add(R.id.fragmentContainer, created, TAG_CATEGORIES).hide(created).commitNow()
            }
        categoriesFragment = cats

        val vault = fm.findFragmentByTag(TAG_VAULT) as? VaultFragment
            ?: VaultFragment.newInstance().also { created ->
                fm.beginTransaction().add(R.id.fragmentContainer, created, TAG_VAULT).hide(created).commitNow()
            }
        vaultFragment = vault

        val library = fm.findFragmentByTag(TAG_LIBRARY) as? LibraryFragment
            ?: LibraryFragment.newInstance().also { created ->
                fm.beginTransaction().add(R.id.fragmentContainer, created, TAG_LIBRARY).hide(created).commitNow()
            }
        libraryFragment = library

        fm.beginTransaction().hide(cats).hide(vault).hide(library).show(browse).commitNow()

        // ------------------------------------------------------------- drawer
        drawerRows = listOf(
            binding.drowHome to R.color.ui_ink2,
            binding.drowInternal to R.color.ui_ink2,
            binding.drowSd to R.color.ui_ink2,
            binding.drowImages to R.color.file_image,
            binding.drowVideos to R.color.file_video,
            binding.drowAudio to R.color.file_audio,
            binding.drowDocs to R.color.file_doc,
            binding.drowArchives to R.color.file_archive,
            binding.drowApks to R.color.file_apk,
            binding.drowFavorites to R.color.brand_accent,
            binding.drowRecents to R.color.ui_ink2,
            binding.drowDownloads to R.color.file_sheet,
            binding.drowTrash to R.color.file_pdf,
            binding.drowVault to R.color.ui_ink2,
            binding.drowSettings to R.color.ui_ink2
        )
        activeDrawerRow = binding.drowHome

        binding.toolbar.setNavigationOnClickListener { openDrawer() }

        binding.drowHome.setOnClickListener { openHome() }
        binding.drowInternal.setOnClickListener { openPrimaryVolume() }
        binding.drowSd.setOnClickListener { openRemovableVolume() }
        binding.drowImages.setOnClickListener { openCategory(MediaCat.IMAGES, binding.drowImages) }
        binding.drowVideos.setOnClickListener { openCategory(MediaCat.VIDEOS, binding.drowVideos) }
        binding.drowAudio.setOnClickListener { openCategory(MediaCat.AUDIO, binding.drowAudio) }
        binding.drowDocs.setOnClickListener { openCategory(MediaCat.DOCUMENTS, binding.drowDocs) }
        binding.drowArchives.setOnClickListener { openCategory(MediaCat.ARCHIVES, binding.drowArchives) }
        binding.drowApks.setOnClickListener { openCategory(MediaCat.APPS, binding.drowApks) }
        binding.drowFavorites.setOnClickListener { openLibrary(LibrarySection.FAVORITES, binding.drowFavorites) }
        binding.drowRecents.setOnClickListener { openLibrary(LibrarySection.RECENT, binding.drowRecents) }
        binding.drowDownloads.setOnClickListener { openLibrary(LibrarySection.DOWNLOADS, binding.drowDownloads) }
        binding.drowTrash.setOnClickListener { openLibrary(LibrarySection.TRASH, binding.drowTrash) }
        binding.drowVault.setOnClickListener { openVault() }
        binding.drowSettings.setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        highlightRow(binding.drowHome)

        // storage meter + dark switch
        fillStorageMeter()
        binding.swDark.isChecked = isDarkMode()
        binding.swDark.setOnCheckedChangeListener { _, checked ->
            applyDarkMode(checked)
        }
        binding.drowDark.setOnClickListener {
            binding.swDark.isChecked = !binding.swDark.isChecked
        }
        binding.tvDrawerVersion.text = getString(R.string.settings_version_value, versionName())

        // Open a folder passed by another screen (e.g. search results)
        handleOpenPath(intent.getStringExtra(EXTRA_OPEN_PATH))
    }

    // ------------------------------------------------------------ navigation

    private fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun closeDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun showFragment(fragment: Fragment) {
        val fm = supportFragmentManager
        val others = listOf(browseFragment, categoriesFragment, vaultFragment, libraryFragment).filterNotNull()
        val tx = fm.beginTransaction()
        others.filter { it !== fragment }.forEach { tx.hide(it) }
        tx.show(fragment).commitNow()
        when (fragment) {
            is BrowseFragment -> fragment.becomeVisible()
            is CategoriesFragment -> fragment.becomeVisible()
            is VaultFragment -> fragment.becomeVisible()
            is LibraryFragment -> fragment.becomeVisible()
        }
        closeDrawer()
    }

    fun openHome() {
        showFragment(browseFragment ?: return)
        browseFragment?.showStorageHome()
        highlightRow(binding.drowHome)
    }

    private fun openVolume(label: String, file: File, row: View) {
        showFragment(browseFragment ?: return)
        browseFragment?.openStorageVolume(label, file)
        highlightRow(row)
    }

    private fun openPrimaryVolume() {
        openVolume(
            getString(R.string.drawer_internal),
            StorageUtils.primaryRoot(),
            binding.drowInternal
        )
    }

    private fun openRemovableVolume() {
        val roots = runCatching { StorageUtils.roots() }.getOrDefault(emptyList())
        val primary = runCatching { StorageUtils.primaryRoot().canonicalPath }.getOrNull()
        val removable = roots.firstOrNull {
            primary == null || runCatching { it.file.canonicalPath != primary }.getOrDefault(true)
        }
        if (removable == null) {
            openHome()
            return
        }
        openVolume(removable.label, removable.file, binding.drowSd)
    }

    private fun openCategory(cat: MediaCat, row: View) {
        showFragment(categoriesFragment ?: return)
        categoriesFragment?.selectCategory(cat)
        highlightRow(row)
    }

    private fun openLibrary(section: LibrarySection, row: View) {
        showFragment(libraryFragment ?: return)
        libraryFragment?.jumpTo(section)
        highlightRow(row)
    }

    private fun openVault() {
        showFragment(vaultFragment ?: return)
        highlightRow(binding.drowVault)
    }

    /** Switches to the Storage home and opens [path] (used by Library/Recents). */
    fun openFolderInBrowse(path: String) {
        openHome()
        browseFragment?.navigateToPath(path)
    }

    /** Switches to a Library section (used by the storage-home shortcuts). */
    fun openLibrarySection(section: LibrarySection) {
        val row = when (section) {
            LibrarySection.FAVORITES -> binding.drowFavorites
            LibrarySection.RECENT -> binding.drowRecents
            LibrarySection.DOWNLOADS -> binding.drowDownloads
            LibrarySection.TRASH -> binding.drowTrash
        }
        openLibrary(section, row)
    }

    // ----------------------------------------------------------- drawer state

    private fun highlightRow(selected: View?) {
        activeDrawerRow = selected
        val accent = ContextCompat.getColor(this, R.color.ui_accent)
        val ink2 = ContextCompat.getColor(this, R.color.ui_ink2)
        for ((row, iconTintRes) in drawerRows) {
            val on = row === selected
            row.background = ContextCompat.getDrawable(
                this,
                if (on) R.drawable.bg_drawer_row_on else R.drawable.bg_drawer_row
            )
            (row.getChildAt(0) as? ImageView)?.imageTintList = ColorStateList.valueOf(
                if (on) accent else ContextCompat.getColor(this, iconTintRes)
            )
            val label = row.getChildAt(1) as? TextView
            label?.setTextColor(if (on) accent else ink2)
            label?.typeface = if (on) android.graphics.Typeface.DEFAULT_BOLD
            else android.graphics.Typeface.DEFAULT
        }
    }

    private fun fillStorageMeter() {
        val primary = StorageUtils.primaryRoot()
        val stat = try {
            StatFs(primary.absolutePath)
        } catch (e: Exception) {
            return
        }
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val used = total - free
        val pct = if (total > 0) (used * 100 / total).toInt() else 0
        binding.tvDrawerUsed.text = getString(R.string.drawer_used, Formatter.formatShortFileSize(this, used))
        binding.tvDrawerFree.text = getString(R.string.drawer_free, Formatter.formatShortFileSize(this, free))
        binding.tvDrawerPct.text = getString(R.string.drawer_pct_fmt, pct)
        binding.pbDrawerStorage.progress = pct
    }

    private fun isDarkMode(): Boolean {
        val mode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyDarkMode(dark: Boolean) {
        getSharedPreferences(PREFS_UI, MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, dark).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun versionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull() ?: ""

    // ------------------------------------------------------------- toolbar helpers

    fun setAppTitle(title: String) {
        binding.toolbar.title = title
    }

    fun clearMenu() {
        binding.toolbar.menu.clear()
        binding.toolbar.setOnMenuItemClickListener(null)
    }

    fun installBrowseMenu(handler: (MenuItem) -> Boolean): Menu {
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.menu_browse)
        binding.toolbar.setOnMenuItemClickListener { item -> handler(item) }
        return binding.toolbar.menu
    }

    /** Installs an arbitrary menu resource (used by the Vault tab). */
    fun installVaultMenu(handler: (MenuItem) -> Boolean): Menu {
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.menu_vault)
        binding.toolbar.setOnMenuItemClickListener { item -> handler(item) }
        return binding.toolbar.menu
    }

    fun currentMenu(): Menu = binding.toolbar.menu

    fun setCrumbs(crumbs: List<PathCrumb>, onCrumb: (PathCrumb) -> Unit) {
        binding.crumbRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.crumbRecycler.adapter = CrumbAdapter(crumbs, onCrumb)
        binding.crumbRecycler.post {
            binding.crumbRecycler.scrollToPosition(crumbs.lastIndex.coerceAtLeast(0))
        }
    }

    fun setUpButton(visible: Boolean, onUp: () -> Unit) {
        binding.btnUp.isVisible = visible
        binding.btnUp.setOnClickListener { onUp() }
    }

    fun showCrumbBarVisible(visible: Boolean) {
        binding.crumbBar.isVisible = visible
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenPath(intent.getStringExtra(EXTRA_OPEN_PATH))
    }

    private fun handleOpenPath(path: String?) {
        if (path.isNullOrBlank()) return
        openFolderInBrowse(path)
    }

}
