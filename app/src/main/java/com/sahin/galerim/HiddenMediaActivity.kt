package com.sahin.galerim

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.appcompat.widget.AppCompatButton
import com.sahin.galerim.data.AppDatabase
import com.sahin.galerim.data.HiddenMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HiddenMediaActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var selectionBar: View
    private lateinit var btnSelectAll: TextView
    private lateinit var tvHiddenTitle: TextView
    private lateinit var tvHiddenTitleCount: TextView
    private val hiddenList = mutableListOf<HiddenMedia>()
    private val selectedItems = mutableSetOf<HiddenMedia>()
    private var isSelectionMode = false

    fun getPlaceholder(context: Context): android.graphics.drawable.Drawable {
        val bgColor = android.graphics.Color.parseColor("#F2F2F2") 
        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(bgColor)
        }
        val icon = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_menu_gallery)?.mutate()
        if (icon != null) {
            icon.setTint(android.graphics.Color.parseColor("#BDBDBD")) 
            val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(bgDrawable, icon))
            val density = context.resources.displayMetrics.density
            val inset = (32 * density).toInt() 
            layerDrawable.setLayerInset(1, inset, inset, inset, inset)
            return layerDrawable
        }
        return bgDrawable
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val currentTheme = prefs.getString("appTheme", "Sistem Teması")
        
        when (currentTheme) {
            "Açık Tema" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Koyu Tema", "Koyu Amoled Tema" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden_media)
        supportActionBar?.hide()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        recyclerView = findViewById(R.id.hiddenRecycler)
        emptyView = findViewById(R.id.emptyView)
        selectionBar = findViewById(R.id.selectionBar)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        tvHiddenTitle = findViewById(R.id.tvHiddenTitle)
        tvHiddenTitleCount = findViewById(R.id.tvHiddenTitleCount)

        ViewCompat.setOnApplyWindowInsetsListener(selectionBar) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val dpTop = (8 * resources.displayMetrics.density).toInt()
            val dpBottom = (8 * resources.displayMetrics.density).toInt()
            v.setPadding(v.paddingLeft, dpTop, v.paddingRight, sysBars.bottom + dpBottom)
            insets
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        
        val spanCount = prefs.getInt("gridSpanCount", 4)
        recyclerView.layoutManager = GridLayoutManager(this, spanCount)
        val density = resources.displayMetrics.density
        val padding = (8 * density).toInt()
        recyclerView.setPadding(padding, 0, padding, 0)
        recyclerView.clipToPadding = false

        recyclerView.adapter = HiddenAdapter()

        findViewById<View>(R.id.btnRestore).setOnClickListener { restoreSelectedItems() }
        findViewById<View>(R.id.btnDelete).setOnClickListener { showDeleteConfirmationDialog() }

        btnSelectAll.setOnClickListener {
            if (selectedItems.size == hiddenList.size) {
                selectedItems.clear()
                exitSelectionMode()
            } else {
                selectedItems.clear()
                selectedItems.addAll(hiddenList)
                updateSelectionUI()
                recyclerView.adapter?.notifyDataSetChanged()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val spanCount = prefs.getInt("gridSpanCount", 4)
        (recyclerView.layoutManager as? GridLayoutManager)?.spanCount = spanCount
        
        applyDynamicColors()
        loadHiddenMedia()
    }

    private fun applyDynamicColors() {
        val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val isAmoledTheme = prefs.getString("appTheme", "Sistem Teması") == "Koyu Amoled Tema"
        val defaultBg = ContextCompat.getColor(this, R.color.p_app_background)
        val actualBg = if (isAmoledTheme) Color.BLACK else defaultBg

        val hiddenRoot = findViewById<LinearLayout>(R.id.hiddenRoot)
        val selectionBarView = findViewById<LinearLayout>(R.id.selectionBar)
        val bgType = prefs.getString("bg_type", "default")
        
        var isDarkBg = true
        var dynamicBarColor = actualBg
        
        if (bgType == "color") {
            val customColor = prefs.getInt("bg_color", actualBg)
            dynamicBarColor = customColor
            hiddenRoot?.setBackgroundColor(customColor)
            val r = Color.red(customColor)
            val g = Color.green(customColor)
            val b = Color.blue(customColor)
            isDarkBg = ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) < 0.6
        } else if (bgType == "image") {
            dynamicBarColor = Color.TRANSPARENT
            val imageUriStr = prefs.getString("bg_image", null)
            var bgSet = false
            if (imageUriStr != null) {
                try {
                    val uri = Uri.parse(imageUriStr)
                    val optionsBounds = android.graphics.BitmapFactory.Options()
                    optionsBounds.inJustDecodeBounds = true
                    contentResolver.openInputStream(uri)?.use { 
                        android.graphics.BitmapFactory.decodeStream(it, null, optionsBounds)
                    }
                    var scale = 1
                    val screenWidth = resources.displayMetrics.widthPixels
                    val screenHeight = resources.displayMetrics.heightPixels
                    val maxDim = Math.max(optionsBounds.outWidth, optionsBounds.outHeight)
                    val reqDim = Math.max(screenWidth, screenHeight)
                    while (maxDim / scale / 2 >= reqDim) { scale *= 2 }
                    val optionsDecode = android.graphics.BitmapFactory.Options()
                    optionsDecode.inSampleSize = scale
                    val bitmap = contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it, null, optionsDecode)
                    }
                    if (bitmap != null) {
                        var rotationDegrees = 0f
                        try {
                            contentResolver.openInputStream(uri)?.use { inputStream ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    val exif = android.media.ExifInterface(inputStream)
                                    val orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                                    rotationDegrees = when (orientation) {
                                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                                        else -> 0f
                                    }
                                }
                            }
                        } catch (e: Exception) {}
                        var rotatedBitmap = bitmap
                        if (rotationDegrees != 0f) {
                            val matrix = android.graphics.Matrix()
                            matrix.postRotate(rotationDegrees)
                            rotatedBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        }
                        val bgScale = Math.max(screenWidth.toFloat() / rotatedBitmap.width, screenHeight.toFloat() / rotatedBitmap.height)
                        val scaledWidth = Math.round(bgScale * rotatedBitmap.width)
                        val scaledHeight = Math.round(bgScale * rotatedBitmap.height)
                        val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(rotatedBitmap, scaledWidth, scaledHeight, true)
                        val x = Math.max(0, (scaledBitmap.width - screenWidth) / 2)
                        val y = Math.max(0, (scaledBitmap.height - screenHeight) / 2)
                        val finalBitmap = android.graphics.Bitmap.createBitmap(scaledBitmap, x, y, Math.min(screenWidth, scaledBitmap.width), Math.min(screenHeight, scaledBitmap.height))
                        val drawable = android.graphics.drawable.BitmapDrawable(resources, finalBitmap)
                        hiddenRoot?.background = drawable
                        bgSet = true
                        val pBmp = android.graphics.Bitmap.createScaledBitmap(finalBitmap, 1, 1, true)
                        val avgColor = pBmp.getPixel(0, 0)
                        val r = Color.red(avgColor)
                        val g = Color.green(avgColor)
                        val b = Color.blue(avgColor)
                        isDarkBg = ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) < 0.6
                    }
                } catch (e: Throwable) { }
            }
            if (!bgSet) {
                dynamicBarColor = actualBg
                hiddenRoot?.setBackgroundColor(actualBg)
                val r = Color.red(actualBg)
                val g = Color.green(actualBg)
                val b = Color.blue(actualBg)
                isDarkBg = ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) < 0.6
            }
        } else {
            dynamicBarColor = actualBg
            hiddenRoot?.setBackgroundColor(actualBg)
            val r = Color.red(actualBg)
            val g = Color.green(actualBg)
            val b = Color.blue(actualBg)
            isDarkBg = ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) < 0.6
        }

        selectionBarView?.setBackgroundColor(dynamicBarColor)

        val adaptiveTextColor = if (isDarkBg) Color.WHITE else Color.BLACK
        val adaptiveSecondaryColor = Color.argb(178, Color.red(adaptiveTextColor), Color.green(adaptiveTextColor), Color.blue(adaptiveTextColor))

        tvHiddenTitle.setTextColor(adaptiveTextColor)
        tvHiddenTitleCount.setTextColor(adaptiveSecondaryColor)
        findViewById<ImageView>(R.id.btnBack)?.setColorFilter(adaptiveTextColor)
        btnSelectAll.setTextColor(adaptiveTextColor)

        val emptyView = findViewById<LinearLayout>(R.id.emptyView)
        emptyView?.let { layout ->
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i)
                if (child is TextView) {
                    child.setTextColor(adaptiveTextColor)
                }
            }
        }

        findViewById<TextView>(R.id.tvRestoreText)?.setTextColor(Color.parseColor("#4CAF50"))
        findViewById<ImageView>(R.id.ivRestoreIcon)?.setColorFilter(Color.parseColor("#4CAF50"))
        findViewById<TextView>(R.id.tvDeleteText)?.setTextColor(Color.parseColor("#FF5252"))
        findViewById<ImageView>(R.id.ivDeleteIcon)?.setColorFilter(Color.parseColor("#FF5252"))
    }

    private fun loadHiddenMedia() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@HiddenMediaActivity)
            val list = db.hiddenMediaDao().getAllHiddenMedia()
            withContext(Dispatchers.Main) {
                hiddenList.clear()
                hiddenList.addAll(list)
                
                var photoCount = 0
                var videoCount = 0
                list.forEach { if (it.isVideo) videoCount++ else photoCount++ }
                val countText = when {
                    photoCount > 0 && videoCount > 0 -> "$photoCount fotoğraf $videoCount video"
                    videoCount > 0 -> "$videoCount video"
                    photoCount > 0 -> "$photoCount fotoğraf"
                    else -> ""
                }
                tvHiddenTitleCount.text = countText
                
                recyclerView.adapter?.notifyDataSetChanged()
                emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
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
            
            rootView.addView(layout, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = 150 })
            layout.startAnimation(AlphaAnimation(0.0f, 1.0f).apply { duration = 400 })
            Handler(Looper.getMainLooper()).postDelayed({
                layout.startAnimation(AlphaAnimation(1.0f, 0.0f).apply { duration = 400 })
                Handler(Looper.getMainLooper()).postDelayed({ rootView.removeView(layout) }, 400)
            }, 2000)
        } catch (e: Exception) {}
    }

    private fun restoreSelectedItems() {
        if (selectedItems.isEmpty()) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@HiddenMediaActivity)
            var photoCount = 0
            var videoCount = 0
            
            for (item in selectedItems) {
                try {
                    val source = File(item.hiddenPath)
                    
                    if (!source.exists()) {
                        db.hiddenMediaDao().delete(item)
                        continue
                    }
                    
                    val dest = File(item.originalPath)
                    
                    val destFolder = dest.parentFile
                    if (destFolder != null && !destFolder.exists()) destFolder.mkdirs()

                    val targetDate = if (item.originalDate > 0) item.originalDate else source.lastModified()

                    val moved = source.renameTo(dest)
                    if (!moved) {
                        source.copyTo(dest, overwrite = true)
                        source.delete()
                    }

                    if (dest.exists()) {
                        try {
                            val exif = ExifInterface(dest.absolutePath)
                            val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                            val dateString = sdf.format(Date(targetDate))
                            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateString)
                            exif.setAttribute(ExifInterface.TAG_DATETIME, dateString)
                            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateString)
                            exif.saveAttributes()
                        } catch (e: Exception) { }

                        dest.setLastModified(targetDate)

                        db.hiddenMediaDao().delete(item)
                        
                        MediaScannerConnection.scanFile(this@HiddenMediaActivity, arrayOf(dest.absolutePath), null) { _, uri ->
                            if (uri != null) {
                                try {
                                    val values = ContentValues().apply {
                                        put(MediaStore.MediaColumns.DATE_ADDED, targetDate / 1000)
                                        put(MediaStore.MediaColumns.DATE_MODIFIED, targetDate / 1000)
                                    }
                                    contentResolver.update(uri, values, null, null)
                                } catch (e: Exception) { }
                            }
                        }
                        if (item.isVideo) videoCount++ else photoCount++
                    }
                } catch (e: Exception) { }
            }

            withContext(Dispatchers.Main) {
                val msg = when {
                    photoCount > 0 && videoCount > 0 -> "$photoCount fotoğraf ve $videoCount video geri yüklendi"
                    photoCount > 0 -> "$photoCount fotoğraf geri yüklendi"
                    videoCount > 0 -> "$videoCount video geri yüklendi"
                    else -> return@withContext
                }
                showCustomToast(this@HiddenMediaActivity, msg, R.drawable.ic_undo)
                
                MainActivity.forceReload = true
                exitSelectionMode()
                loadHiddenMedia()
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        if (selectedItems.isEmpty()) return
        val dialogBgColor = ContextCompat.getColor(this, R.color.p_app_dialog_bg)
        val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
        val isAmoledTheme = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE).getString("appTheme", "Sistem Teması") == "Koyu Amoled Tema"
        
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

        var photoCount = 0
        var videoCount = 0
        selectedItems.forEach { if (it.isVideo) videoCount++ else photoCount++ }
        
        val itemsText = when {
            photoCount > 0 && videoCount > 0 -> "$photoCount fotoğraf ve $videoCount video"
            photoCount > 0 -> "$photoCount fotoğraf"
            videoCount > 0 -> "$videoCount video"
            else -> ""
        }
        messageView.text = "$itemsText kalıcı olarak silinsin mi?"
        
        val btnConfirm = view.findViewById<AppCompatButton>(R.id.btnConfirm)
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
                layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply { marginEnd = dp10 }
                setOnClickListener { dialog.dismiss() }
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
                layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply { marginStart = dp10 }
                setOnClickListener {
                    dialog.dismiss()
                    deleteSelectedItemsPermanently()
                }
            }
            parentLayout.addView(btnCancelNew)
            parentLayout.addView(btnConfirmNew)
        }
        dialog.show()
    }

    private fun deleteSelectedItemsPermanently() {
        if (selectedItems.isEmpty()) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@HiddenMediaActivity)
            var photoCount = 0
            var videoCount = 0
            for (item in selectedItems) {
                try {
                    val file = File(item.hiddenPath)
                    if (file.exists()) file.delete()
                    db.hiddenMediaDao().delete(item)
                    if (item.isVideo) videoCount++ else photoCount++
                } catch (e: Exception) {}
            }
            withContext(Dispatchers.Main) {
                val msg = when {
                    photoCount > 0 && videoCount > 0 -> "$photoCount fotoğraf ve $videoCount video kalıcı olarak silindi"
                    photoCount > 0 -> "$photoCount fotoğraf kalıcı olarak silindi"
                    videoCount > 0 -> "$videoCount video kalıcı olarak silindi"
                    else -> return@withContext
                }
                showCustomToast(this@HiddenMediaActivity, msg, R.drawable.ic_action_delete)
                exitSelectionMode()
                loadHiddenMedia()
            }
        }
    }

    private fun updateSelectionUI() {
        if (isSelectionMode) {
            var photoCount = 0
            var videoCount = 0
            selectedItems.forEach { if (it.isVideo) videoCount++ else photoCount++ }
            
            val selectionText = when {
                photoCount > 0 && videoCount > 0 -> "$photoCount fotoğraf, $videoCount video seçili"
                photoCount > 0 -> "$photoCount fotoğraf seçili"
                videoCount > 0 -> "$videoCount video seçili"
                else -> "0 seçili"
            }
            tvHiddenTitle.text = selectionText
            tvHiddenTitle.textSize = 14f
            tvHiddenTitleCount.visibility = View.GONE
            
            if (selectedItems.size == hiddenList.size && hiddenList.isNotEmpty()) {
                btnSelectAll.text = "Hiçbirini Seçme"
            } else {
                btnSelectAll.text = "Hepsini Seç"
            }
        } else {
            tvHiddenTitle.text = "Gizli Klasör"
            tvHiddenTitle.textSize = 20f
            tvHiddenTitleCount.visibility = View.VISIBLE
        }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        selectionBar.visibility = View.GONE
        btnSelectAll.visibility = View.GONE
        updateSelectionUI()
        recyclerView.adapter?.notifyDataSetChanged()
    }

    inner class HiddenAdapter : RecyclerView.Adapter<HiddenAdapter.ViewHolder>() {
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.mediaThumbnail)
            val check: ImageView = v.findViewById(R.id.selectionCheck)
            val overlay: View = v.findViewById(R.id.selectionOverlay)
            val videoIcon: View = v.findViewById(R.id.videoInfoPanel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(layoutInflater.inflate(R.layout.item_media, parent, false))
        }
        
        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            val textureView = holder.itemView.findViewById<TextureView>(R.id.mediaTextureView)
            textureView?.visibility = View.GONE
            holder.img.visibility = View.VISIBLE
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = hiddenList[position]
            
            Glide.with(this@HiddenMediaActivity)
                .load(File(item.hiddenPath))
                .placeholder(getPlaceholder(this@HiddenMediaActivity))
                .error(getPlaceholder(this@HiddenMediaActivity))
                .centerCrop()
                .into(holder.img)
            
            val textureView = holder.itemView.findViewById<TextureView>(R.id.mediaTextureView)
            textureView.visibility = View.GONE
            textureView.surfaceTextureListener = null
            holder.img.visibility = View.VISIBLE

            if (item.isVideo) {
                holder.videoIcon.visibility = View.VISIBLE
                val tvDuration = holder.itemView.findViewById<TextView>(R.id.videoDuration)
                tvDuration.text = ""
                tvDuration.tag = item.hiddenPath
                
                (holder.itemView.context as? AppCompatActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
                    val retriever = android.media.MediaMetadataRetriever()
                    var durationText = ""
                    try {
                        retriever.setDataSource(item.hiddenPath)
                        val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val timeInMillis = time?.toLongOrNull() ?: 0L
                        val seconds = (timeInMillis / 1000) % 60
                        val minutes = (timeInMillis / (1000 * 60)) % 60
                        val hours = (timeInMillis / (1000 * 60 * 60))
                        durationText = if (hours > 0) {
                            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
                        } else {
                            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                        }
                    } catch (e: Exception) {
                    } finally {
                        try { retriever.release() } catch(e: Exception){}
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (tvDuration.tag == item.hiddenPath) {
                            tvDuration.text = durationText
                        }
                    }
                }
            } else {
                holder.videoIcon.visibility = View.GONE
            }
            
            holder.check.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            holder.overlay.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            
            if (selectedItems.contains(item)) {
                holder.overlay.setBackgroundColor(Color.parseColor("#88000000"))
                holder.check.setImageResource(R.drawable.ic_check_circle_on)
            } else {
                holder.overlay.setBackgroundColor(Color.TRANSPARENT)
                holder.check.setImageResource(R.drawable.ic_check_circle_off)
            }

            holder.itemView.setOnClickListener {
                if (isSelectionMode) {
                    if (selectedItems.contains(item)) selectedItems.remove(item) else selectedItems.add(item)
                    if (selectedItems.isEmpty()) {
                        exitSelectionMode()
                    } else {
                        updateSelectionUI()
                        notifyItemChanged(position)
                    }
                } else {
                    HiddenFullScreenActivity.currentHiddenList.clear()
                    HiddenFullScreenActivity.currentHiddenList.addAll(hiddenList)
                    val intent = Intent(this@HiddenMediaActivity, Class.forName("com.sahin.galerim.HiddenFullScreenActivity"))
                    intent.putExtra("position", position)
                    startActivity(intent)
                }
            }

            holder.itemView.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                    
                    selectedItems.add(item)
                    selectionBar.visibility = View.VISIBLE
                    btnSelectAll.visibility = View.VISIBLE
                    updateSelectionUI()
                    notifyDataSetChanged()
                }
                true
            }
        }

        override fun getItemCount() = hiddenList.size
    }
}
