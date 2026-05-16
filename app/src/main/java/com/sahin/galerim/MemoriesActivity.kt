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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MemoriesActivity : AppCompatActivity() {

    private lateinit var recyclerMemories: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var btnBack: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var rootLayout: View
    private lateinit var topBar: LinearLayout
    
    private var isAmoledTheme = false
    private val memoriesList = mutableListOf<MemoryItem>()

    data class MemoryItem(val title: String, val subtitle: String, val coverUri: android.net.Uri, val items: List<MediaItem>)

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
        setContentView(R.layout.activity_memories)
        supportActionBar?.hide()

        rootLayout = findViewById(R.id.rootLayout)
        topBar = findViewById(R.id.topBar)
        recyclerMemories = findViewById(R.id.recyclerMemories)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        btnBack = findViewById(R.id.btnBack)
        tvTitle = findViewById(R.id.tvTitle)

        applyDynamicColors()

        btnBack.setOnClickListener { finish() }

        recyclerMemories.layoutManager = LinearLayoutManager(this)
        recyclerMemories.adapter = MemoriesAdapter()

        generateMemories()
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

    private fun generateMemories() {
        lifecycleScope.launch(Dispatchers.IO) {
            val currentCal = Calendar.getInstance()
            val currentDay = currentCal.get(Calendar.DAY_OF_YEAR)
            val currentYear = currentCal.get(Calendar.YEAR)
            
            val memoryGroups = mutableListOf<MemoryItem>()
            val allMedia = MainActivity.mediaList.toList()
            
            val yearsAgoMap = mutableMapOf<Int, MutableList<MediaItem>>()
            
            for (item in allMedia) {
                val itemCal = Calendar.getInstance()
                itemCal.timeInMillis = item.dateAdded * 1000L
                
                val itemDay = itemCal.get(Calendar.DAY_OF_YEAR)
                val itemYear = itemCal.get(Calendar.YEAR)
                
                if (itemDay == currentDay && itemYear < currentYear) {
                    val diff = currentYear - itemYear
                    if (!yearsAgoMap.containsKey(diff)) {
                        yearsAgoMap[diff] = mutableListOf()
                    }
                    yearsAgoMap[diff]?.add(item)
                }
            }
            
            for ((years, items) in yearsAgoMap) {
                if (items.isNotEmpty()) {
                    val title = "$years Yıl Önce Bugün"
                    val subtitle = "${items.size} Öğelik Anı"
                    memoryGroups.add(MemoryItem(title, subtitle, items.random().uri, items))
                }
            }
            
            withContext(Dispatchers.Main) {
                memoriesList.clear()
                memoriesList.addAll(memoryGroups.sortedBy { it.title })
                
                if (memoriesList.isEmpty()) {
                    emptyStateLayout.visibility = View.VISIBLE
                    recyclerMemories.visibility = View.GONE
                } else {
                    emptyStateLayout.visibility = View.GONE
                    recyclerMemories.visibility = View.VISIBLE
                    recyclerMemories.adapter?.notifyDataSetChanged()
                }
            }
        }
    }

    inner class MemoriesAdapter : RecyclerView.Adapter<MemoriesAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivMemoryCover: ImageView = view.findViewById(R.id.ivMemoryCover)
            val tvMemoryTitle: TextView = view.findViewById(R.id.tvMemoryTitle)
            val tvMemorySubtitle: TextView = view.findViewById(R.id.tvMemorySubtitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_memory_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val memory = memoriesList[position]
            
            Glide.with(this@MemoriesActivity)
                .load(memory.coverUri)
                .centerCrop()
                .into(holder.ivMemoryCover)
                
            holder.tvMemoryTitle.text = memory.title
            holder.tvMemorySubtitle.text = memory.subtitle
            
            holder.itemView.setOnClickListener {
                MainActivity.displayedMediaList.clear()
                MainActivity.displayedMediaList.addAll(memory.items)
                
                val intent = Intent(this@MemoriesActivity, FullScreenActivity::class.java)
                intent.putExtra("position", 0)
                intent.putExtra("isSlideshow", true)
                startActivity(intent)
            }
        }

        override fun getItemCount(): Int = memoriesList.size
    }
}
