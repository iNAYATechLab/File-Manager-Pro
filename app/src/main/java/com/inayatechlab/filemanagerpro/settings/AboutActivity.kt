package com.inayatechlab.filemanagerpro.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.inayatechlab.filemanagerpro.R
import com.inayatechlab.filemanagerpro.databinding.ActivityAboutBinding

/**
 * About / company page: app identity, version, iNayaTech Lab info, source
 * repository and license.
 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(R.string.about_title)

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        binding.tvVersion.text = getString(R.string.settings_version_value, version)
        binding.tvMadeBy.text = getString(R.string.about_made_by_fmt, getString(R.string.about_company_name))
        binding.tvCopyright.text =
            getString(R.string.about_copyright_fmt, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))

        binding.btnGitHub.setOnClickListener {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iNAYATechLab/File-Manager-Pro"))
                )
            }
        }
    }
}
