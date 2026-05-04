package com.sahin.galerim

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatButton
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import com.sahin.galerim.utils.BiometricHelper
import com.sahin.galerim.data.AppDatabase
import com.sahin.galerim.data.HiddenMedia

class MainActivity : AppCompatActivity() {

    private var placeholderDrawable: android.graphics.drawable.Drawable? = null

    fun getPlaceholder(): android.graphics.drawable.Drawable {
        if (placeholderDrawable != null) return placeholderDrawable!!
        
        val bgColor = android.graphics.Color.parseColor("#F2F2F2") 
        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(bgColor)
        }
        
        val icon = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_menu_gallery)?.mutate()
        if (icon != null) {
            icon.setTint(android.graphics.Color.parseColor("#BDBDBD")) 
            val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(bgDrawable, icon))
            
            val density = resources.displayMetrics.density
            val inset = (32 * density).toInt() 
            layerDrawable.setLayerInset(1, inset, inset, inset, inset)
            placeholderDrawable = layerDrawable
        } else {
            placeholderDrawable = bgDrawable
        }
        return placeholderDrawable!!
    }

    companion object {
        val displayedMediaList = mutableListOf<MediaItem>()
        val galleryItems = mutableListOf<GalleryItem>() 
        val favoritePaths = mutableSetOf<String>()
        val mediaList = mutableListOf<MediaItem>()
        val trashList = mutableListOf<MediaItem>()

        val trashedPaths = mutableSetOf<String>()
        val trashedOriginalPaths = mutableMapOf<String, String>()
        val trashedTimestamps = mutableMapOf<String, Long>()
        val trashedIsVideo = mutableMapOf<String, Boolean>()
        val trashedDurations = mutableMapOf<String, Long>()
        val trashedSizes = mutableMapOf<String, Long>()

        val itemLocationCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        val geocodeCache = java.util.concurrent.ConcurrentHashMap<String, String>()

        var forceReload = false

        fun saveTrashedPaths(context: Context) {
            val dataToSave = trashedPaths.map { path ->
                val time = trashedTimestamps[path] ?: System.currentTimeMillis()
                val orig = trashedOriginalPaths[path] ?: ""
                val isVid = trashedIsVideo[path] ?: false
                val dur = trashedDurations[path] ?: 0L
                val size = trashedSizes[path] ?: 0L
                "$path|$time|$orig|$isVid|$dur|$size"
            }.toSet()

            context.getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("trashedItemsData", dataToSave)
                .apply()
        }

        fun loadTrashedPaths(context: Context) {
            val prefs = context.getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
            val savedSet = prefs.getStringSet("trashedItemsData", emptySet()) ?: emptySet()
            
            trashedPaths.clear()
            trashedOriginalPaths.clear()
            trashedTimestamps.clear()
            trashedIsVideo.clear()
            trashedDurations.clear()
            trashedSizes.clear()
            
            for (item in savedSet) {
                val parts = item.split("|")
                if (parts.size >= 6) {
                    val path = parts[0]
                    val time = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                    val orig = parts[2]
                    val isVid = parts[3].toBoolean()
                    val dur = parts[4].toLongOrNull() ?: 0L
                    val size = parts[5].toLongOrNull() ?: 0L
                    
                    trashedPaths.add(path)
                    trashedTimestamps[path] = time
                    trashedOriginalPaths[path] = orig
                    trashedIsVideo[path] = isVid
                    trashedDurations[path] = dur
                    trashedSizes[path] = size
                }
            }
        }

        fun saveFavoritePaths(context: Context) {
            context.getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("favoriteItems", favoritePaths)
                .apply()
        }
    }

    lateinit var bottomTabLayout: TabLayout
    lateinit var allRecycler: RecyclerView
    lateinit var albumsRecycler: RecyclerView
    val albumList = mutableListOf<Album>()
    var filterBucketId: Long? = null
    var filterLocation: String? = null
    var locationGroups: Map<String, List<MediaItem>> = emptyMap()
    
    var isSelectionMode = false
    val selectedMedia = mutableSetOf<MediaItem>()
    lateinit var selectionTopBar: View
    lateinit var selectionBottomBar: View
    lateinit var tvSelectionCount: TextView
    
    lateinit var trashTopBar: View
    lateinit var emptyTrashView: View
    
    lateinit var favoritesTopBar: View
    lateinit var emptyFavoritesView: View
    lateinit var emptySearchView: View
    
    var isShowingTrash = false
    var isShowingFavorites = false
    var isShowingPlaces = false
    var isShowingLocations = false
    
    var isSearchMode = false
    var currentSearchQuery = ""
    
    var previousTabPosition = 0
    val autoPlayHandler = Handler(Looper.getMainLooper())
    var currentlyPlayingPosition = -1
    var mediaPlayer: MediaPlayer? = null
    var isActivityResumed = false
    
    var moreMenuPopup: PopupWindow? = null
    var bottomSheetMenu: BottomSheetDialog? = null
    var sortBottomSheetMenu: BottomSheetDialog? = null
    var appearanceBottomSheetMenu: BottomSheetDialog? = null
    var locationTask: Job? = null

    var mediaObserver: ContentObserver? = null
    var needsRefresh = false
    var isAmoledTheme = false
    var isFastScrolling = false

    val tabScrollStates = mutableMapOf<Int, android.os.Parcelable?>()

    lateinit var coordinatorLayout: View
    lateinit var mainTitle: TextView
    lateinit var fastScrollContainer: View
    lateinit var fastScrollBubbleContainer: CardView
    lateinit var fastScrollBubble: TextView
    lateinit var fastScrollThumb: View
    
    lateinit var searchContainer: View
    lateinit var etSearch: EditText
    lateinit var btnClearSearch: ImageView
    lateinit var btnSearchBack: ImageView
    lateinit var topIconsContainer: View
    
    var hideScrollerRunnable: Runnable? = null
    val scrollerHandler = Handler(Looper.getMainLooper())

    fun getAccentColor(): Int {
        val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        return Color.parseColor(prefs.getString("accentColor", "#5C94FF"))
    }

    val autoPlayRunnable = object : Runnable {
        override fun run() {
            if (!isActivityResumed || isFinishing || isDestroyed) return
            
            try {
                if (!isSelectionMode && allRecycler.visibility == View.VISIBLE && galleryItems.isNotEmpty() && allRecycler.scrollState == RecyclerView.SCROLL_STATE_IDLE && !allRecycler.isComputingLayout) {
                    val layoutManager = allRecycler.layoutManager as? GridLayoutManager
                    val first = layoutManager?.findFirstVisibleItemPosition() ?: -1
                    val last = layoutManager?.findLastVisibleItemPosition() ?: -1

                    if (first != -1 && last != -1) {
                        val visibleItems = mutableListOf<Int>()
                        
                        for (i in first..last) {
                            if (i in galleryItems.indices) {
                                val item = galleryItems[i]
                                if (item is MediaContentItem && (item.media.isVideo || isAnimated(item.media.path))) {
                                    visibleItems.add(i)
                                }
                            }
                        }

                        if (visibleItems.isNotEmpty()) {
                            var nextPos = visibleItems[0] 
                            val currentIndex = visibleItems.indexOf(currentlyPlayingPosition)
                            
                            if (currentIndex != -1 && currentIndex + 1 < visibleItems.size) {
                                nextPos = visibleItems[currentIndex + 1]
                            }

                            if (nextPos != currentlyPlayingPosition || currentlyPlayingPosition == -1) {
                                val oldPos = currentlyPlayingPosition
                                currentlyPlayingPosition = nextPos
                                releaseMediaPlayer()
                                if (oldPos != -1) allRecycler.adapter?.notifyItemChanged(oldPos)
                                allRecycler.adapter?.notifyItemChanged(currentlyPlayingPosition)
                            }
                        } else {
                            if (currentlyPlayingPosition != -1) {
                                val oldPos = currentlyPlayingPosition
                                currentlyPlayingPosition = -1
                                releaseMediaPlayer()
                                allRecycler.adapter?.notifyItemChanged(oldPos)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
            } 
            
            autoPlayHandler.removeCallbacksAndMessages(null)
            autoPlayHandler.postDelayed(this, 3000)
        }
    }

    fun getMenuBgColor(): Int {
        val themePrefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val currentTheme = themePrefs.getString("appTheme", "Sistem Teması")
        val isAmoled = currentTheme == "Koyu Amoled Tema"
        return if (isAmoled) Color.parseColor("#121212") else ContextCompat.getColor(this, R.color.p_app_dialog_bg)
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
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        coordinatorLayout = findViewById(R.id.coordinatorLayout)
        bottomTabLayout = findViewById(R.id.bottomTabLayout)
        allRecycler = findViewById(R.id.allRecycler)
        albumsRecycler = findViewById(R.id.albumsRecycler)
        selectionTopBar = findViewById(R.id.selectionTopBar)
        selectionBottomBar = findViewById(R.id.selectionBottomBar)
        tvSelectionCount = findViewById(R.id.tvSelectionCount)
        
        trashTopBar = findViewById(R.id.trashTopBar)
        emptyTrashView = findViewById(R.id.emptyTrashView)
        
        favoritesTopBar = findViewById(R.id.favoritesTopBar)
        emptyFavoritesView = findViewById(R.id.emptyFavoritesView)
        emptySearchView = findViewById(R.id.emptySearchView)

        mainTitle = findViewById(R.id.mainTitle)
        fastScrollContainer = findViewById(R.id.fastScrollContainer)
        fastScrollBubbleContainer = findViewById(R.id.fastScrollBubbleContainer)
        fastScrollBubble = findViewById(R.id.fastScrollBubble)
        fastScrollThumb = findViewById(R.id.fastScrollThumb)
        
        searchContainer = findViewById(R.id.searchContainer)
        etSearch = findViewById(R.id.etSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        btnSearchBack = findViewById(R.id.btnSearchBack)
        topIconsContainer = findViewById(R.id.topIconsContainer)

        applyDynamicColorsToUI()
        
        loadTrashedPaths(this)
        favoritePaths.addAll(prefs.getStringSet("favoriteItems", emptySet()) ?: emptySet())
        
        findViewById<View>(R.id.btnBackFromTrash)?.setOnClickListener {
            resetStates()
            bottomTabLayout.getTabAt(0)?.select()
        }

        findViewById<View>(R.id.btnBackFromFavorites)?.setOnClickListener {
            resetStates()
            bottomTabLayout.getTabAt(0)?.select()
        }

        findViewById<View>(R.id.btnTrashEdit)?.setOnClickListener {
            if (!isSelectionMode && trashList.isNotEmpty()) {
                isSelectionMode = true
                currentlyPlayingPosition = -1
                releaseMediaPlayer()
                updateSelectionUI()
                allRecycler.adapter?.notifyDataSetChanged()
            }
        }

        findViewById<View>(R.id.btnTrashMore)?.setOnClickListener { showTrashMoreMenu(it) }
        findViewById<View>(R.id.btnMainMore)?.setOnClickListener { showMainMoreMenu(it) }
        
        setupElegantBottomTabs()
        setupSelectionButtons()
        setupFastScroller()
        setupSearchFunctionality()
        
        val itemAnimator = allRecycler.itemAnimator
        if (itemAnimator is androidx.recyclerview.widget.SimpleItemAnimator) {
            itemAnimator.supportsChangeAnimations = false
        }

        val spanCount = prefs.getInt("gridSpanCount", 4)
        val layoutManager = GridLayoutManager(this, spanCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (galleryItems.getOrNull(position) is HeaderItem) spanCount else 1
            }
        }
        
        allRecycler.layoutManager = layoutManager
        allRecycler.adapter = AllMediaAdapter(this)
        
        albumsRecycler.layoutManager = GridLayoutManager(this, spanCount)
        albumsRecycler.adapter = AlbumsAdapter(this)

        mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                if (isActivityResumed) {
                    loadAllMedia() 
                } else {
                    needsRefresh = true 
                }
            }
        }
        
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver!!)
        contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver!!)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSearchMode) {
                    closeSearchMode()
                } else if (isSelectionMode) {
                    exitSelectionMode()
                } else if (filterBucketId != null || filterLocation != null) { 
                    filterBucketId = null
                    filterLocation = null
                    isShowingLocations = false
                    isShowingPlaces = false
                    allRecycler.visibility = View.GONE
                    albumsRecycler.visibility = View.VISIBLE
                    albumsRecycler.adapter?.notifyDataSetChanged()
                } else if (isShowingTrash || isShowingFavorites || isShowingPlaces || isShowingLocations) { 
                    resetStates()
                    bottomTabLayout.getTabAt(0)?.select() 
                } else if (bottomTabLayout.selectedTabPosition != 0) {
                    bottomTabLayout.getTabAt(0)?.select()
                } else { 
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true 
                }
            }
        })
        
        checkAndRequestPermission()
    }
    
    fun closeSearchMode() {
        isSearchMode = false
        currentSearchQuery = ""
        etSearch.text.clear()
        
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        
        searchContainer.visibility = View.GONE
        topIconsContainer.visibility = View.VISIBLE
        
        if (bottomTabLayout.selectedTabPosition == 2) {
            allRecycler.visibility = View.GONE
            albumsRecycler.visibility = View.VISIBLE
        }
        
        loadDisplayedList()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaObserver?.let { 
            contentResolver.unregisterContentObserver(it) 
        }
    }

    override fun onResume() { 
        super.onResume() 
        isActivityResumed = true 
        
        val themePrefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val currentTheme = themePrefs.getString("appTheme", "Sistem Teması")
        val newAmoled = currentTheme == "Koyu Amoled Tema"
        
        if (isAmoledTheme != newAmoled) {
            isAmoledTheme = newAmoled
            applyDynamicColorsToUI()
        }
        
        if (mediaList.isEmpty() || needsRefresh || forceReload) { 
            loadAllMedia()
            needsRefresh = false 
            forceReload = false
        } else {
            if (isShowingFavorites) {
                loadDisplayedList()
            } else {
                allRecycler.adapter?.notifyDataSetChanged()
            }
            autoPlayHandler.removeCallbacksAndMessages(null)
            autoPlayHandler.postDelayed(autoPlayRunnable, 1000)
        }
    }
    
    override fun onPause() { 
        super.onPause()
        isActivityResumed = false
        autoPlayHandler.removeCallbacksAndMessages(null)
        locationTask?.cancel()
        
        try { 
            moreMenuPopup?.dismiss()
            moreMenuPopup = null
            bottomSheetMenu?.dismiss()
            bottomSheetMenu = null
            sortBottomSheetMenu?.dismiss()
            sortBottomSheetMenu = null
            appearanceBottomSheetMenu?.dismiss()
            appearanceBottomSheetMenu = null 
        } catch (e: Exception) {
        }

        val oldPos = currentlyPlayingPosition
        currentlyPlayingPosition = -1
        releaseMediaPlayer()
        
        if (oldPos != -1) {
            allRecycler.adapter?.notifyItemChanged(oldPos)
        }
    }

    fun releaseMediaPlayer() { 
        val player = mediaPlayer
        mediaPlayer = null
        
        try { 
            player?.apply {
                setOnPreparedListener(null)
                setOnCompletionListener(null)
                setOnErrorListener(null)
                setOnInfoListener(null)
                try { 
                    if (isPlaying) stop() 
                } catch (e: Exception) {}
                try { 
                    reset() 
                } catch (e: Exception) {}
                try { 
                    release() 
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
        } 
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.all { it }) {
            loadAllMedia()
        }
    }

    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                loadAllMedia()
            } else {
                try { 
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) 
                } catch (e: Exception) { 
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) 
                }
            }
        } else {
            val perms = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
                loadAllMedia()
            } else {
                requestPermissionLauncher.launch(perms)
            }
        }
    }
}
