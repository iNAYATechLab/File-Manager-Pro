package com.inayatechlab.filemanagerpro.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.ActivitySettingsBinding
import com.inayatechlab.filemanagerpro.util.SettingsStore

/** App settings: file-browser behaviour + about. Values persist via [SettingsStore]. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(R.string.action_settings)

        // File browser
        binding.swHidden.isChecked = SettingsStore.showHiddenFiles(this)
        binding.swHidden.setOnCheckedChangeListener { _, checked ->
            SettingsStore.setShowHiddenFiles(this, checked)
        }

        binding.swGrid.isChecked = SettingsStore.gridView(this)
        binding.swGrid.setOnCheckedChangeListener { _, checked ->
            SettingsStore.setGridView(this, checked)
        }

        binding.swConfirmDelete.isChecked = SettingsStore.confirmDelete(this)
        binding.swConfirmDelete.setOnCheckedChangeListener { _, checked ->
            SettingsStore.setConfirmDelete(this, checked)
        }

        // About
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        binding.tvVersion.text = getString(R.string.settings_version_value, version)
    }
}
