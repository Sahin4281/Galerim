package com.sahin.galerim

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sahin.galerim.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class FullScreenActivity : AppCompatActivity() {
    var placeholderDrawable: android.graphics.drawable.Drawable? = null

    lateinit var viewPager: ViewPager2
    lateinit var filmstripRecycler: RecyclerView
    lateinit var btnFavorite: ImageButton
    lateinit var btnEdit: ImageButton
    lateinit var btnShare: ImageButton
    lateinit var btnTrash: ImageButton
    lateinit var btnMore: ImageButton
    var currentPosition: Int = 0
    
    var detailsDialog: BottomSheetDialog? = null
    var trashDialog: BottomSheetDialog? = null
    var btnGoToWeb: TextView? = null

    var isGlobalMuted = false 
    val timeHandler = Handler(Looper.getMainLooper())
    var activeViewHolder: FsFullScreenAdapter.ViewHolder? = null
    var isUserSeeking = false 
    
    var isUiHidden = false
    var isAmoledTheme = false

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                showFsCustomToast("Hedef klasör seçildi. Dosya işlemi başlatılıyor...", android.R.drawable.ic_menu_info_details)
            }
        }
    }

    val updateTimeRunnable = object : Runnable {
        override fun run() {
            activeViewHolder?.let { holder ->
                try {
                    if (holder.videoView.isPlaying && !isUserSeeking) {
                        val currentMs = holder.videoView.currentPosition
                        holder.tvCurrentTime.text = formatFsTime(currentMs)
                        holder.videoSeekBar.progress = currentMs
                        timeHandler.postDelayed(this, 250) 
                    }
                } catch (e: Exception) {}
            }
        }
    }

    fun getPlaceholder(): android.graphics.drawable.Drawable {
        if (placeholderDrawable != null) return placeholderDrawable!!
        
        val bgColor = android.graphics.Color.parseColor("#F2F2F2") 
        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(bgColor)
        }
        
        val icon = androidx.core.content.ContextCompat.getDrawable(this@FullScreenActivity, android.R.drawable.ic_menu_gallery)?.mutate()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        val themePrefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val currentTheme = themePrefs.getString("appTheme", "Sistem Teması")
        isAmoledTheme = currentTheme == "Koyu Amoled Tema"
        when (currentTheme) {
            "Açık Tema" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Koyu Tema", "Koyu Amoled Tema" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        super.onCreate(savedInstanceState)
        
        if (MainActivity.displayedMediaList.isEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        window.apply {
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
                isStatusBarContrastEnforced = false
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_fullscreen)
        supportActionBar?.hide()

        viewPager = findViewById(R.id.viewPager)
        
        viewPager.layoutParams = android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT, 
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
        )
        
        viewPager.offscreenPageLimit = 1
        (viewPager.getChildAt(0) as? RecyclerView)?.itemAnimator = null

        filmstripRecycler = findViewById(R.id.filmstripRecycler)
        btnFavorite = findViewById(R.id.btnFavorite)
        btnEdit = findViewById(R.id.btnEdit)
        btnShare = findViewById(R.id.btnShare)
        btnTrash = findViewById(R.id.btnTrash)
        btnMore = findViewById(R.id.btnMore)

        val bottomUI = findViewById<View>(R.id.bottomUIContainer)
        if (bottomUI != null) {
            val initialPaddingBottom = bottomUI.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(bottomUI) { v, insets ->
                val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, initialPaddingBottom + sysBars.bottom)
                insets
            }
        }

        btnGoToWeb = TextView(this).apply {
            text = "Web sitesine git"
            setTextColor(Color.WHITE)
            textSize = 14f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#99000000"))
                cornerRadius = 100f
            }
            val dpPaddingH = (24 * resources.displayMetrics.density).toInt()
            val dpPaddingV = (10 * resources.displayMetrics.density).toInt()
            setPadding(dpPaddingH, dpPaddingV, dpPaddingH, dpPaddingV)
            visibility = View.GONE
            elevation = 10f
        }
        
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        val bottomMarg = (130 * resources.displayMetrics.density).toInt()
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = bottomMarg
        }
        rootView.addView(btnGoToWeb, lp)

        viewPager.reduceFsDragSensitivity()
        currentPosition = intent.getIntExtra("position", 0)
        if (currentPosition >= MainActivity.displayedMediaList.size) currentPosition = 0
        
        viewPager.adapter = FsFullScreenAdapter(this, MainActivity.displayedMediaList)
        viewPager.setCurrentItem(currentPosition, false)
        
        val layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        filmstripRecycler.layoutManager = layoutManager
        filmstripRecycler.adapter = FsFilmstripAdapter(this, MainActivity.displayedMediaList)
        filmstripRecycler.scrollToPosition(currentPosition)

        updateFavoriteIcon()
        checkAndShowWebButton(currentPosition)
        applyDynamicColorsToUI()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    getViewHolder(currentPosition)?.let { holder ->
                        try {
                            if (holder.videoView.isPlaying) {
                                holder.videoView.pause()
                                holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
                            }
                            holder.videoThumbnail.visibility = View.VISIBLE
                            timeHandler.removeCallbacks(updateTimeRunnable)
                            holder.mediaPlayerRef?.setVolume(0f, 0f)
                        } catch (e: Exception) {}
                    }
                } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    val autoPlay = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE).getBoolean("autoPlayVideos", false)
                    getViewHolder(currentPosition)?.let { holder ->
                        val item = MainActivity.displayedMediaList[currentPosition]
                        if (item.isVideo) {
                            holder.mediaPlayerRef?.setVolume(if (isGlobalMuted) 0f else 1f, if (isGlobalMuted) 0f else 1f)
                            if (autoPlay) {
                                if (!holder.videoView.isPlaying) {
                                    holder.videoView.start()
                                    holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_pause)
                                    startProgressUpdater(holder)
                                }
                                holder.videoView.postDelayed({
                                    holder.videoThumbnail.visibility = View.GONE
                                }, 250)
                            } else {
                                holder.videoThumbnail.visibility = View.VISIBLE
                                holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
                            }
                        }
                    }
                }
            }

            override fun onPageSelected(position: Int) {
                val oldPosition = currentPosition
                currentPosition = position
                updateFavoriteIcon()
                checkAndShowWebButton(position)
                
                timeHandler.removeCallbacks(updateTimeRunnable)

                if (oldPosition != currentPosition) {
                    getViewHolder(oldPosition)?.let { oldHolder ->
                        try {
                            oldHolder.videoThumbnail.visibility = View.VISIBLE
                            oldHolder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
                            oldHolder.mediaPlayerRef?.setVolume(0f, 0f)
                            if (oldHolder.videoView.isPlaying) oldHolder.videoView.pause()
                        } catch (e: Exception) {}
                    }
                }

                getViewHolder(currentPosition)?.let { newHolder ->
                    newHolder.controlsLayout.visibility = if (isUiHidden) View.GONE else View.VISIBLE
                    newHolder.controlsLayout.alpha = if (isUiHidden) 0f else 1f
                    
                    if (MainActivity.displayedMediaList[position].isVideo) {
                        val uriString = MainActivity.displayedMediaList[position].uri.toString()
                        if (newHolder.videoView.tag != uriString) {
                            newHolder.videoView.tag = uriString
                            newHolder.videoView.setVideoURI(MainActivity.displayedMediaList[position].uri)
                        }
                    }
                }

                filmstripRecycler.adapter?.let {
                    it.notifyItemChanged(oldPosition)
                    it.notifyItemChanged(currentPosition)
                    filmstripRecycler.smoothScrollToPosition(currentPosition)
                }
            }
        })

        viewPager.post {
            getViewHolder(currentPosition)?.let { holder ->
                val item = MainActivity.displayedMediaList[currentPosition]
                if (item.isVideo) {
                    val uriString = item.uri.toString()
                    holder.videoView.tag = uriString
                    holder.videoView.setVideoURI(item.uri)
                }
            }
        }

        btnFavorite.setOnClickListener { toggleFavorite() }
        btnEdit.setOnClickListener { handleEdit() }
        btnShare.setOnClickListener { handleShare() }
        btnTrash.setOnClickListener { showTrashDialog() }
        btnMore.setOnClickListener { showMoreMenu(it) }
    }

    private fun applyDynamicColorsToUI() {
        val bgColor = ContextCompat.getColor(this, R.color.p_app_background)
        val iconTint = ContextCompat.getColor(this, R.color.p_app_icon_tint)
        val actualBg = if (isAmoledTheme) Color.BLACK else bgColor
        
        findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)?.setBackgroundColor(actualBg)
        
        val bottomUI = findViewById<View>(R.id.bottomUIContainer)
        val semiTransparentBg = androidx.core.graphics.ColorUtils.setAlphaComponent(actualBg, 230)
        bottomUI?.setBackgroundColor(semiTransparentBg)
        
        btnEdit.setColorFilter(iconTint)
        btnShare.setColorFilter(iconTint)
        btnTrash.setColorFilter(iconTint)
        btnMore.setColorFilter(iconTint)
        
        updateFavoriteIcon()
    }

    fun checkAndShowWebButton(position: Int) {
        if (position < 0 || position >= MainActivity.displayedMediaList.size) return
        val item = MainActivity.displayedMediaList[currentPosition]
        
        if (item.isVideo) {
            btnGoToWeb?.visibility = View.GONE
            btnGoToWeb?.tag = null
            return
        }
        
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@FullScreenActivity)
            val capturedUrl = db.urlDao().getUrlForPath(item.path)
            
            withContext(Dispatchers.Main) {
                if (!capturedUrl.isNullOrEmpty()) {
                    btnGoToWeb?.text = "Web sitesine git"
                    btnGoToWeb?.tag = "active"
                    btnGoToWeb?.visibility = if (isUiHidden) View.GONE else View.VISIBLE
                    btnGoToWeb?.bringToFront()
                    btnGoToWeb?.requestLayout()
                    btnGoToWeb?.setOnClickListener {
                        try {
                            val finalUrl = if (!capturedUrl.startsWith("http")) "http://$capturedUrl" else capturedUrl
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)))
                        } catch (e: Exception) {
                            showFsCustomToast("Tarayıcı açılamadı", android.R.drawable.ic_menu_info_details)
                        }
                    }
                } else {
                    val fileName = File(item.path).name
                    val browserInfo = detectBrowserFromFileName(fileName)
                    if (browserInfo != null) {
                        val (buttonText, packageName) = browserInfo
                        btnGoToWeb?.text = buttonText
                        btnGoToWeb?.tag = "active"
                        btnGoToWeb?.visibility = if (isUiHidden) View.GONE else View.VISIBLE
                        btnGoToWeb?.bringToFront()
                        btnGoToWeb?.requestLayout()
                        btnGoToWeb?.setOnClickListener {
                            try {
                                val intent = packageManager.getLaunchIntentForPackage(packageName)
                                if (intent != null) {
                                    startActivity(intent)
                                } else {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                                    if (browserIntent.resolveActivity(packageManager) != null) {
                                        startActivity(browserIntent)
                                        showFsCustomToast("$buttonText bulunamadı, varsayılan tarayıcı açılıyor", android.R.drawable.ic_menu_info_details)
                                    } else {
                                        val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                                        if (playIntent.resolveActivity(packageManager) != null) {
                                            startActivity(playIntent)
                                        } else {
                                            showFsCustomToast("Tarayıcı bulunamadı", android.R.drawable.ic_menu_info_details)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                showFsCustomToast("Tarayıcı açılamadı", android.R.drawable.ic_menu_info_details)
                            }
                        }
                    } else {
                        btnGoToWeb?.visibility = View.GONE
                        btnGoToWeb?.tag = null
                    }
                }
            }
        }
    }

    private fun detectBrowserFromFileName(fileName: String): Pair<String, String>? {
        val lowerName = fileName.lowercase(Locale.ROOT)
        return when {
            lowerName.contains("_chrome") -> "Chrome'u Aç" to "com.android.chrome"
            lowerName.contains("_firefox") -> "Firefox'u Aç" to "org.mozilla.firefox"
            lowerName.contains("_samsung") || lowerName.contains("_internet") -> "Samsung İnternet'i Aç" to "com.sec.android.app.sbrowser"
            else -> null
        }
    }

    fun toggleUIVisibility(): Boolean {
        isUiHidden = !isUiHidden
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        val bottomUI = findViewById<View>(R.id.bottomUIContainer)
        val viewsToAnimate = mutableListOf<View>()
        
        if (bottomUI != null) {
            viewsToAnimate.add(bottomUI)
        } else {
            viewsToAnimate.add(filmstripRecycler)
            viewsToAnimate.add(btnFavorite)
            viewsToAnimate.add(btnEdit)
            viewsToAnimate.add(btnShare)
            viewsToAnimate.add(btnTrash)
            viewsToAnimate.add(btnMore)
        }
        
        if (btnGoToWeb?.tag == "active") {
            btnGoToWeb?.let { viewsToAnimate.add(it) }
        }

        val rv = viewPager.getChildAt(0) as? RecyclerView
        rv?.let {
            for (i in 0 until it.childCount) {
                val holder = it.getChildViewHolder(it.getChildAt(i)) as? FsFullScreenAdapter.ViewHolder
                holder?.controlsLayout?.let { controls -> viewsToAnimate.add(controls) }
            }
        }

        activeViewHolder?.controlsLayout?.visibility = if (isUiHidden) View.GONE else View.VISIBLE

        if (isUiHidden) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            viewsToAnimate.forEach { view ->
                view.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction { view.visibility = View.GONE }
                    .start()
            }
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            viewsToAnimate.forEach { view ->
                view.visibility = View.VISIBLE
                view.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .withEndAction(null)
                    .start()
            }
            if (btnGoToWeb?.tag == "active") {
                btnGoToWeb?.bringToFront()
            }
        }

        return isUiHidden
    }

    fun getViewHolder(pos: Int): FsFullScreenAdapter.ViewHolder? {
        val rv = viewPager.getChildAt(0) as? RecyclerView
        return rv?.findViewHolderForAdapterPosition(pos) as? FsFullScreenAdapter.ViewHolder
    }

    fun startProgressUpdater(holder: FsFullScreenAdapter.ViewHolder) {
        timeHandler.removeCallbacks(updateTimeRunnable)
        activeViewHolder = holder
        timeHandler.post(updateTimeRunnable)
    }

    override fun onPause() {
        super.onPause()
        timeHandler.removeCallbacksAndMessages(null)
        activeViewHolder = null
        try {
            val rv = viewPager.getChildAt(0) as? RecyclerView
            rv?.let {
                for (i in 0 until it.childCount) {
                    val holder = it.getChildViewHolder(it.getChildAt(i)) as? FsFullScreenAdapter.ViewHolder
                    holder?.let { h ->
                        if (h.videoView.isPlaying) {
                            h.videoView.pause()
                        }
                        h.videoView.suspend() 
                        
                        if(!isUiHidden) {
                            h.controlsLayout.visibility = View.VISIBLE
                        }
                        h.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
                    }
                }
            }
        } catch (e: Exception) {}
        detailsDialog?.dismiss()
        trashDialog?.dismiss()
    }

    override fun onResume() {
        super.onResume()
        val themePrefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val currentTheme = themePrefs.getString("appTheme", "Sistem Teması")
        val newAmoled = currentTheme == "Koyu Amoled Tema"
        if (isAmoledTheme != newAmoled) {
            isAmoledTheme = newAmoled
        }
        applyDynamicColorsToUI()

        try {
            val rv = viewPager.getChildAt(0) as? RecyclerView
            rv?.let {
                for (i in 0 until it.childCount) {
                    val holder = it.getChildViewHolder(it.getChildAt(i)) as? FsFullScreenAdapter.ViewHolder
                    holder?.videoView?.resume()
                }
            }
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        timeHandler.removeCallbacksAndMessages(null)
        activeViewHolder = null
        try {
            val rv = viewPager.getChildAt(0) as? RecyclerView
            rv?.let {
                for (i in 0 until it.childCount) {
                    val holder = it.getChildViewHolder(it.getChildAt(i)) as? FsFullScreenAdapter.ViewHolder
                    holder?.videoView?.post {
                        try { holder?.videoView?.stopPlayback() } catch (e: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {}
        detailsDialog?.dismiss()
        trashDialog?.dismiss()
    }

    fun updateFavoriteIcon() {
        if (currentPosition >= MainActivity.displayedMediaList.size) return
        val iconTint = ContextCompat.getColor(this, R.color.p_app_icon_tint)
        if (MainActivity.favoritePaths.contains(MainActivity.displayedMediaList[currentPosition].path)) {
            btnFavorite.setImageResource(R.drawable.ic_fs_heart_filled)
            btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252"))
        } else {
            btnFavorite.setImageResource(R.drawable.ic_fs_heart)
            btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(iconTint)
        }
    }

    fun toggleFavorite() {
        if (currentPosition >= MainActivity.displayedMediaList.size) return
        val item = MainActivity.displayedMediaList[currentPosition]
        val isVideo = item.isVideo
        val typeStr = if (isVideo) "video" else "fotoğraf"
        
        if (MainActivity.favoritePaths.contains(item.path)) {
            MainActivity.favoritePaths.remove(item.path)
            showFsCustomToast("1 $typeStr favorilerden çıkarıldı", 0)
        } else {
            MainActivity.favoritePaths.add(item.path)
            showFsCustomToast("1 $typeStr favorilere eklendi", 0)
        }
        
        MainActivity.saveFavoritePaths(this)
        updateFavoriteIcon()
    }
}
