package com.sahin.galerim

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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

class HiddenMediaActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var selectionBar: View
    private lateinit var btnSelectAll: TextView
    private val hiddenList = mutableListOf<HiddenMedia>()
    private val selectedItems = mutableSetOf<HiddenMedia>()
    private var isSelectionMode = false
    private var isAmoledTheme = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        isAmoledTheme = prefs.getString("appTheme", "Sistem Teması") == "Koyu Amoled Tema"
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden_media)
        supportActionBar?.hide()

        recyclerView = findViewById(R.id.hiddenRecycler)
        emptyView = findViewById(R.id.emptyView)
        selectionBar = findViewById(R.id.selectionBar)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        val hiddenRoot = findViewById<LinearLayout>(R.id.hiddenRoot)

        if (isAmoledTheme) {
            hiddenRoot.setBackgroundColor(Color.BLACK)
            selectionBar.setBackgroundColor(Color.BLACK)
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        
        recyclerView.layoutManager = GridLayoutManager(this, 4)
        recyclerView.adapter = HiddenAdapter()

        findViewById<View>(R.id.btnRestore).setOnClickListener { restoreSelectedItems() }
        findViewById<View>(R.id.btnDelete).setOnClickListener { deleteSelectedItemsPermanently() }

        btnSelectAll.setOnClickListener {
            if (selectedItems.size == hiddenList.size) {
                selectedItems.clear()
                exitSelectionMode()
            } else {
                selectedItems.clear()
                selectedItems.addAll(hiddenList)
                updateSelectAllText()
                recyclerView.adapter?.notifyDataSetChanged()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadHiddenMedia()
    }

    private fun loadHiddenMedia() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@HiddenMediaActivity)
            val list = db.hiddenMediaDao().getAllHiddenMedia()
            withContext(Dispatchers.Main) {
                hiddenList.clear()
                hiddenList.addAll(list)
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
            val icon = ImageView(context).apply {
                setImageResource(iconResId)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(56, 56).apply { setMargins(0, 0, 24, 0) }
            }
            val text = TextView(context).apply {
                this.text = message
                setTextColor(Color.WHITE)
                textSize = 15f
            }
            layout.addView(icon)
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
                    
                    // Hayalet dosya kontrolü: Dosya fiziksel olarak yoksa veritabanından temizle ve atla
                    if (!source.exists()) {
                        db.hiddenMediaDao().delete(item)
                        continue
                    }
                    
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
                    photoCount > 0 && videoCount > 0 -> "$photoCount fotoğraf, $videoCount video geri yüklendi"
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

    private fun deleteSelectedItemsPermanently() {
        if (selectedItems.isEmpty()) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@HiddenMediaActivity)
            var count = 0
            for (item in selectedItems) {
                try {
                    val file = File(item.hiddenPath)
                    if (file.exists()) file.delete()
                    db.hiddenMediaDao().delete(item)
                    count++
                } catch (e: Exception) {}
            }
            withContext(Dispatchers.Main) {
                if (count > 0) {
                    showCustomToast(this@HiddenMediaActivity, "$count öğe tamamen silindi", R.drawable.ic_delete_outline)
                }
                exitSelectionMode()
                loadHiddenMedia()
            }
        }
    }

    private fun updateSelectAllText() {
        if (selectedItems.size == hiddenList.size && hiddenList.isNotEmpty()) {
            btnSelectAll.text = "Hiçbirini Seçme"
        } else {
            btnSelectAll.text = "Hepsini Seç"
        }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        selectionBar.visibility = View.GONE
        btnSelectAll.visibility = View.GONE
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

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = hiddenList[position]
            Glide.with(this@HiddenMediaActivity).load(File(item.hiddenPath)).centerCrop().into(holder.img)
            
            holder.videoIcon.visibility = if (item.isVideo) View.VISIBLE else View.GONE
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
                        updateSelectAllText()
                        notifyItemChanged(position)
                    }
                } else {
                    HiddenFullScreenActivity.currentHiddenList = hiddenList
                    val intent = Intent(this@HiddenMediaActivity, HiddenFullScreenActivity::class.java)
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
                    updateSelectAllText()
                    notifyDataSetChanged()
                }
                true
            }
        }

        override fun getItemCount() = hiddenList.size
    }
}
