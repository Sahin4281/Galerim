package com.sahin.galerim

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
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

class HiddenFullScreenActivity : AppCompatActivity() {

    companion object {
        var currentHiddenList = mutableListOf<HiddenMedia>()
    }

    private lateinit var viewPager: ViewPager2
    private var currentPosition: Int = 0
    private var isAmoledTheme = false
    private lateinit var customBottomBar: LinearLayout
    var isUiHidden = false

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

    private fun createCustomBottomBar() {
        val inflater = LayoutInflater.from(this)
        customBottomBar = inflater.inflate(R.layout.layout_hidden_bottom_bar, null) as LinearLayout
        
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
        }
        
        if (isAmoledTheme) {
            customBottomBar.setBackgroundColor(Color.BLACK)
        } else {
            customBottomBar.setBackgroundColor(ContextCompat.getColor(this, R.color.p_app_background))
        }

        customBottomBar.findViewById<View>(R.id.btnRestoreContainer).setOnClickListener { restoreCurrentItem() }
        customBottomBar.findViewById<View>(R.id.btnDeleteContainer).setOnClickListener { deleteCurrentItem() }

        ViewCompat.setOnApplyWindowInsetsListener(customBottomBar) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, (20 * resources.displayMetrics.density).toInt() + sysBars.bottom)
            insets
        }

        findViewById<ViewGroup>(android.R.id.content).addView(customBottomBar, params)
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

                FileInputStream(source).use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
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

                    source.delete()
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
                    Toast.makeText(this@HiddenFullScreenActivity, "Öğe galeriye geri yüklendi", Toast.LENGTH_SHORT).show()
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
            try {
                val file = File(item.hiddenPath)
                if (file.exists()) file.delete()
                db.hiddenMediaDao().delete(item)
            } catch (e: Exception) {}

            withContext(Dispatchers.Main) {
                Toast.makeText(this@HiddenFullScreenActivity, "Kalıcı olarak silindi", Toast.LENGTH_SHORT).show()
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
                Glide.with(holder.itemView.context).load(File(item.hiddenPath)).into(holder.videoThumbnail)
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
                Glide.with(holder.itemView.context).load(File(item.hiddenPath)).into(holder.photoView)
                
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
