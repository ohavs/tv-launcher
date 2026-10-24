package com.ohav.tvhome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ohav.tvhome.settings.SettingsActivity
import com.ohav.tvhome.ui.HomeScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var home: HomeController

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lifecycleScope.launch { home.reload() }
        }
    }

    private val listingsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        home.shelf.invalidate()
        home.resumeSignal++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        home = HomeController(this)
        onBackPressedDispatcher.addCallback(this) { home.homeSignal++ }
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Top Shelf needs to read other apps' channels.
        if (checkSelfPermission(TV_LISTINGS) != PackageManager.PERMISSION_GRANTED) listingsPermission.launch(TV_LISTINGS)
        home.repo.packagesChanged(home.prefs)
        lifecycleScope.launch { home.reload() }
        setContent {
            HomeScreen(home, openSettings = { startActivity(Intent(this, SettingsActivity::class.java)) })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) home.homeSignal++
    }

    override fun onResume() {
        super.onResume()
        // Catches installs/removals missed while another app was in front.
        if (home.repo.packagesChanged(home.prefs)) lifecycleScope.launch { home.reload() }
        home.onResume()
    }

    companion object {
        private const val TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }
}
