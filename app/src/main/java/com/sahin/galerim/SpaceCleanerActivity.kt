package com.sahin.galerim

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SpaceCleanerActivity : AppCompatActivity() {

    private lateinit var recyclerDuplicates: RecyclerView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var tvSelectedSize: TextView
    private lateinit var btnDeleteSelected: AppCompatButton
    private lateinit var btnBack: ImageView
    
    private val duplicateList = mutableListOf<MediaItem>()
    private val selectedForDeletion = mutableSetOf<MediaItem>()
    private lateinit var adapter: DuplicatesAdapter
    
    private var isAmoledTheme = false

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            removeDeletedItemsFromLists()
            Toast.makeText(this, "Seçilen öğeler silindi.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val currentTheme = prefs.getString("appTheme", "Sistem Teması")
        isAmoledTheme = currentTheme == "Koyu Amoled Tema"
        when (currentTheme) {
            "Açık Tema" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Koyu Tema", "Koyu Amoled Tema" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_space_cleaner)
        supportActionBar?.hide()

        recyclerDuplicates = findViewById(R.id.recyclerDuplicates)
        loadingLayout = findViewById(R.id.loadingLayout)
        bottomBar = findViewById(R.id.bottomBar)
        tvSelectedSize = findViewById(R.id.tvSelectedSize)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)
        btnBack = findViewById(R.id.btnBack)

        applyDynamicColors()

        btnBack.setOnClickListener { finish() }
        
        adapter = DuplicatesAdapter()
        recyclerDuplicates.layoutManager = GridLayoutManager(this, 3)
        recyclerDuplicates.adapter = adapter

        btnDeleteSelected.setOnClickListener {
            if (selectedForDeletion.isNotEmpty()) {
                deleteSelectedMedia()
            }
        }

        scanForDuplicates()
    }

    private fun applyDynamicColors() {
        val bgColor = ContextCompat.getColor(this, R.color.p_app_background)
        val actualBg = if (isAmoledTheme) Color.BLACK else bgColor
        findViewById<View>(R.id.rootLayout).setBackgroundColor(actualBg)
        
        val topBar = findViewById<LinearLayout>(R.id.topBar)
        topBar.setBackgroundColor(actualBg)
        bottomBar.setBackgroundColor(actualBg)
        
        val isDarkBg = isColorDark(actualBg)
        val adaptiveTextColor = if (isDarkBg) Color.WHITE else Color.BLACK
        
        findViewById<TextView>(R.id.tvTitle).setTextColor(adaptiveTextColor)
        btnBack.setColorFilter(adaptiveTextColor)
        tvSelectedSize.setTextColor(adaptiveTextColor)
        
        val pb = loadingLayout.getChildAt(0) as ProgressBar
        pb.indeterminateTintList = android.content.res.ColorStateList.valueOf(adaptiveTextColor)
        
        (loadingLayout.getChildAt(1) as TextView).setTextColor(adaptiveTextColor)
    }

    private fun isColorDark(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) < 0.6
    }

    private fun scanForDuplicates() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allMedia = MainActivity.mediaList.toList()
            val groupedBySize = allMedia.groupBy { it.size }.filter { it.key > 0 && it.value.size > 1 }
            
            val duplicates = mutableListOf<MediaItem>()
            for ((_, items) in groupedBySize) {
                val sortedItems = items.sortedBy { it.dateAdded }
                duplicates.addAll(sortedItems)
            }
            
            withContext(Dispatchers.Main) {
                duplicateList.clear()
                duplicateList.addAll(duplicates)
                loadingLayout.visibility = View.GONE
                
                if (duplicateList.isNotEmpty()) {
                    recyclerDuplicates.visibility = View.VISIBLE
                    adapter.notifyDataSetChanged()
                    
                    duplicateList.forEach { item ->
                        val groupItems = duplicateList.filter { it.size == item.size }
                        if (groupItems.indexOf(item) > 0) {
                            selectedForDeletion.add(item)
                        }
                    }
                    updateBottomBar()
                } else {
                    val tv = loadingLayout.getChildAt(1) as TextView
                    tv.text = "Kopya dosya bulunamadı."
                    loadingLayout.getChildAt(0).visibility = View.GONE
                    loadingLayout.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateBottomBar() {
        if (selectedForDeletion.isNotEmpty()) {
            bottomBar.visibility = View.VISIBLE
            val totalBytes = selectedForDeletion.sumOf { it.size }
            val mb = totalBytes / (1024.0 * 1024.0)
            tvSelectedSize.text = String.format(java.util.Locale.US, "%.1f MB seçili", mb)
        } else {
            bottomBar.visibility = View.GONE
        }
        adapter.notifyDataSetChanged()
    }

    private fun deleteSelectedMedia() {
        val uris = selectedForDeletion.map { it.uri }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
            deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        } else {
            var successCount = 0
            selectedForDeletion.forEach { item ->
                try {
                    val file = File(item.path)
                    if (file.exists() && file.delete()) {
                        contentResolver.delete(item.uri, null, null)
                        successCount++
                    } else {
                        contentResolver.delete(item.uri, null, null)
                        successCount++
                    }
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val recoverableSecurityException = e as? android.app.RecoverableSecurityException
                        if (recoverableSecurityException != null) {
                            val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                            return
                        }
                    }
                } catch (e: Exception) {}
            }
            if (successCount > 0) {
                removeDeletedItemsFromLists()
                Toast.makeText(this, "$successCount öğe silindi.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeDeletedItemsFromLists() {
        duplicateList.removeAll(selectedForDeletion)
        MainActivity.mediaList.removeAll(selectedForDeletion)
        MainActivity.displayedMediaList.removeAll(selectedForDeletion)
        MainActivity.forceReload = true
        selectedForDeletion.clear()
        
        if (duplicateList.isEmpty()) {
            recyclerDuplicates.visibility = View.GONE
            bottomBar.visibility = View.GONE
            val tv = loadingLayout.getChildAt(1) as TextView
            tv.text = "Tüm kopyalar temizlendi."
            loadingLayout.getChildAt(0).visibility = View.GONE
            loadingLayout.visibility = View.VISIBLE
        } else {
            updateBottomBar()
        }
    }

    inner class DuplicatesAdapter : RecyclerView.Adapter<DuplicatesAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
            val vOverlay: View = view.findViewById(R.id.vOverlay)
            val ivCheck: ImageView = view.findViewById(R.id.ivCheck)
            val tvSize: TextView = view.findViewById(R.id.tvSize)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_duplicate_media, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = duplicateList[position]
            
            Glide.with(this@SpaceCleanerActivity)
                .load(item.uri)
                .centerCrop()
                .into(holder.ivThumbnail)

            val mb = item.size / (1024.0 * 1024.0)
            holder.tvSize.text = String.format(java.util.Locale.US, "%.1f MB", mb)

            val isSelected = selectedForDeletion.contains(item)
            if (isSelected) {
                holder.vOverlay.visibility = View.VISIBLE
                val accentColor = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
                    .getString("accentColor", "#5C94FF")?.let { Color.parseColor(it) } ?: Color.BLUE
                holder.ivCheck.setImageDrawable(CheckCircleDrawable(accentColor))
                holder.ivCheck.imageTintList = null
            } else {
                holder.vOverlay.visibility = View.GONE
                holder.ivCheck.setImageResource(R.drawable.ic_check_circle_off)
                holder.ivCheck.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            }

            holder.itemView.setOnClickListener {
                if (isSelected) {
                    selectedForDeletion.remove(item)
                } else {
                    selectedForDeletion.add(item)
                }
                updateBottomBar()
            }
        }

        override fun getItemCount(): Int = duplicateList.size
    }
}
