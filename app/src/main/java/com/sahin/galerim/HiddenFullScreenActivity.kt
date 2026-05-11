package com.sahin.galerim

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.sahin.galerim.data.AppDatabase
import com.sahin.galerim.data.HiddenMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.Uri
import android.widget.TextView

class HiddenFullScreenActivity : AppCompatActivity() {

    companion object {
        var currentHiddenList = mutableListOf<HiddenMedia>()
    }

    private lateinit var viewPager: ViewPager2
    private var currentPosition: Int = 0
    private var isAmoledTheme = false
    private lateinit var customBottomBar: LinearLayout
    var isUiHidden = false

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
        isAmoledTheme = prefs.getString("appTheme", "Sistem Teması") == "Koyu Amoled Tema"
        when (prefs.getString("appTheme", "Sistem Teması")) {
            "Açık Tema" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Koyu Tema", "Koyu Amoled Tema" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen)
        supportActionBar?.hide()

        if (currentHiddenList.isEmpty()) {
            finish()
            return
        }

        viewPager = findViewById(R.id.viewPager)
        currentPosition = intent.getIntExtra("position", 0)

        findViewById<View>(R.id.bottomUIContainer)?.visibility = View.GONE
        findViewById<View>(R.id.filmstripRecycler)?.visibility = View.GONE
        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        createCustomBottomBar()

        viewPager.adapter = HiddenPagerAdapter()
        viewPager.setCurrentItem(currentPosition, false)
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPosition = position
            }
        })
    }

    override fun onResume() {
        super.onResume()
        applyDynamicColors()
    }

    private fun createCustomBottomBar() {
        val inflater = LayoutInflater.from(this)
        customBottomBar = inflater.inflate(R.layout.layout_hidden_bottom_bar, null) as LinearLayout
        
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
        }

        customBottomBar.findViewById<View>(R.id.btnRestoreContainer).setOnClickListener { restoreCurrentItem() }
        customBottomBar.findViewById<View>(R.id.btnDeleteContainer).setOnClickListener { deleteCurrentItem() }

        ViewCompat.setOnApplyWindowInsetsListener(customBottomBar) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val dpTop = (8 * resources.displayMetrics.density).toInt()
            val dpBottom = (4 * resources.displayMetrics.density).toInt()
            v.setPadding(v.paddingLeft, dpTop, v.paddingRight, sysBars.bottom + dpBottom)
            insets
        }

        findViewById<ViewGroup>(android.R.id.content).addView(customBottomBar, params)
    }

    private fun applyDynamicColors() {
        val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val defaultBg = ContextCompat.getColor(this, R.color.p_app_background)
        val actualBg = if (isAmoledTheme) Color.BLACK else defaultBg
        
        var isDarkBg = true
        var dynamicBarColor = actualBg
        val bgType = prefs.getString("bg_type", "default")
        
        if (bgType == "color") {
            val customColor = prefs.getInt("bg_color", actualBg)
            dynamicBarColor = customColor
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
                        bgSet = true
                        val pBmp = android.graphics.Bitmap.createScaledBitmap(bitmap, 1, 1, true)
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
                val r = Color.red(actualBg)
                val g = Color.green(actualBg)
                val b = Color.blue(actualBg)
                isDarkBg = ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) < 0.6
            }
        } else {
            dynamicBarColor = actualBg
            val r = Color.red(actualBg)
            val g = Color.green(actualBg)
            val b = Color.blue(actualBg)
            isDarkBg = ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) < 0.6
        }

        val semiTransparentBg = androidx.core.graphics.ColorUtils.setAlphaComponent(dynamicBarColor, 230)
        customBottomBar.setBackgroundColor(semiTransparentBg)
        
        val adaptiveTextColor = if (isDarkBg) Color.WHITE else Color.BLACK
        
        val restoreContainer = customBottomBar.findViewById<ViewGroup>(R.id.btnRestoreContainer)
        for(i in 0 until restoreContainer.childCount) {
            val child = restoreContainer.getChildAt(i)
            if (child is ImageView) child.setColorFilter(Color.parseColor("#4CAF50"))
            if (child is TextView) child.setTextColor(Color.parseColor("#4CAF50"))
        }
        
        val deleteContainer = customBottomBar.findViewById<ViewGroup>(R.id.btnDeleteContainer)
        for(i in 0 until deleteContainer.childCount) {
            val child = deleteContainer.getChildAt(i)
            if (child is ImageView) child.setColorFilter(Color.parseColor("#FF5252"))
            if (child is TextView) child.setTextColor(Color.parseColor("#FF5252"))
        }
    }

    fun toggleUIVisibility() {
        isUiHidden = !isUiHidden
        val topBar = findViewById<View>(R.id.btnBack)?.parent as? View
        
        if (isUiHidden) {
            topBar?.visibility = View.GONE
            customBottomBar.visibility = View.GONE
        } else {
            topBar?.visibility = View.VISIBLE
            customBottomBar.visibility = View.VISIBLE
        }
        
        val rv = viewPager.getChildAt(0) as? RecyclerView
        rv?.let {
            for (i in 0 until it.childCount) {
                val holder = it.getChildViewHolder(it.getChildAt(i)) as? HiddenPagerAdapter.ViewHolder
                holder?.controlsLayout?.visibility = if (isUiHidden) View.GONE else View.VISIBLE
            }
        }
    }

    private fun showCustomToast(context: Context, message: String, iconResId: Int) {
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
        
        val toast = Toast(context)
        toast.duration = Toast.LENGTH_SHORT
        toast.view = layout
        toast.show()
    }

    private fun restoreCurrentItem() {
        if (currentHiddenList.isEmpty()) return
        val item = currentHiddenList[currentPosition]
        
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@HiddenFullScreenActivity)
            var success = false
            try {
                val source = File(item.hiddenPath)
                val dest = File(item.originalPath)
                
                val destFolder = dest.parentFile
                if (destFolder != null && !destFolder.exists()) destFolder.mkdirs()

                val targetDate = if (item.originalDate > 0) item.originalDate else source.lastModified()

                val moved = source.renameTo(dest)
                if (!moved) {
                    FileInputStream(source).use { input ->
                        FileOutputStream(dest).use { output ->
                            input.copyTo(output)
                        }
                    }
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
                    
                    MediaScannerConnection.scanFile(this@HiddenFullScreenActivity, arrayOf(dest.absolutePath), null) { _, uri ->
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
                    success = true
                }
            } catch (e: Exception) { }

            withContext(Dispatchers.Main) {
                if (success) {
                    val typeStr = if (item.isVideo) "video" else "fotoğraf"
                    showCustomToast(this@HiddenFullScreenActivity, "1 $typeStr geri yüklendi", R.drawable.ic_undo)
                    currentHiddenList.removeAt(currentPosition)
                    MainActivity.forceReload = true
                    if (currentHiddenList.isEmpty()) {
                        finish()
                    } else {
                        viewPager.adapter?.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun deleteCurrentItem() {
        if (currentHiddenList.isEmpty()) return
        val item = currentHiddenList[currentPosition]
        
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@HiddenFullScreenActivity)
            var success = false
            try {
                val file = File(item.hiddenPath)
                if (file.exists()) {
                    file.delete()
                    success = true
                }
                db.hiddenMediaDao().delete(item)
            } catch (e: Exception) {}

            withContext(Dispatchers.Main) {
                if (success) {
                    val typeStr = if (item.isVideo) "video" else "fotoğraf"
                    showCustomToast(this@HiddenFullScreenActivity, "1 $typeStr kalıcı olarak silindi", R.drawable.ic_action_delete)
                }
                currentHiddenList.removeAt(currentPosition)
                if (currentHiddenList.isEmpty()) {
                    finish()
                } else {
                    viewPager.adapter?.notifyDataSetChanged()
                }
            }
        }
    }

    inner class HiddenPagerAdapter : RecyclerView.Adapter<HiddenPagerAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val photoView: PhotoView = view.findViewById(R.id.fullImage)
            val videoContainer: RelativeLayout = view.findViewById(R.id.videoContainer)
            val videoView: VideoView = view.findViewById(R.id.fullVideo)
            val videoThumbnail: ImageView = view.findViewById(R.id.videoThumbnail)
            val controlsLayout: View = view.findViewById(R.id.videoControlsLayout)
            val btnBottomPlayPause: ImageButton = view.findViewById(R.id.btnBottomPlayPause)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_fullscreen, parent, false))
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = currentHiddenList[position]
            if (item.isVideo) {
                holder.photoView.visibility = View.GONE
                holder.videoContainer.visibility = View.VISIBLE
                Glide.with(holder.itemView.context)
                     .load(File(item.hiddenPath))
                     .error(getPlaceholder(holder.itemView.context))
                     .into(holder.videoThumbnail)
                holder.videoView.setVideoPath(item.hiddenPath)
                
                holder.controlsLayout.visibility = if (isUiHidden) View.GONE else View.VISIBLE
                
                holder.btnBottomPlayPause.setOnClickListener {
                    if (holder.videoView.isPlaying) {
                        holder.videoView.pause()
                        holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
                    } else {
                        holder.videoThumbnail.visibility = View.GONE
                        holder.videoView.start()
                        holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_pause)
                    }
                }
                
                holder.videoView.setOnCompletionListener {
                    holder.videoThumbnail.visibility = View.VISIBLE
                    holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
                }
                
                holder.videoContainer.setOnClickListener {
                    (holder.itemView.context as? HiddenFullScreenActivity)?.toggleUIVisibility()
                }
            } else {
                holder.videoContainer.visibility = View.GONE
                holder.controlsLayout.visibility = View.GONE
                holder.photoView.visibility = View.VISIBLE
                Glide.with(holder.itemView.context)
                     .load(File(item.hiddenPath))
                     .error(getPlaceholder(holder.itemView.context))
                     .into(holder.photoView)
                
                holder.photoView.setOnPhotoTapListener { _, _, _ ->
                    (holder.itemView.context as? HiddenFullScreenActivity)?.toggleUIVisibility()
                }
            }
        }
        
        override fun getItemCount() = currentHiddenList.size
        
        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            try {
                holder.videoView.stopPlayback()
            } catch (e: Exception) {}
        }
    }
}
