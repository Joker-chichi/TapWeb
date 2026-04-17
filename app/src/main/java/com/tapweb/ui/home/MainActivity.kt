package com.tapweb.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.tapweb.R
import com.tapweb.data.db.AppDatabase
import com.tapweb.data.db.WebSiteEntity
import com.tapweb.data.repository.WebSiteRepository
import com.tapweb.databinding.ActivityMainBinding
import com.tapweb.ui.addsite.AddSiteActivity
import com.tapweb.ui.settings.SettingsActivity
import com.tapweb.ui.webview.WebViewActivity
import com.tapweb.util.BrowserLauncher
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: WebSiteRepository
    private lateinit var adapter: WebSiteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val dao = AppDatabase.getInstance(this).webSiteDao()
        repository = WebSiteRepository(dao)

        setupRecyclerView()
        setupFab()
        setupToolbar()
        observeWebsites()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private fun setupRecyclerView() {
        adapter = WebSiteAdapter(
            onClick = { website -> openWebsite(website) },
            onLongClick = { website, view -> showContextMenu(website, view) },
            onViewUrl = { website -> BrowserLauncher.open(this, website.url) }
        )
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = GridLayoutManager(this, 1)
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddSiteActivity::class.java))
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun observeWebsites() {
        lifecycleScope.launch {
            repository.getAll().collectLatest { websites ->
                adapter.submitList(websites)
                binding.emptyState.visibility =
                    if (websites.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility =
                    if (websites.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun openWebsite(website: WebSiteEntity) {
        when (website.openMode) {
            com.tapweb.data.model.OpenMode.WEBVIEW -> {
                startActivity(
                    Intent(this, WebViewActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse("tapweb://site/${website.id}")
                        putExtra(WebViewActivity.EXTRA_WEBSITE_ID, website.id)
                        putExtra(WebViewActivity.EXTRA_URL, website.url)
                        putExtra(WebViewActivity.EXTRA_TITLE, website.title)
                    }
                )
            }
            com.tapweb.data.model.OpenMode.BROWSER -> {
                BrowserLauncher.open(this, website.url)
            }
        }
    }

    private fun showContextMenu(website: WebSiteEntity, anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.menu_context_card, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit -> {
                        startActivity(
                            Intent(this@MainActivity, AddSiteActivity::class.java).apply {
                                putExtra(AddSiteActivity.EXTRA_WEBSITE_ID, website.id)
                            }
                        )
                        true
                    }
                    R.id.action_delete -> {
                        lifecycleScope.launch { repository.delete(website) }
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }
}
