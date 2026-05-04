package com.sahin.galerim

import android.Manifest
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.transition.Fade
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sahin.galerim.data.AppDatabase
import com.sahin.galerim.data.HiddenMedia
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class FullScreenActivity : AppCompatActivity() {
    private var placeholderDrawable: android.graphics.drawable.Drawable? = null

    private fun getPlaceholder(): android.graphics.drawable.Drawable {
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

    private fun isUnsupportedFormat(path: String): Boolean {
        val p = path.lowercase(java.util.Locale.getDefault())
        return p.endsWith(".tif") || p.endsWith(".tiff")
    }


    private lateinit var viewPager: ViewPager2
    private lateinit var filmstripRecycler: RecyclerView
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnEdit: ImageButton
    private lateinit var btnShare: ImageButton
    private lateinit var btnTrash: ImageButton
    private lateinit var btnMore: ImageButton
    private var currentPosition: Int = 0
    
    private var detailsDialog: BottomSheetDialog? = null
    private var trashDialog: BottomSheetDialog? = null
    private var btnGoToWeb: TextView? = null

    private var isGlobalMuted = false 
    private val timeHandler = Handler(Looper.getMainLooper())
    private var activeViewHolder: FullScreenAdapter.ViewHolder? = null
    private var isUserSeeking = false 
    
    var isUiHidden = false
    private var isAmoledTheme = false

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                showCustomToast(this, "Hedef klasör seçildi. Dosya işlemi başlatılıyor...", android.R.drawable.ic_menu_info_details)
            }
        }
    }

    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            activeViewHolder?.let { holder ->
                try {
                    if (holder.videoView.isPlaying && !isUserSeeking) {
                        val currentMs = holder.videoView.currentPosition
                        holder.tvCurrentTime.text = formatTime(currentMs)
                        holder.videoSeekBar.progress = currentMs
                        timeHandler.postDelayed(this, 250) 
                    }
                } catch (e: Exception) {}
            }
        }
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
        
        viewPager.layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, 
            RelativeLayout.LayoutParams.MATCH_PARENT
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

        reduceDragSensitivity(viewPager)
        currentPosition = intent.getIntExtra("position", 0)
        if (currentPosition >= MainActivity.displayedMediaList.size) currentPosition = 0
        
        viewPager.adapter = FullScreenAdapter(MainActivity.displayedMediaList)
        viewPager.setCurrentItem(currentPosition, false)
        
        val layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        filmstripRecycler.layoutManager = layoutManager
        filmstripRecycler.adapter = FilmstripAdapter(MainActivity.displayedMediaList)
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

    private fun checkAndShowWebButton(position: Int) {
        if (position < 0 || position >= MainActivity.displayedMediaList.size) return
        val item = MainActivity.displayedMediaList[position]
        
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
                            showCustomToast(this@FullScreenActivity, "Tarayıcı açılamadı", android.R.drawable.ic_menu_info_details)
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
                                        showCustomToast(this@FullScreenActivity, "$buttonText bulunamadı, varsayılan tarayıcı açılıyor", android.R.drawable.ic_menu_info_details)
                                    } else {
                                        val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                                        if (playIntent.resolveActivity(packageManager) != null) {
                                            startActivity(playIntent)
                                        } else {
                                            showCustomToast(this@FullScreenActivity, "Tarayıcı bulunamadı", android.R.drawable.ic_menu_info_details)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                showCustomToast(this@FullScreenActivity, "Tarayıcı açılamadı", android.R.drawable.ic_menu_info_details)
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
                val holder = it.getChildViewHolder(it.getChildAt(i)) as? FullScreenAdapter.ViewHolder
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

    private fun getViewHolder(pos: Int): FullScreenAdapter.ViewHolder? {
        val rv = viewPager.getChildAt(0) as? RecyclerView
        return rv?.findViewHolderForAdapterPosition(pos) as? FullScreenAdapter.ViewHolder
    }

    private fun startProgressUpdater(holder: FullScreenAdapter.ViewHolder) {
        timeHandler.removeCallbacks(updateTimeRunnable)
        activeViewHolder = holder
        timeHandler.post(updateTimeRunnable)
    }

    private fun formatTime(ms: Int): String {
        val s = (ms / 1000) % 60; val m = (ms / (1000 * 60)) % 60; val h = ms / (1000 * 60 * 60)
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    private fun handleEdit() {
        if (currentPosition >= MainActivity.displayedMediaList.size) return
        val item = MainActivity.displayedMediaList[currentPosition]
        if (!item.isVideo) {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_EDIT).apply { setDataAndType(item.uri, "image/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Fotoğrafı düzenle"))
        } else { showCustomToast(this, "Videolar şimdilik düzenlenemez.", android.R.drawable.ic_menu_info_details) }
    }

    private fun handleShare() {
        if (currentPosition >= MainActivity.displayedMediaList.size) return
        val item = MainActivity.displayedMediaList[currentPosition]
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = if (item.isVideo) "video/*" else "image/*"; putExtra(Intent.EXTRA_STREAM, item.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Paylaş"))
    }

    private fun showTrashDialog() {
        if (currentPosition >= MainActivity.displayedMediaList.size) return
        val currentItem = MainActivity.displayedMediaList[currentPosition]
        val useTrash = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE).getBoolean("useTrash", true)
        val dialogBgColor = ContextCompat.getColor(this, R.color.p_app_dialog_bg)
        val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
        
        trashDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_trash_confirmation, null)
        trashDialog?.setContentView(view)
        
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 60f
            setColor(if (isAmoledTheme) Color.BLACK else dialogBgColor)
        }
        
        trashDialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
        
        val messageView = view.findViewById<TextView>(R.id.dialogMessage)
        messageView.setTextColor(primaryColor)

        val itemType = if (currentItem.isVideo) "video" else "fotoğraf"
        if (useTrash) {
            messageView.text = "1 $itemType çöp kutusuna gönderilsin mi?"
        } else {
            messageView.text = "1 $itemType kalıcı olarak silinsin mi?"
        }
        
        val btnConfirm = view.findViewById<AppCompatButton>(R.id.btnConfirm)
        val btnCancel = view.findViewById<AppCompatButton>(R.id.btnCancel)
        val parentLayout = btnConfirm.parent as? ViewGroup
        
        if (parentLayout is LinearLayout) {
            parentLayout.removeAllViews()
            parentLayout.gravity = Gravity.CENTER
            
            val dp10 = (10 * resources.displayMetrics.density).toInt()
            val btnWidth = (110 * resources.displayMetrics.density).toInt() 
            val btnHeight = (42 * resources.displayMetrics.density).toInt()
            
            val btnCancelNew = AppCompatButton(this).apply {
                text = "Hayır"
                setTextColor(Color.WHITE)
                isAllCaps = false
                textSize = 15f
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#4CAF50")) 
                    cornerRadius = 30f
                }
                layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply {
                    marginEnd = dp10
                }
                setOnClickListener { trashDialog?.dismiss() }
            }
            
            val btnConfirmNew = AppCompatButton(this).apply {
                text = "Evet"
                setTextColor(Color.WHITE)
                isAllCaps = false
                textSize = 15f
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FF5252")) 
                    cornerRadius = 30f
                }
                layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply {
                    marginStart = dp10
                }
                setOnClickListener {
                    trashDialog?.dismiss()
                    if (useTrash) moveToAppTrash() else deletePermanently()
                }
            }
            
            parentLayout.addView(btnCancelNew)
            parentLayout.addView(btnConfirmNew)
        }

        trashDialog?.show()
    }

    private fun moveToAppTrash() {
        if (currentPosition >= MainActivity.displayedMediaList.size) return
        val item = MainActivity.displayedMediaList[currentPosition]
        MainActivity.trashedPaths.add(item.path)
        MainActivity.saveTrashedPaths(this)
        MainActivity.mediaList.remove(item)
        MainActivity.trashList.add(item)
        MainActivity.displayedMediaList.removeAt(currentPosition)
        
        MainActivity.forceReload = true
        
        if (currentPosition >= MainActivity.displayedMediaList.size && currentPosition > 0) {
            currentPosition--
        }
        
        viewPager.adapter?.notifyDataSetChanged()
        filmstripRecycler.adapter?.notifyDataSetChanged()
        
        val msg = if (item.isVideo) "1 video çöpe taşındı" else "1 fotoğraf çöpe taşındı"
        showCustomToast(this, msg, R.drawable.ic_action_delete)
        if (MainActivity.displayedMediaList.isEmpty()) finish()
    }

    private fun deletePermanently() {
        if (currentPosition >= MainActivity.displayedMediaList.size) return
        val item = MainActivity.displayedMediaList[currentPosition]
        try {
            val file = File(item.path)
            if (file.exists() && file.delete()) {
                contentResolver.delete(item.uri, null, null)
                MainActivity.trashedPaths.remove(item.path)
            } else {
                val rows = contentResolver.delete(item.uri, null, null)
                if (rows > 0) MainActivity.trashedPaths.remove(item.path)
            }
        } catch (e: Exception) {}
        MainActivity.saveTrashedPaths(this)
        MainActivity.mediaList.remove(item)
        MainActivity.trashList.remove(item)
        MainActivity.displayedMediaList.removeAt(currentPosition)
        
        MainActivity.forceReload = true
        
        if (currentPosition >= MainActivity.displayedMediaList.size && currentPosition > 0) {
            currentPosition--
        }
        
        viewPager.adapter?.notifyDataSetChanged()
        filmstripRecycler.adapter?.notifyDataSetChanged()
        
        val msg = if (item.isVideo) "1 video kalıcı olarak silindi" else "1 fotoğraf kalıcı olarak silindi"
        showCustomToast(this, msg, R.drawable.ic_action_delete)
        if (MainActivity.displayedMediaList.isEmpty()) finish()
    }

    override fun onPause() {
        super.onPause()
        timeHandler.removeCallbacksAndMessages(null)
        activeViewHolder = null
        try {
            val rv = viewPager.getChildAt(0) as? RecyclerView
            rv?.let {
                for (i in 0 until it.childCount) {
                    val holder = it.getChildViewHolder(it.getChildAt(i)) as? FullScreenAdapter.ViewHolder
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
                    val holder = it.getChildViewHolder(it.getChildAt(i)) as? FullScreenAdapter.ViewHolder
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
                    val holder = it.getChildViewHolder(it.getChildAt(i)) as? FullScreenAdapter.ViewHolder
                    holder?.videoView?.post {
                        try { holder?.videoView?.stopPlayback() } catch (e: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {}
        detailsDialog?.dismiss()
        trashDialog?.dismiss()
    }

    private fun reduceDragSensitivity(viewPager: ViewPager2) {
        try {
            val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
            recyclerViewField.isAccessible = true
            val recyclerView = recyclerViewField.get(viewPager) as RecyclerView
            val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
            touchSlopField.isAccessible = true
            val touchSlop = touchSlopField.get(recyclerView) as Int
            touchSlopField.set(recyclerView, touchSlop * 2)
        } catch (e: Exception) {}
    }

    private fun updateFavoriteIcon() {
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

    private fun toggleFavorite() {
        if (currentPosition >= MainActivity.displayedMediaList.size) return
        val item = MainActivity.displayedMediaList[currentPosition]
        val isVideo = item.isVideo
        val typeStr = if (isVideo) "video" else "fotoğraf"
        
        if (MainActivity.favoritePaths.contains(item.path)) {
            MainActivity.favoritePaths.remove(item.path)
            showCustomToast(this, "1 $typeStr favorilerden çıkarıldı", 0)
        } else {
            MainActivity.favoritePaths.add(item.path)
            showCustomToast(this, "1 $typeStr favorilere eklendi", 0)
        }
        
        MainActivity.saveFavoritePaths(this)
        updateFavoriteIcon()
    }

        private fun performHideMedia() {
        if (currentPosition >= MainActivity.displayedMediaList.size) return
        val item = MainActivity.displayedMediaList[currentPosition]

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@FullScreenActivity)
            val hiddenFolder = File(filesDir, "hidden_vault")
            if (!hiddenFolder.exists()) hiddenFolder.mkdirs()

            var success = false
            try {
                val sourceFile = File(item.path)
                if (sourceFile.exists()) {
                    val originalDate = item.dateAdded * 1000L 
                    // İsim çakışmasını önlemek için zaman damgası eklendi
                    val destFile = File(hiddenFolder, "${System.currentTimeMillis()}_${sourceFile.name}")
                    
                    FileInputStream(sourceFile).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    if (destFile.exists()) {
                        destFile.setLastModified(originalDate) 
                        
                        val hiddenEntry = HiddenMedia(
                            originalPath = sourceFile.absolutePath,
                            hiddenPath = destFile.absolutePath,
                            isVideo = item.isVideo,
                            dateAdded = System.currentTimeMillis(),
                            originalDate = originalDate
                        )
                        db.hiddenMediaDao().insert(hiddenEntry)

                        sourceFile.delete()
                        contentResolver.delete(item.uri, null, null)
                        success = true
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

            withContext(Dispatchers.Main) {
                if (success) {
                    val msg = if (item.isVideo) "1 video gizlendi" else "1 fotoğraf gizlendi"
                    showCustomToast(this@FullScreenActivity, msg, android.R.drawable.ic_secure)
                    
                    MainActivity.mediaList.remove(item)
                    MainActivity.displayedMediaList.removeAt(currentPosition)
                    MainActivity.forceReload = true
                    
                    if (currentPosition >= MainActivity.displayedMediaList.size && currentPosition > 0) {
                        currentPosition--
                    }
                    
                    viewPager.adapter?.notifyDataSetChanged()
                    filmstripRecycler.adapter?.notifyDataSetChanged()
                    if (MainActivity.displayedMediaList.isEmpty()) finish()
                } else {
                    showCustomToast(this@FullScreenActivity, "Gizleme başarısız oldu", android.R.drawable.ic_menu_info_details)
                }
            }
        }
    }


    private fun showAlbumSelectionDialog(action: String, itemsToProcess: List<MediaItem>) {
        val dialogBgColor = ContextCompat.getColor(this, R.color.p_app_dialog_bg)
        val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
        val dialog = BottomSheetDialog(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 64)
            background = android.graphics.drawable.GradientDrawable().apply { 
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 60f
                setColor(if (isAmoledTheme) Color.BLACK else dialogBgColor) 
            }
        }
        
        val title = TextView(this).apply {
            text = if (action == "COPY") "Kopyalanacak Albümü Seçin" else "Taşınacak Albümü Seçin"
            setTextColor(primaryColor)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(24, 0, 0, 40)
        }
        layout.addView(title)
        
        val uniqueAlbums = mutableListOf<Album>()
        val sdCardPattern = Regex("^[A-Za-z0-9]{4}-[A-Za-z0-9]{4}$")

        MainActivity.mediaList.groupBy { File(it.path).parentFile?.absolutePath }.forEach { (path, items) ->
            if (path != null && items.isNotEmpty()) {
                val folder = File(path)
                val rawName = folder.name
                val displayName = when {
                    rawName == "0" -> "Dahili Depolama"
                    sdCardPattern.matches(rawName) -> "Hafıza Kartı"
                    else -> rawName
                }
                uniqueAlbums.add(Album(null, path, displayName, items.first().uri, items.size))
            }
        }
        uniqueAlbums.sortByDescending { it.count }
        
        val recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@FullScreenActivity, 3)
            adapter = DialogAlbumAdapter(uniqueAlbums) { selectedAlbum ->
                dialog.dismiss()
                processCopyMove(action, itemsToProcess, File(selectedAlbum.locationName!!))
            }
        }
        layout.addView(recyclerView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        
        dialog.setContentView(layout)
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
        dialog.show()
    }

    inner class DialogAlbumAdapter(private val albums: List<Album>, private val onClick: (Album) -> Unit) : RecyclerView.Adapter<DialogAlbumAdapter.ViewHolder>() {
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val thumb: ImageView = v.findViewById(R.id.thumbnail)
            val name: TextView = v.findViewById(R.id.albumName)
            init { v.setOnClickListener { onClick(albums[bindingAdapterPosition]) } }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(layoutInflater.inflate(R.layout.item_album, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val a = albums[position]
            Glide.with(this@FullScreenActivity).asBitmap().load(a.thumbnail).centerCrop().into(holder.thumb)
            holder.name.text = "${a.name}\n${a.count}"
            holder.name.setTextColor(ContextCompat.getColor(this@FullScreenActivity, R.color.p_app_text_primary))
        }
        override fun getItemCount() = albums.size
    }

    private fun processCopyMove(action: String, items: List<MediaItem>, destFolder: File) {
        showCustomToast(this, "İşlem başlatıldı...", android.R.drawable.ic_menu_info_details)
        lifecycleScope.launch(Dispatchers.IO) {
            var pCount = 0
            var vCount = 0
            for (item in items) {
                try {
                    val source = File(item.path)
                    val dest = File(destFolder, source.name)
                    if (source.exists() && source.absolutePath != dest.absolutePath) {
                        source.copyTo(dest, overwrite = true)
                        android.media.MediaScannerConnection.scanFile(this@FullScreenActivity, arrayOf(dest.absolutePath), null, null)
                        
                        if (action == "MOVE") {
                            source.delete()
                            contentResolver.delete(item.uri, null, null)
                        }
                        if (item.isVideo) vCount++ else pCount++
                    }
                } catch (e: Exception) {}
            }
            withContext(Dispatchers.Main) {
                val actionText = if(action=="COPY") "kopyalandı" else "taşındı"
                val msg = when {
                    pCount > 0 && vCount > 0 -> "$pCount fotoğraf ve $vCount video $actionText"
                    pCount > 0 -> "$pCount fotoğraf $actionText"
                    vCount > 0 -> "$vCount video $actionText"
                    else -> ""
                }
                showCustomToast(this@FullScreenActivity, msg, android.R.drawable.ic_menu_info_details)
                if (action == "MOVE") {
                    MainActivity.forceReload = true
                    finish()
                }
            }
        }
    }

    private fun showMoreMenu(view: View) {
        if (currentPosition >= MainActivity.displayedMediaList.size) return
        val dialogBgColor = ContextCompat.getColor(this, R.color.p_app_dialog_bg)
        val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
        
        val menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { 
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 45f
                setColor(if (isAmoledTheme) Color.BLACK else dialogBgColor) 
            }
            setPadding(0, 24, 0, 24)
        }
        
        val popup = PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.elevation = 30f 
        
        val options = mutableListOf("Ayrıntılar", "Gizle", "Kopyala", "Taşı", "Tarih ve saati düzenle", "Konumu düzenle")
        val item = MainActivity.displayedMediaList[currentPosition]
        
        if (item.isVideo) { 
            options.add("Video oynatıcıda aç") 
        }

        for (opt in options) {
            menuLayout.addView(TextView(this).apply {
                text = opt
                setTextColor(primaryColor)
                textSize = 15f
                setPadding(64, 32, 64, 32)
                setOnClickListener {
                    popup.dismiss()
                    when(opt) {
                        "Ayrıntılar" -> { showModernDetailsBottomSheet() }
                        "Gizle" -> { performHideMedia() }
                        "Kopyala" -> { showAlbumSelectionDialog("COPY", listOf(item)) }
                        "Taşı" -> { showAlbumSelectionDialog("MOVE", listOf(item)) }
                        "Tarih ve saati düzenle" -> { showDateEditDialog() }
                        "Konumu düzenle" -> { showLocationEditDialog(view, listOf(item)) }
                        "Video oynatıcıda aç" -> { 
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(item.uri, "video/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                startActivity(intent)
                            } catch (e: Exception) { showCustomToast(this@FullScreenActivity, "Oynatıcı bulunamadı", android.R.drawable.ic_menu_info_details) }
                        }
                    }
                }
            })
        }
        
        menuLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.showAsDropDown(view, -50, -(menuLayout.measuredHeight + view.height + 30))
    }

    private fun showDateEditDialog() {
        val cal = Calendar.getInstance()
        val dialogBgColor = ContextCompat.getColor(this, R.color.p_app_dialog_bg)
        val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
        val dialog = BottomSheetDialog(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 64)
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 60f; setColor(if (isAmoledTheme) Color.BLACK else dialogBgColor) }
        }

        layout.addView(TextView(this).apply {
            text = "Tarih ve Saati Düzenle"
            setTextColor(primaryColor); textSize = 18f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(24, 0, 0, 40)
        })

        val pickersLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 40)
        }

        val npDay = NumberPicker(this).apply { minValue = 1; maxValue = 31; value = cal.get(Calendar.DAY_OF_MONTH) }
        val npMonth = NumberPicker(this).apply { minValue = 0; maxValue = 11; displayedValues = arrayOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"); value = cal.get(Calendar.MONTH) }
        val npYear = NumberPicker(this).apply { minValue = 1970; maxValue = 2050; value = cal.get(Calendar.YEAR) }
        val npHour = NumberPicker(this).apply { minValue = 0; maxValue = 23; value = cal.get(Calendar.HOUR_OF_DAY) }
        val npMin = NumberPicker(this).apply { minValue = 0; maxValue = 59; value = cal.get(Calendar.MINUTE) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            npDay.textColor = primaryColor; npMonth.textColor = primaryColor; npYear.textColor = primaryColor; npHour.textColor = primaryColor; npMin.textColor = primaryColor
        }

        pickersLayout.addView(npDay)
        pickersLayout.addView(npMonth)
        pickersLayout.addView(npYear)
        pickersLayout.addView(TextView(this).apply { text = "  " })
        pickersLayout.addView(npHour)
        pickersLayout.addView(TextView(this).apply { text = ":" ; setTextColor(primaryColor); textSize = 20f; setPadding(10,0,10,0) })
        pickersLayout.addView(npMin)

        layout.addView(pickersLayout)

        val btnLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        btnLayout.addView(TextView(this).apply { text = "İptal"; setTextColor(Color.parseColor("#888888")); textSize = 16f; setPadding(32, 24, 32, 24); setOnClickListener { dialog.dismiss() } })
        btnLayout.addView(TextView(this).apply {
            text = "Kaydet"
            setTextColor(Color.parseColor("#FF9800")); textSize = 16f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(32, 24, 32, 24)
            setOnClickListener {
                dialog.dismiss()
                cal.set(npYear.value, npMonth.value, npDay.value, npHour.value, npMin.value)
                saveNewDate(cal.timeInMillis)
            }
        })
        layout.addView(btnLayout)

        dialog.setContentView(layout)
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
        dialog.show()
    }

    private fun saveNewDate(newTime: Long) {
        if (currentPosition >= MainActivity.displayedMediaList.size) return
        val item = MainActivity.displayedMediaList[currentPosition]
        val dateStr = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(java.util.Date(newTime))
        
        showCustomToast(this, "Tarih güncelleniyor...", android.R.drawable.ic_menu_info_details)
        
        lifecycleScope.launch(Dispatchers.IO) {
            val timeInSeconds = newTime / 1000L
            item.dateAdded = timeInSeconds
            
            try {
                if (!item.isVideo) {
                    val exif = ExifInterface(item.path)
                    exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
                    exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
                    exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)
                    exif.saveAttributes()
                }
                
                File(item.path).setLastModified(newTime)
                
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATE_MODIFIED, timeInSeconds)
                    if (!item.isVideo) put(MediaStore.Images.Media.DATE_TAKEN, newTime)
                    else put(MediaStore.Video.Media.DATE_TAKEN, newTime)
                }
                contentResolver.update(item.uri, values, null, null)

                MediaScannerConnection.scanFile(this@FullScreenActivity, arrayOf(item.path), null, null)

                withContext(Dispatchers.Main) { 
                    showCustomToast(this@FullScreenActivity, "Tarih başarıyla güncellendi", android.R.drawable.ic_menu_info_details)
                    MainActivity.mediaList.sortByDescending { it.dateAdded }
                    MainActivity.forceReload = true 
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    showCustomToast(this@FullScreenActivity, "Hata: Tarih güncellenemedi", android.R.drawable.ic_menu_info_details)
                }
            }
        }
    }

    private fun showClearLocationConfirmationDialog(items: List<MediaItem>) {
        val dialogBgColor = ContextCompat.getColor(this, R.color.p_app_dialog_bg)
        val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
        
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_trash_confirmation, null)
        dialog.setContentView(view)
        
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 60f
            setColor(if (isAmoledTheme) Color.BLACK else dialogBgColor)
        }
        
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
        
        val messageView = view.findViewById<TextView>(R.id.dialogMessage)
        messageView.gravity = Gravity.CENTER
        messageView.setTextColor(primaryColor)

        val item = items.first()
        val itemType = if (item.isVideo) "videonun" else "fotoğrafın"
        messageView.text = "1 $itemType konum bilgisi temizlensin mi?"
        
        val btnConfirm = view.findViewById<AppCompatButton>(R.id.btnConfirm)
        val parentLayout = btnConfirm.parent as? ViewGroup
        
        if (parentLayout is LinearLayout) {
            parentLayout.removeAllViews()
            parentLayout.gravity = Gravity.CENTER
            
            val dp10 = (10 * resources.displayMetrics.density).toInt()
            val btnWidth = (110 * resources.displayMetrics.density).toInt() 
            val btnHeight = (42 * resources.displayMetrics.density).toInt()
            
            val btnCancelNew = AppCompatButton(this).apply {
                text = "İptal"
                setTextColor(Color.WHITE)
                isAllCaps = false
                textSize = 15f
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#4CAF50")) 
                    cornerRadius = 30f
                }
                layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply {
                    marginEnd = dp10
                }
                setOnClickListener { dialog.dismiss() }
            }
            
            val btnConfirmNew = AppCompatButton(this).apply {
                text = "Temizle"
                setTextColor(Color.WHITE)
                isAllCaps = false
                textSize = 15f
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FF5252")) 
                    cornerRadius = 30f
                }
                layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply {
                    marginStart = dp10
                }
                setOnClickListener {
                    dialog.dismiss()
                    clearLocationData(items)
                }
            }
            
            parentLayout.addView(btnCancelNew)
            parentLayout.addView(btnConfirmNew)
        }

        dialog.show()
    }

    private fun showLocationEditDialog(anchor: View, items: List<MediaItem>) {
        val dialogBgColor = ContextCompat.getColor(this, R.color.p_app_dialog_bg)
        val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
        val iconTint = ContextCompat.getColor(this, R.color.p_app_icon_tint)

        val menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { 
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 45f
                setColor(if (isAmoledTheme) Color.BLACK else dialogBgColor) 
            }
            setPadding(0, 24, 0, 24)
        }
        
        val popup = PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.elevation = 30f 

        val btnSelect = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(64, 32, 64, 32)
            setOnClickListener {
                popup.dismiss()
                showInteractiveMapDialog(items)
            }
        }
        btnSelect.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_menu_locations)
            setColorFilter(iconTint)
            layoutParams = LinearLayout.LayoutParams(64, 64).apply { setMargins(0, 0, 32, 0) }
        })
        btnSelect.addView(TextView(this).apply {
            text = "Konum seç"
            setTextColor(primaryColor)
            textSize = 15f
        })
        menuLayout.addView(btnSelect)

        val btnClear = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(64, 32, 64, 32)
            setOnClickListener {
                popup.dismiss()
                showClearLocationConfirmationDialog(items)
            }
        }
        btnClear.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_action_delete)
            setColorFilter(Color.parseColor("#FF5252"))
            layoutParams = LinearLayout.LayoutParams(64, 64).apply { setMargins(0, 0, 32, 0) }
        })
        btnClear.addView(TextView(this).apply {
            text = "Konum temizle"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 15f
        })
        menuLayout.addView(btnClear)

        menuLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.showAsDropDown(anchor, -50, -(menuLayout.measuredHeight + anchor.height + 30))
    }

    private fun showInteractiveMapDialog(items: List<MediaItem>) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            }
        }
        
        val bgColor = ContextCompat.getColor(this, R.color.p_app_background)
        val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
        val iconTint = ContextCompat.getColor(this, R.color.p_app_icon_tint)

        val actualBg = if (isAmoledTheme) Color.BLACK else bgColor

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(actualBg)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        
        val topBar = android.widget.RelativeLayout(this).apply {
            setPadding(40, 40, 40, 40)
            setBackgroundColor(actualBg)
        }
        val btnClose = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(iconTint)
            setOnClickListener { dialog.dismiss() }
        }
        val topTitle = TextView(this).apply {
            text = "Haritadan Konum Seç"
            setTextColor(primaryColor)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val paramsTitle = android.widget.RelativeLayout.LayoutParams(android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT, android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(android.widget.RelativeLayout.CENTER_IN_PARENT)
        }
        topBar.addView(btnClose)
        topBar.addView(topTitle, paramsTitle)
        layout.addView(topBar)
        
        var selectedLat: Double? = null
        var selectedLng: Double? = null
        
        val webView = android.webkit.WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            webViewClient = android.webkit.WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setGeolocationEnabled(true)
            
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(origin: String, callback: android.webkit.GeolocationPermissions.Callback) {
                    callback.invoke(origin, true, false)
                }
            }

            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun onLocationPicked(lat: Double, lng: Double) {
                    selectedLat = lat
                    selectedLng = lng
                }
                @android.webkit.JavascriptInterface
                fun showToast(msg: String) {
                    post { showCustomToast(this@FullScreenActivity, msg, android.R.drawable.ic_menu_info_details) }
                }
            }, "Android")
            
            val html = """<!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    html, body { height: 100%; margin: 0; padding: 0; background: #e5e5e5; }
                    #map { height: 100%; width: 100%; } 
                    .locate-btn { background: white; border: 2px solid rgba(0,0,0,0.2); border-radius: 50%; width: 44px; height: 44px; font-size: 22px; cursor: pointer; line-height: 44px; text-align: center; text-decoration: none; color: #333; display: flex; justify-content: center; align-items: center; box-shadow: 0 2px 6px rgba(0,0,0,0.3); margin-top: 15px !important; margin-right: 15px !important; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map', {zoomControl: false}).setView([39.0, 35.0], 5);
                    L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
                        maxZoom: 20,
                        attribution: 'Google'
                    }).addTo(map);
                    
                    L.control.zoom({position: 'bottomright'}).addTo(map);
                    
                    var marker;
                    function updateMarker(lat, lng) {
                        if(marker) map.removeLayer(marker);
                        marker = L.marker([lat, lng], {draggable: true}).addTo(map);
                        marker.on('dragend', function(e) {
                            var pos = e.target.getLatLng();
                            Android.onLocationPicked(pos.lat, pos.lng);
                        });
                        Android.onLocationPicked(lat, lng);
                    }
                    
                    var LocateControl = L.Control.extend({
                        options: {position: 'topright'},
                        onAdd: function(map) {
                            var container = L.DomUtil.create('div', 'leaflet-bar leaflet-control');
                            container.style.border = 'none';
                            container.style.boxShadow = 'none';
                            var button = L.DomUtil.create('a', 'locate-btn', container);
                            button.innerHTML = '🎯';
                            button.href = '#';
                            button.onclick = function(e) {
                                e.preventDefault();
                                e.stopPropagation();
                                Android.showToast("Konum aranıyor, lütfen bekleyin...");
                                map.locate({setView: true, maxZoom: 16, enableHighAccuracy: true, timeout: 15000, maximumAge: 0});
                            };
                            return container;
                        }
                    });
                    map.addControl(new LocateControl());
                    
                    map.on('locationfound', function(e) {
                        updateMarker(e.latlng.lat, e.latlng.lng);
                    });
                    map.on('locationerror', function(e) {
                        Android.showToast("Konum bulunamadı. Cihazın GPS'inin açık olduğundan emin olun.");
                    });
                    map.on('click', function(e) {
                        updateMarker(e.latlng.lat, e.latlng.lng);
                    });
                </script>
            </body>
            </html>"""
            loadDataWithBaseURL("https://app.local", html, "text/html", "UTF-8", null)
        }
        layout.addView(webView)
        
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(actualBg)
        }
        val btnCancel = TextView(this).apply {
            text = "İptal"
            setTextColor(primaryColor)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { dialog.dismiss() }
        }
        
        val btnSave = TextView(this).apply {
            text = "Tamamlandı"
            setTextColor(Color.parseColor("#FF9800"))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                if (selectedLat != null && selectedLng != null) {
                    dialog.dismiss()
                    updateLocationData(items, selectedLat!!, selectedLng!!)
                } else {
                    showCustomToast(this@FullScreenActivity, "Lütfen haritadan bir konum seçin", android.R.drawable.ic_menu_info_details)
                }
            }
        }
        
        bottomBar.addView(btnCancel)
        bottomBar.addView(btnSave)
        layout.addView(bottomBar)

        dialog.setContentView(layout)
        dialog.show()
    }

    private fun clearLocationData(items: List<MediaItem>) {
        showCustomToast(this, "Konum temizleniyor...", android.R.drawable.ic_menu_info_details)
        lifecycleScope.launch(Dispatchers.IO) {
            val item = items.first()
            if(!item.isVideo) {
                try {
                    val exif = ExifInterface(item.path)
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                    exif.saveAttributes()
                }catch(e:Exception){}
            }
            withContext(Dispatchers.Main) {
                val itemType = if (item.isVideo) "Video" else "Fotoğraf"
                showCustomToast(this@FullScreenActivity, "$itemType konum verileri temizlendi", android.R.drawable.ic_menu_info_details)
            }
        }
    }

    private fun convertDecimalToDMS(coord: Double): String {
        val absCoord = Math.abs(coord)
        val degree = absCoord.toInt()
        val minDouble = (absCoord - degree) * 60
        val minute = minDouble.toInt()
        val second = ((minDouble - minute) * 60 * 1000).toInt()
        return "$degree/1,$minute/1,$second/1000"
    }

    private fun updateLocationData(items: List<MediaItem>, lat: Double, lng: Double) {
        showCustomToast(this, "Konum güncelleniyor...", android.R.drawable.ic_menu_info_details)
        lifecycleScope.launch(Dispatchers.IO) {
            val latStr = convertDecimalToDMS(lat)
            val lngStr = convertDecimalToDMS(lng)
            val latRef = if (lat >= 0) "N" else "S"
            val lngRef = if (lng >= 0) "E" else "W"

            val item = items.first()
            if(!item.isVideo) {
                try {
                    val exif = ExifInterface(item.path)
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latStr)
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, lngStr)
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lngRef)
                    exif.saveAttributes()
                } catch(e:Exception){}
            }
            withContext(Dispatchers.Main) {
                val itemType = if (item.isVideo) "Video" else "Fotoğraf"
                showCustomToast(this@FullScreenActivity, "$itemType konumu başarıyla güncellendi", android.R.drawable.ic_menu_info_details)
            }
        }
    }

    @Suppress("DEPRECATION")
    fun showModernDetailsBottomSheet() {
        if (currentPosition >= MainActivity.displayedMediaList.size) return
        val item = MainActivity.displayedMediaList[currentPosition]
        val dialogBgColor = ContextCompat.getColor(this, R.color.p_app_dialog_bg)
        val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
        val secondaryColor = ContextCompat.getColor(this, R.color.p_app_text_secondary)
        
        detailsDialog = BottomSheetDialog(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(64, 48, 64, 64)
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 60f; setColor(if (isAmoledTheme) Color.BLACK else dialogBgColor) }
        }
        
        val dateStr = SimpleDateFormat("d MMMM yyyy HH:mm", Locale("tr")).format(java.util.Date(item.dateAdded * 1000))
        layout.addView(TextView(this).apply {
            text = dateStr
            setTextColor(primaryColor)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        })
        
        val file = File(item.path)
        var readablePath = (file.parent ?: "").replace("/storage/emulated/0", "Dahili depolama")
        
        val sdCardPattern = Regex("/storage/([A-Za-z0-9]{4}-[A-Za-z0-9]{4})")
        readablePath = readablePath.replace(sdCardPattern, "Hafıza Kartı")
        
        if (!readablePath.endsWith("/")) readablePath += "/"
        
        var resolutionStr = "Bilinmiyor"
        var locationStr = "Bilinmiyor"

        try {
            if (item.isVideo) {
                val retriever = MediaMetadataRetriever().apply { setDataSource(this@FullScreenActivity, item.uri) }
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
                if (w > 0 && h > 0) resolutionStr = "${w}x${h}  |  ${String.format("%.1f", (w * h) / 1000000.0)}MP"
                
                val locMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                if (locMeta != null) {
                    val matcher = Pattern.compile("([+-][0-9.]+)([+-][0-9.]+)").matcher(locMeta)
                    if (matcher.find()) {
                        val lat = matcher.group(1)?.toDoubleOrNull()
                        val lng = matcher.group(2)?.toDoubleOrNull()
                        if (lat != null && lng != null) {
                            val geocoder = Geocoder(this@FullScreenActivity, Locale.getDefault())
                            val addresses = geocoder.getFromLocation(lat, lng, 1)
                            if (!addresses.isNullOrEmpty()) {
                                locationStr = addresses[0].getAddressLine(0) ?: "$lat, $lng"
                            }
                        }
                    }
                }
                retriever.release()
            } else {
                contentResolver.query(item.uri, arrayOf(MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val w = cursor.getInt(0); val h = cursor.getInt(1)
                        if (w > 0 && h > 0) resolutionStr = "${w}x${h}  |  ${String.format("%.1f", (w * h) / 1000000.0)}MP"
                    }
                }
                
                val exif = ExifInterface(item.path)
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    val lat = latLong[0].toDouble()
                    val lng = latLong[1].toDouble()
                    val geocoder = Geocoder(this@FullScreenActivity, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    if (!addresses.isNullOrEmpty()) {
                        locationStr = addresses[0].getAddressLine(0) ?: "$lat, $lng"
                    }
                }
            }
        } catch (e: Exception) {}

        val info = arrayOf(
            "Dosya adı:", file.name, 
            "Konum:", locationStr, 
            "Dosya yolu:", readablePath, 
            "Boyut:", String.format("%.2f MB", item.size / (1024.0 * 1024.0)), 
            "Çözünürlük:", resolutionStr
        )
        
        for (i in info.indices step 2) {
            layout.addView(TextView(this).apply { text = info[i]; setTextColor(secondaryColor); textSize = 13f })
            layout.addView(TextView(this).apply { 
                text = info[i+1]
                setTextColor(primaryColor)
                textSize = 15f
                setPadding(0, 0, 0, if (i == info.size - 2) 0 else 32) 
            })
        }
        
        detailsDialog?.setContentView(layout)
        detailsDialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
        detailsDialog?.show()
    }

    private fun showCustomToast(context: Context, message: String, iconResId: Int) {
        try {
            val rootView = findViewById<ViewGroup>(android.R.id.content)
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#333333"))
                    cornerRadius = 50f
                }
                setPadding(40, 24, 40, 24)
            }
            
            if (iconResId != 0) {
                val icon = ImageView(context).apply {
                    setImageResource(iconResId)
                    setColorFilter(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(56, 56).apply { setMargins(0, 0, 24, 0) }
                }
                layout.addView(icon)
            }
            
            val text = TextView(context).apply {
                this.text = message
                setTextColor(Color.WHITE)
                textSize = 15f
            }
            layout.addView(text)
            
            val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { 
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 150 
            }
            rootView.addView(layout, params)
            
            layout.startAnimation(AlphaAnimation(0.0f, 1.0f).apply { duration = 400 })
            Handler(Looper.getMainLooper()).postDelayed({
                layout.startAnimation(AlphaAnimation(1.0f, 0.0f).apply { duration = 400 })
                Handler(Looper.getMainLooper()).postDelayed({ rootView.removeView(layout) }, 400)
            }, 2000)
        } catch (e: Exception) {}
    }
    
    inner class FilmstripAdapter(private val list: List<MediaItem>) : RecyclerView.Adapter<FilmstripAdapter.ViewHolder>() {
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) { val img: ImageView = v.findViewById(R.id.filmstripImage) }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(layoutInflater.inflate(R.layout.item_filmstrip, p, false))
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            Glide.with(this@FullScreenActivity).load(list[p].uri).centerCrop().into(h.img)
            h.img.alpha = if (p == currentPosition) 1f else 0.5f
            h.itemView.setOnClickListener { viewPager.setCurrentItem(p, true) }
        }
        override fun getItemCount() = list.size
    }

    inner class FullScreenAdapter(private val list: List<MediaItem>) : RecyclerView.Adapter<FullScreenAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val photoView: PhotoView = view.findViewById(R.id.fullImage)
            val videoContainer: RelativeLayout = view.findViewById(R.id.videoContainer)
            val videoView: VideoView = view.findViewById(R.id.fullVideo)
            val videoThumbnail: ImageView = view.findViewById(R.id.videoThumbnail)
            val controlsLayout: View = view.findViewById(R.id.videoControlsLayout)
            val videoSeekBar: SeekBar = view.findViewById(R.id.videoSeekBar)
            val btnBottomPlayPause: ImageButton = view.findViewById(R.id.btnBottomPlayPause)
            val btnPrev: ImageButton = view.findViewById(R.id.btnPrev)
            val btnNext: ImageButton = view.findViewById(R.id.btnNext)
            val tvCurrentTime: TextView = view.findViewById(R.id.tvCurrentTime)
            val tvTotalTime: TextView = view.findViewById(R.id.tvTotalTime)
            val tvSeekPreview: TextView = view.findViewById(R.id.tvSeekPreview)
            val btnMuteToggle: ImageButton = view.findViewById(R.id.btnMuteToggle)
            var mediaPlayerRef: MediaPlayer? = null
            var lastSeekTime = 0L 
            
            var scaleFactor = 1f
            lateinit var scaleGestureDetector: ScaleGestureDetector
            var isDragging = false
            var activePointerId = -1
            var lastRawX = 0f
            var lastRawY = 0f
            
            var swipeStartX = 0f
            var swipeStartY = 0f
            
            init {
                tvSeekPreview.background = GradientDrawable().apply { 
                    setColor(Color.parseColor("#B3000000"))
                    cornerRadius = 20f 
                }
            }
            
            fun clampTranslation() {
                if (scaleFactor <= 1.0f) {
                    videoView.translationX = 0f
                    videoView.translationY = 0f
                    videoThumbnail.translationX = 0f
                    videoThumbnail.translationY = 0f
                    return
                }

                val view = videoView
                val parent = videoContainer
                if (view.width == 0 || view.height == 0) return

                val maxTransX = ((view.width * scaleFactor - parent.width) / 2f).coerceAtLeast(0f)
                val maxTransY = ((view.height * scaleFactor - parent.height) / 2f).coerceAtLeast(0f)

                val clampedX = view.translationX.coerceIn(-maxTransX, maxTransX)
                val clampedY = view.translationY.coerceIn(-maxTransY, maxTransY)

                videoView.translationX = clampedX
                videoView.translationY = clampedY
                videoThumbnail.translationX = clampedX
                videoThumbnail.translationY = clampedY
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = 
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_fullscreen, parent, false))
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            if (item.isVideo) {
                holder.photoView.visibility = View.GONE
                holder.videoContainer.visibility = View.VISIBLE
                
                holder.controlsLayout.visibility = if (isUiHidden) View.GONE else View.VISIBLE
                holder.controlsLayout.alpha = if (isUiHidden) 0f else 1f

                holder.videoThumbnail.scaleType = ImageView.ScaleType.FIT_CENTER
                Glide.with(holder.itemView.context).load(item.uri).into(holder.videoThumbnail)
                
                holder.videoView.tag = null

                holder.videoView.setOnPreparedListener { mp: MediaPlayer? ->
                    holder.mediaPlayerRef = mp
                    holder.tvCurrentTime.text = "00:00"
                    holder.tvTotalTime.text = formatTime(mp?.duration ?: 0)
                    holder.videoSeekBar.max = mp?.duration ?: 0
                    
                    if (isGlobalMuted) mp?.setVolume(0f, 0f) else mp?.setVolume(1f, 1f)
                    holder.btnMuteToggle.setImageResource(if (isGlobalMuted) R.drawable.ic_modern_mute else R.drawable.ic_modern_unmute)

                    if (holder.bindingAdapterPosition == currentPosition) {
                        val autoPlay = holder.itemView.context.getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE).getBoolean("autoPlayVideos", false)
                        if (autoPlay) {
                            holder.videoView.start()
                            holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_pause)
                            startProgressUpdater(holder)
                            holder.videoView.postDelayed({
                                holder.videoThumbnail.visibility = View.GONE
                            }, 250)
                        } else {
                            holder.videoThumbnail.visibility = View.VISIBLE
                            holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
                        }
                    } else {
                        holder.videoThumbnail.visibility = View.VISIBLE
                        holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
                    }
                }
                
                holder.scaleGestureDetector = ScaleGestureDetector(holder.itemView.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                        viewPager.isUserInputEnabled = false
                        return true
                    }

                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val oldScale = holder.scaleFactor
                        holder.scaleFactor *= detector.scaleFactor
                        holder.scaleFactor = holder.scaleFactor.coerceIn(1.0f, 5.0f)

                        val scaleRatio = holder.scaleFactor / oldScale

                        val view = holder.videoView
                        val focusX = detector.focusX - (view.left + view.width / 2f)
                        val focusY = detector.focusY - (view.top + view.height / 2f)

                        val dx = focusX * (1 - scaleRatio)
                        val dy = focusY * (1 - scaleRatio)

                        holder.videoView.translationX += dx
                        holder.videoView.translationY += dy
                        holder.videoThumbnail.translationX += dx
                        holder.videoThumbnail.translationY += dy

                        holder.videoView.scaleX = holder.scaleFactor
                        holder.videoView.scaleY = holder.scaleFactor
                        holder.videoThumbnail.scaleX = holder.scaleFactor
                        holder.videoThumbnail.scaleY = holder.scaleFactor

                        holder.clampTranslation()
                        return true
                    }

                    override fun onScaleEnd(detector: ScaleGestureDetector) {
                        holder.clampTranslation()
                        if (holder.scaleFactor <= 1.0f) {
                            viewPager.isUserInputEnabled = true
                        } else {
                            viewPager.isUserInputEnabled = false
                        }
                    }
                })

                holder.videoContainer.setOnTouchListener { _, event ->
                    holder.scaleGestureDetector.onTouchEvent(event)
                    
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            holder.activePointerId = event.getPointerId(0)
                            holder.lastRawX = event.x
                            holder.lastRawY = event.y
                            holder.swipeStartX = event.rawX
                            holder.swipeStartY = event.rawY
                            holder.isDragging = false
                        }
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            val actionIndex = event.actionIndex
                            holder.activePointerId = event.getPointerId(actionIndex)
                            holder.lastRawX = event.getX(actionIndex)
                            holder.lastRawY = event.getY(actionIndex)
                        }
                        MotionEvent.ACTION_POINTER_UP -> {
                            val actionIndex = event.actionIndex
                            if (event.getPointerId(actionIndex) == holder.activePointerId) {
                                val newIndex = if (actionIndex == 0) 1 else 0
                                if (newIndex < event.pointerCount) {
                                    holder.activePointerId = event.getPointerId(newIndex)
                                    holder.lastRawX = event.getX(newIndex)
                                    holder.lastRawY = event.getY(newIndex)
                                }
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val pointerIndex = event.findPointerIndex(holder.activePointerId)
                            if (pointerIndex != -1) {
                                val x = event.getX(pointerIndex)
                                val y = event.getY(pointerIndex)
                                
                                if (event.pointerCount == 1 && holder.scaleFactor > 1.0f && !holder.scaleGestureDetector.isInProgress) {
                                    val dx = x - holder.lastRawX
                                    val dy = y - holder.lastRawY
                                    
                                    holder.videoView.translationX += dx
                                    holder.videoView.translationY += dy
                                    holder.videoThumbnail.translationX += dx
                                    holder.videoThumbnail.translationY += dy
                                    
                                    holder.clampTranslation()
                                    holder.isDragging = true
                                }
                                holder.lastRawX = x
                                holder.lastRawY = y
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            holder.clampTranslation()
                            holder.activePointerId = -1

                            if (holder.scaleFactor <= 1.0f) {
                                val diffY = event.rawY - holder.swipeStartY
                                val diffX = event.rawX - holder.swipeStartX
                                val isSwipeUp = Math.abs(diffX) < Math.abs(diffY) && diffY < -150

                                if (event.actionMasked == MotionEvent.ACTION_UP && isSwipeUp) {
                                    val act = holder.itemView.context as? FullScreenActivity
                                    act?.showModernDetailsBottomSheet()
                                } else if (event.actionMasked == MotionEvent.ACTION_UP && !holder.isDragging && event.eventTime - event.downTime < 200) {
                                    val act = holder.itemView.context as? FullScreenActivity
                                    act?.toggleUIVisibility()
                                    val hidden = act?.isUiHidden == true
                                    
                                    TransitionManager.beginDelayedTransition(holder.videoContainer as ViewGroup)
                                    holder.controlsLayout.visibility = if (hidden) View.GONE else View.VISIBLE
                                }
                            }
                            holder.isDragging = false
                        }
                    }
                    true
                }

                val togglePlayPause = {
                    try {
                        if (holder.videoView.isPlaying) {
                            holder.videoView.pause()
                            holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
                            timeHandler.removeCallbacks(updateTimeRunnable)
                        } else {
                            holder.videoView.start()
                            holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_pause)
                            startProgressUpdater(holder)
                            holder.videoView.postDelayed({
                                holder.videoThumbnail.visibility = View.GONE
                            }, 250)
                        }
                    } catch (e: Exception) {}
                }
                
                holder.btnBottomPlayPause.setOnClickListener { togglePlayPause() }

                holder.videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            holder.tvCurrentTime.text = formatTime(progress)
                            holder.tvSeekPreview.text = formatTime(progress)
                            
                            seekBar?.let { sb ->
                                val width = sb.width - sb.paddingLeft - sb.paddingRight
                                val thumbPos = sb.paddingLeft + (width * progress / sb.max.toFloat())
                                holder.tvSeekPreview.translationX = thumbPos - (holder.tvSeekPreview.width / 2f)
                            }
                            
                            val now = System.currentTimeMillis()
                            if (now - holder.lastSeekTime > 200) { 
                                holder.lastSeekTime = now
                                try { holder.videoView.seekTo(progress) } catch (e: Exception) {}
                            }
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        isUserSeeking = true 
                        viewPager.isUserInputEnabled = false 
                        holder.tvSeekPreview.visibility = View.VISIBLE 
                    }
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        isUserSeeking = false 
                        viewPager.isUserInputEnabled = true 
                        holder.tvSeekPreview.visibility = View.GONE 
                        try { holder.videoView.seekTo(seekBar?.progress ?: 0) } catch (e: Exception) {}
                        startProgressUpdater(holder)
                    }
                })

                holder.videoView.setOnCompletionListener { 
                    holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
                    holder.videoThumbnail.visibility = View.VISIBLE
                    timeHandler.removeCallbacks(updateTimeRunnable)
                }

                holder.btnPrev.setOnClickListener {
                    val currentPos = viewPager.currentItem
                    if (currentPos > 0) viewPager.setCurrentItem(currentPos - 1, true)
                }
                holder.btnNext.setOnClickListener {
                    val currentPos = viewPager.currentItem
                    if (currentPos < list.size - 1) viewPager.setCurrentItem(currentPos + 1, true)
                }
                holder.btnMuteToggle.setOnClickListener {
                    isGlobalMuted = !isGlobalMuted
                    holder.mediaPlayerRef?.setVolume(if (isGlobalMuted) 0f else 1f, if (isGlobalMuted) 0f else 1f)
                    holder.btnMuteToggle.setImageResource(if (isGlobalMuted) R.drawable.ic_modern_mute else R.drawable.ic_modern_unmute)
                }
                
                        } else {
                holder.videoContainer.visibility = View.GONE
                holder.controlsLayout.visibility = View.GONE
                holder.photoView.visibility = View.VISIBLE
                
                // TIF kontrolü eklendi
                if (isUnsupportedFormat(item.path)) {
                    Glide.with(holder.photoView.context).clear(holder.photoView)
                    holder.photoView.setImageDrawable(getPlaceholder())
                } else {
                    Glide.with(holder.photoView.context)
                        .load(item.uri)
                        .error(getPlaceholder()) // Olası hatalar için
                        .into(holder.photoView)
                }
                
                holder.photoView.setOnPhotoTapListener { _, _, _ ->
                    val act = holder.itemView.context as? FullScreenActivity
                    act?.toggleUIVisibility()
                }
                
                holder.photoView.setOnSingleFlingListener { e1, e2, velocityX, velocityY ->
                    if (e1 != null && e2 != null) {
                        val diffY = e2.rawY - e1.rawY
                        val diffX = e2.rawX - e1.rawX
                        
                        if (Math.abs(diffX) < Math.abs(diffY) && diffY < -150 && Math.abs(velocityY) > 200) {
                            val act = holder.itemView.context as? FullScreenActivity
                            act?.showModernDetailsBottomSheet()
                            return@setOnSingleFlingListener true
                        }
                    }
                    false
                }
            }
        }
        
        override fun getItemCount() = list.size

        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            try { 
                holder.videoView.post {
                    try {
                        holder.videoView.stopPlayback()
                        holder.mediaPlayerRef?.release()
                        holder.mediaPlayerRef = null
                    } catch (e: Exception) {}
                }
                holder.scaleFactor = 1f
                holder.videoView.scaleX = 1f
                holder.videoView.scaleY = 1f
                holder.videoThumbnail.scaleX = 1f
                holder.videoThumbnail.scaleY = 1f
                holder.videoView.translationX = 0f
                holder.videoView.translationY = 0f
                holder.videoThumbnail.translationX = 0f
                holder.videoThumbnail.translationY = 0f
                holder.isDragging = false
                holder.activePointerId = -1
                holder.lastRawX = 0f
                holder.lastRawY = 0f
                holder.swipeStartX = 0f
                holder.swipeStartY = 0f
            } catch (e: Exception) {}
        }
    }
}
