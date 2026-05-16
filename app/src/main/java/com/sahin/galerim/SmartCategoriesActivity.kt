package com.sahin.galerim

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmartCategoriesActivity : AppCompatActivity() {

    private lateinit var recyclerCategories: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var btnBack: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var rootLayout: View
    private lateinit var topBar: LinearLayout
    
    private var isAmoledTheme = false
    private val categoriesList = mutableListOf<SmartCategory>()

    data class SmartCategory(val title: String, val items: List<MediaItem>, val coverUri: android.net.Uri)

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
        setContentView(R.layout.activity_smart_categories)
        supportActionBar?.hide()

        rootLayout = findViewById(R.id.rootLayout)
        topBar = findViewById(R.id.topBar)
        recyclerCategories = findViewById(R.id.recyclerCategories)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        btnBack = findViewById(R.id.btnBack)
        tvTitle = findViewById(R.id.tvTitle)

        applyDynamicColors()

        btnBack.setOnClickListener { finish() }

        recyclerCategories.layoutManager = GridLayoutManager(this, 2)
        recyclerCategories.adapter = CategoriesAdapter()

        generateCategories()
    }

    private fun applyDynamicColors() {
        val bgColor = ContextCompat.getColor(this, R.color.p_app_background)
        val actualBg = if (isAmoledTheme) Color.BLACK else bgColor
        rootLayout.setBackgroundColor(actualBg)
        topBar.setBackgroundColor(actualBg)
        
        val r = Color.red(actualBg)
        val g = Color.green(actualBg)
        val b = Color.blue(actualBg)
        val isDarkBg = ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) < 0.6
        val adaptiveTextColor = if (isDarkBg) Color.WHITE else Color.BLACK
        
        tvTitle.setTextColor(adaptiveTextColor)
        btnBack.setColorFilter(adaptiveTextColor)
        
        (emptyStateLayout.getChildAt(0) as ImageView).setColorFilter(adaptiveTextColor)
        (emptyStateLayout.getChildAt(1) as TextView).setTextColor(adaptiveTextColor)
    }

    private fun generateCategories() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allMedia = MainActivity.mediaList.toList()
            val tempCategories = mutableListOf<SmartCategory>()

            val camera = allMedia.filter { it.path.contains("DCIM/Camera", true) }
            if (camera.isNotEmpty()) {
                tempCategories.add(SmartCategory("Kamera", camera.sortedByDescending { it.dateAdded }, camera.first().uri))
            }

            val screenshots = allMedia.filter { it.path.contains("Screenshots", true) || it.path.contains("Ekran Görüntüleri", true) }
            if (screenshots.isNotEmpty()) {
                tempCategories.add(SmartCategory("Ekran Görüntüleri", screenshots.sortedByDescending { it.dateAdded }, screenshots.first().uri))
            }

            val downloads = allMedia.filter { it.path.contains("Download", true) }
            if (downloads.isNotEmpty()) {
                tempCategories.add(SmartCategory("İndirilenler", downloads.sortedByDescending { it.dateAdded }, downloads.first().uri))
            }

            val whatsapp = allMedia.filter { it.path.contains("WhatsApp", true) }
            if (whatsapp.isNotEmpty()) {
                tempCategories.add(SmartCategory("WhatsApp", whatsapp.sortedByDescending { it.dateAdded }, whatsapp.first().uri))
            }

            val videos = allMedia.filter { it.isVideo }
            if (videos.isNotEmpty()) {
                tempCategories.add(SmartCategory("Videolar", videos.sortedByDescending { it.dateAdded }, videos.first().uri))
            }

            val animated = allMedia.filter { it.path.endsWith(".gif", true) || it.path.endsWith(".webp", true) }
            if (animated.isNotEmpty()) {
                tempCategories.add(SmartCategory("Hareketli Görseller", animated.sortedByDescending { it.dateAdded }, animated.first().uri))
            }

            val largeFiles = allMedia.filter { it.size > 50 * 1024 * 1024 }
            if (largeFiles.isNotEmpty()) {
                tempCategories.add(SmartCategory("Büyük Boyutlu", largeFiles.sortedByDescending { it.size }, largeFiles.first().uri))
            }

            withContext(Dispatchers.Main) {
                categoriesList.clear()
                categoriesList.addAll(tempCategories)
                
                if (categoriesList.isEmpty()) {
                    emptyStateLayout.visibility = View.VISIBLE
                    recyclerCategories.visibility = View.GONE
                } else {
                    emptyStateLayout.visibility = View.GONE
                    recyclerCategories.visibility = View.VISIBLE
                    recyclerCategories.adapter?.notifyDataSetChanged()
                }
            }
        }
    }

    inner class CategoriesAdapter : RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivCategoryCover: ImageView = view.findViewById(R.id.ivCategoryCover)
            val tvCategoryTitle: TextView = view.findViewById(R.id.tvCategoryTitle)
            val tvCategoryCount: TextView = view.findViewById(R.id.tvCategoryCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_smart_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categoriesList[position]
            
            Glide.with(this@SmartCategoriesActivity)
                .load(category.coverUri)
                .centerCrop()
                .into(holder.ivCategoryCover)
                
            holder.tvCategoryTitle.text = category.title
            holder.tvCategoryCount.text = "${category.items.size} Öğe"
            
            holder.itemView.setOnClickListener {
                MainActivity.displayedMediaList.clear()
                MainActivity.displayedMediaList.addAll(category.items)
                
                val intent = Intent(this@SmartCategoriesActivity, FullScreenActivity::class.java)
                intent.putExtra("position", 0)
                intent.putExtra("isSlideshow", false)
                startActivity(intent)
            }
        }

        override fun getItemCount(): Int = categoriesList.size
    }
}
