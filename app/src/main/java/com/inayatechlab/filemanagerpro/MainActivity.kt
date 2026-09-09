package com.inayatechlab.filemanagerpro

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.inayatechlab.filemanagerpro.browse.BrowseFragment
import com.inayatechlab.filemanagerpro.browse.CrumbAdapter
import com.inayatechlab.filemanagerpro.categories.CategoriesFragment
import com.inayatechlab.filemanagerpro.databinding.ActivityMainBinding
import com.inayatechlab.filemanagerpro.model.PathCrumb
import com.inayatechlab.filemanagerpro.vault.VaultFragment

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_PATH = "extra_open_path"
        private const val TAG_BROWSE = "browse"
        private const val TAG_CATEGORIES = "categories"
        private const val TAG_VAULT = "vault"
    }

    private lateinit var binding: ActivityMainBinding
    private var browseFragment: BrowseFragment? = null
    private var categoriesFragment: CategoriesFragment? = null
    private var vaultFragment: VaultFragment? = null
    private var menuHandler: ((MenuItem) -> Boolean)? = null

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val vault = vaultFragment
            if (vault != null && !vault.isHidden && vault.isVisible) {
                if (!vault.handleBack()) selectTab(R.id.nav_storage)
                return
            }
            val cats = categoriesFragment
            if (cats != null && !cats.isHidden && cats.isVisible) {
                selectTab(R.id.nav_storage)
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

        fm.beginTransaction().hide(cats).hide(vault).show(browse).commitNow()

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_storage -> {
                    showFragment(browse)
                    true
                }
                R.id.nav_categories -> {
                    showFragment(cats)
                    true
                }
                R.id.nav_vault -> {
                    showFragment(vault)
                    true
                }
                else -> false
            }
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

    private fun handleOpenPath(path: String?) {
        if (path.isNullOrBlank()) return
        selectTab(R.id.nav_storage)
        browseFragment?.navigateToPath(path)
    }

    private fun showFragment(fragment: Fragment) {
        val fm = supportFragmentManager
        val others = listOf(browseFragment, categoriesFragment, vaultFragment).filterNotNull()
        val tx = fm.beginTransaction()
        others.filter { it !== fragment }.forEach { tx.hide(it) }
        tx.show(fragment).commitNow()
        when (fragment) {
            is BrowseFragment -> fragment.becomeVisible()
            is CategoriesFragment -> fragment.becomeVisible()
            is VaultFragment -> fragment.becomeVisible()
        }
    }

    private fun selectTab(id: Int) {
        val wasSelected = binding.bottomNav.selectedItemId == id
        binding.bottomNav.selectedItemId = id
        // selecting the already-selected tab does not fire the listener — handle it manually
        if (wasSelected) {
            when (id) {
                R.id.nav_storage -> showFragment(browseFragment ?: return)
                R.id.nav_categories -> showFragment(categoriesFragment ?: return)
                R.id.nav_vault -> showFragment(vaultFragment ?: return)
            }
        }
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

    /** Installs an arbitrary menu resource (used by the Vault tab). */
    fun installVaultMenu(handler: (MenuItem) -> Boolean): Menu {
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.menu_vault)
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
