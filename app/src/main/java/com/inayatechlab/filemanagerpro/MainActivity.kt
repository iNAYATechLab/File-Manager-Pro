package com.inayatechlab.filemanagerpro

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.inayatechlab.filemanagerpro.browse.BrowseFragment
import com.inayatechlab.filemanagerpro.browse.CrumbAdapter
import com.inayatechlab.filemanagerpro.categories.CategoriesFragment
import com.inayatechlab.filemanagerpro.databinding.ActivityMainBinding
import com.inayatechlab.filemanagerpro.favorites.FavoritesFragment
import com.inayatechlab.filemanagerpro.model.PathCrumb
import com.inayatechlab.filemanagerpro.recent.RecentFragment
import com.inayatechlab.filemanagerpro.util.FavoritesStore
import com.inayatechlab.filemanagerpro.util.RecentStore

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_PATH = "extra_open_path"
        private const val TAG_BROWSE = "browse"
        private const val TAG_CATEGORIES = "categories"
        private const val TAG_FAVORITES = "favorites"
        private const val TAG_RECENT = "recent"
    }

    private lateinit var binding: ActivityMainBinding
    private var browseFragment: BrowseFragment? = null
    private var categoriesFragment: CategoriesFragment? = null
    private var favoritesFragment: FavoritesFragment? = null
    private var recentFragment: RecentFragment? = null
    private var menuHandler: ((MenuItem) -> Boolean)? = null

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val browse = browseFragment
            if (browse != null && !browse.isHidden && browse.isVisible) {
                if (browse.handleBack()) return
            } else {
                // Any other tab returns to the Storage home (browse).
                if (selectTab(R.id.nav_storage)) return
            }
            // nothing consumed — fall back to default behaviour (finish activity)
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, backCallback)

        binding.toolbar.title = ""
        binding.btnUp.setOnClickListener { }

        // Persisted stores must be ready before any screen queries them.
        FavoritesStore.reload(applicationContext)
        RecentStore.reload(applicationContext)
        // Bounded freshness scan of mounted volumes (opened/modified/created recents).
        RecentStore.scanAndTrack(applicationContext)

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

        val favs = fm.findFragmentByTag(TAG_FAVORITES) as? FavoritesFragment
            ?: FavoritesFragment.newInstance().also { created ->
                fm.beginTransaction().add(R.id.fragmentContainer, created, TAG_FAVORITES).hide(created).commitNow()
            }
        favoritesFragment = favs

        val rec = fm.findFragmentByTag(TAG_RECENT) as? RecentFragment
            ?: RecentFragment.newInstance().also { created ->
                fm.beginTransaction().add(R.id.fragmentContainer, created, TAG_RECENT).hide(created).commitNow()
            }
        recentFragment = rec
        fm.beginTransaction().hide(cats).hide(favs).hide(rec).show(browse).commitNow()

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_storage -> showFragment(browse)
                R.id.nav_categories -> showFragment(cats)
                R.id.nav_favorites -> showFragment(favs)
                R.id.nav_recent -> showFragment(rec)
                else -> return@setOnItemSelectedListener false
            }
            true
        }
        // Select the initial tab only after the first layout pass. Firing the
        // listener during onCreate reaches fragments whose view/_binding is not
        // created yet (e.g. after process death / rotation), crashing in
        // reload() with a NullPointerException.
        binding.bottomNav.post { binding.bottomNav.selectedItemId = R.id.nav_storage }

        // Open a folder passed by another screen (e.g. search results)
        handleOpenPath(intent.getStringExtra(EXTRA_OPEN_PATH))
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenPath(intent.getStringExtra(EXTRA_OPEN_PATH))
    }

    /** Browse into [path]; falls back to the current tab if none found. */
    fun openBrowsePath(path: String) {
        if (selectTab(R.id.nav_storage)) {
            browseFragment?.navigateToPath(path)
        }
    }

    private fun handleOpenPath(path: String?) {
        if (path.isNullOrBlank()) return
        openBrowsePath(path)
    }

    private fun showFragment(fragment: Fragment) {
        val fm = supportFragmentManager
        fm.beginTransaction()
            .hide(categoriesFragment ?: return)
            .hide(favoritesFragment ?: return)
            .hide(recentFragment ?: return)
            .hide(browseFragment ?: return)
            .show(fragment)
            .commitNow()
        when (fragment) {
            is BrowseFragment -> fragment.becomeVisible()
            is CategoriesFragment -> fragment.becomeVisible()
            is FavoritesFragment -> fragment.becomeVisible()
            is RecentFragment -> fragment.becomeVisible()
        }
    }

    private fun selectTab(id: Int): Boolean {
        val wasSelected = binding.bottomNav.selectedItemId == id
        binding.bottomNav.selectedItemId = id
        // selecting the already-selected tab does not fire the listener — handle it manually
        if (wasSelected) {
            when (id) {
                R.id.nav_storage -> showFragment(browseFragment ?: return false)
                R.id.nav_categories -> showFragment(categoriesFragment ?: return false)
                R.id.nav_favorites -> showFragment(favoritesFragment ?: return false)
                R.id.nav_recent -> showFragment(recentFragment ?: return false)
            }
        }
        return true
    }

    // ------------------------------------------------------------- toolbar helpers

    fun setAppTitle(title: String) {
        binding.toolbar.title = title
    }

    fun clearMenu() {
        binding.toolbar.menu.clear()
        binding.toolbar.setOnMenuItemClickListener(null)
        menuHandler = null
    }

    fun installBrowseMenu(handler: (MenuItem) -> Boolean): Menu {
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.menu_browse)
        menuHandler = handler
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
}
