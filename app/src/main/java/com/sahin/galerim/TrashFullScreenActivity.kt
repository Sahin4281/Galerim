package com.sahin.galerim

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class TrashFullScreenActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private var currentPosition: Int = 0
    private var isAmoledTheme = false
    private var isUiHidden = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        isAmoledTheme = prefs.getString("appTheme", "Sistem Teması") == "Koyu Amoled Tema"
        when (prefs.getString("appTheme", "Sistem Teması")) {
            "Açık Tema" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Koyu Tema", "Koyu Amoled Tema" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_trash_full_screen)
        supportActionBar?.hide()

        if (MainActivity.trashList.isEmpty()) {
            finish()
            return
        }

        viewPager = findViewById(R.id.viewPager)
        currentPosition = intent.getIntExtra("position", 0)

        val btnRestore = findViewById<View>(R.id.btnRestore)
        val btnPermanentDelete = findViewById<View>(R.id.btnPermanentDelete)
        
        btnRestore?.setOnClickListener { restoreCurrentItem() }
        btnPermanentDelete?.setOnClickListener { showDeleteConfirmationDialog() }

        viewPager.adapter = TrashPagerAdapter()
        viewPager.setCurrentItem(currentPosition, false)
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPosition = position
            }
        })
    }

    fun toggleUIVisibility() {
        isUiHidden = !isUiHidden
        val bottomBar = findViewById<View>(R.id.btnRestore)?.parent as? View
        bottomBar?.visibility = if (isUiHidden) View.GONE else View.VISIBLE
        
        val rv = viewPager.getChildAt(0) as? RecyclerView
        rv?.let {
            for (i in 0 until it.childCount) {
                val holder = it.getChildViewHolder(it.getChildAt(i)) as? TrashPagerAdapter.ViewHolder
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
        
        val toast = Toast(context)
        toast.duration = Toast.LENGTH_SHORT
        toast.view = layout
        toast.show()
    }

    private fun showDeleteConfirmationDialog() {
        if (MainActivity.trashList.isEmpty()) return
        val item = MainActivity.trashList[currentPosition]
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

        val itemType = if (item.isVideo) "video" else "fotoğraf"
        messageView.text = "1 $itemType kalıcı olarak silinsin mi?"
        
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
                    deleteCurrentItem()
                }
            }
            parentLayout.addView(btnCancelNew)
            parentLayout.addView(btnConfirmNew)
        }
        dialog.show()
    }

    private fun restoreCurrentItem() {
        if (MainActivity.trashList.isEmpty()) return
        val item = MainActivity.trashList[currentPosition]
        
        var isRestored = false

        try {
            if (item.path.contains(".galerim_trash")) {
                val trashFile = File(item.path)
                val origPath = MainActivity.trashedOriginalPaths[item.path]

                if (trashFile.exists() && origPath != null) {
                    val destFile = File(origPath)
                    destFile.parentFile?.mkdirs()

                    FileInputStream(trashFile).use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    }

                    if (destFile.exists()) {
                        destFile.setLastModified(trashFile.lastModified())
                        MediaScannerConnection.scanFile(this, arrayOf(destFile.absolutePath), null, null)

                        trashFile.delete()
                        MainActivity.trashedPaths.remove(item.path)
                        MainActivity.trashedOriginalPaths.remove(item.path)
                        MainActivity.trashedTimestamps.remove(item.path)
                        MainActivity.trashedIsVideo.remove(item.path)
                        MainActivity.trashedDurations.remove(item.path)
                        MainActivity.trashedSizes.remove(item.path)
                        isRestored = true
                    }
                }
            } else {
                MainActivity.trashedPaths.remove(item.path)
                MainActivity.trashedTimestamps.remove(item.path)
                isRestored = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (isRestored) {
            MainActivity.saveTrashedPaths(this)
            MainActivity.trashList.removeAt(currentPosition)
            MainActivity.forceReload = true

            val msg = if (item.isVideo) "1 video geri yüklendi" else "1 fotoğraf geri yüklendi"
            showCustomToast(this, msg, R.drawable.ic_undo)

            if (MainActivity.trashList.isEmpty()) {
                finish()
            } else {
                viewPager.adapter?.notifyItemRemoved(currentPosition)
                if (currentPosition >= MainActivity.trashList.size) {
                    currentPosition = MainActivity.trashList.size - 1
                }
                viewPager.adapter?.notifyItemRangeChanged(currentPosition, MainActivity.trashList.size)
            }
        } else {
            Toast.makeText(this, "Geri yükleme işlemi başarısız oldu.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteCurrentItem() {
        if (MainActivity.trashList.isEmpty()) return
        val item = MainActivity.trashList[currentPosition]
        
        var isDeleted = false

        try {
            val file = File(item.path)
            if (file.exists() && file.delete()) {
                if (item.uri.scheme != "file") {
                    contentResolver.delete(item.uri, null, null)
                }
                isDeleted = true
            } else {
                if (item.uri.scheme != "file") {
                    val rows = contentResolver.delete(item.uri, null, null)
                    if (rows > 0) isDeleted = true
                }
            }

            if (isDeleted) {
                MainActivity.trashedPaths.remove(item.path)
                MainActivity.trashedOriginalPaths.remove(item.path)
                MainActivity.trashedTimestamps.remove(item.path)
                MainActivity.trashedIsVideo.remove(item.path)
                MainActivity.trashedDurations.remove(item.path)
                MainActivity.trashedSizes.remove(item.path)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (isDeleted) {
            MainActivity.saveTrashedPaths(this)
            MainActivity.trashList.removeAt(currentPosition)
            MainActivity.forceReload = true

            val msg = if (item.isVideo) "1 video kalıcı olarak silindi" else "1 fotoğraf kalıcı olarak silindi"
            showCustomToast(this, msg, R.drawable.ic_action_delete)

            if (MainActivity.trashList.isEmpty()) {
                finish()
            } else {
                viewPager.adapter?.notifyItemRemoved(currentPosition)
                if (currentPosition >= MainActivity.trashList.size) {
                    currentPosition = MainActivity.trashList.size - 1
                }
                viewPager.adapter?.notifyItemRangeChanged(currentPosition, MainActivity.trashList.size)
            }
        } else {
            Toast.makeText(this, "Silme işlemi başarısız oldu.", Toast.LENGTH_SHORT).show()
        }
    }

    inner class TrashPagerAdapter : RecyclerView.Adapter<TrashPagerAdapter.ViewHolder>() {
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
            val item = MainActivity.trashList[position]
            if (item.isVideo) {
                holder.photoView.visibility = View.GONE
                holder.videoContainer.visibility = View.VISIBLE
                Glide.with(holder.itemView.context).load(item.uri).into(holder.videoThumbnail)
                holder.videoView.setVideoURI(item.uri)
                
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
                
                holder.videoContainer.setOnClickListener { toggleUIVisibility() }
            } else {
                holder.videoContainer.visibility = View.GONE
                holder.controlsLayout.visibility = View.GONE
                holder.photoView.visibility = View.VISIBLE
                Glide.with(holder.itemView.context).load(item.uri).into(holder.photoView)
                holder.photoView.setOnPhotoTapListener { _, _, _ -> toggleUIVisibility() }
            }
        }
        override fun getItemCount() = MainActivity.trashList.size
        
        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            try {
                holder.videoView.stopPlayback()
            } catch (e: Exception) {}
        }
    }
}
