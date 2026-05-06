package com.sahin.galerim

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
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
import android.widget.NumberPicker
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sahin.galerim.data.AppDatabase
import com.sahin.galerim.data.HiddenMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

fun formatFsTime(ms: Int): String {
    val s = (ms / 1000) % 60; val m = (ms / (1000 * 60)) % 60; val h = ms / (1000 * 60 * 60)
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

fun isFsUnsupportedFormat(path: String): Boolean {
    val p = path.lowercase(Locale.getDefault())
    return p.endsWith(".tif") || p.endsWith(".tiff")
}

fun ViewPager2.reduceFsDragSensitivity() {
    try {
        val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recyclerViewField.isAccessible = true
        val recyclerView = recyclerViewField.get(this) as RecyclerView
        val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int
        touchSlopField.set(recyclerView, touchSlop * 2)
    } catch (e: Exception) {}
}

fun Context.showFsCustomToast(message: String, iconResId: Int) {
    val activity = this as? androidx.appcompat.app.AppCompatActivity ?: return
    try {
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = 50f
            }
            setPadding(40, 24, 40, 24)
        }
        
        if (iconResId != 0) {
            val icon = ImageView(activity).apply {
                setImageResource(iconResId)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(56, 56).apply { setMargins(0, 0, 24, 0) }
            }
            layout.addView(icon)
        }
        
        val text = TextView(activity).apply {
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

fun FullScreenActivity.handleEdit() {
    if (currentPosition >= MainActivity.displayedMediaList.size) return
    val item = MainActivity.displayedMediaList[currentPosition]
    if (!item.isVideo) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_EDIT).apply { setDataAndType(item.uri, "image/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Fotoğrafı düzenle"))
    } else { showFsCustomToast("Videolar şimdilik düzenlenemez.", android.R.drawable.ic_menu_info_details) }
}

fun FullScreenActivity.handleShare() {
    if (currentPosition >= MainActivity.displayedMediaList.size) return
    val item = MainActivity.displayedMediaList[currentPosition]
    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = if (item.isVideo) "video/*" else "image/*"; putExtra(Intent.EXTRA_STREAM, item.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Paylaş"))
}

fun FullScreenActivity.showTrashDialog() {
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

fun FullScreenActivity.moveToAppTrash() {
    if (currentPosition >= MainActivity.displayedMediaList.size) return
    val item = MainActivity.displayedMediaList[currentPosition]
    
    lifecycleScope.launch(Dispatchers.IO) {
        var success = false
        try {
            val trashFolder = File(filesDir, ".galerim_trash")
            if (!trashFolder.exists()) {
                trashFolder.mkdirs()
            }

            val sourceFile = File(item.path)
            if (sourceFile.exists()) {
                val destFile = File(trashFolder, "${System.currentTimeMillis()}_${sourceFile.name}")
                
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destFile).use { output -> 
                        input.copyTo(output) 
                    }
                }
                
                if (destFile.exists()) {
                    destFile.setLastModified(sourceFile.lastModified())
                    
                    MainActivity.trashedPaths.add(destFile.absolutePath)
                    MainActivity.trashedOriginalPaths[destFile.absolutePath] = sourceFile.absolutePath
                    MainActivity.trashedTimestamps[destFile.absolutePath] = System.currentTimeMillis()
                    MainActivity.trashedIsVideo[destFile.absolutePath] = item.isVideo
                    MainActivity.trashedDurations[destFile.absolutePath] = item.duration
                    MainActivity.trashedSizes[destFile.absolutePath] = item.size
                    
                    sourceFile.delete()
                    contentResolver.delete(item.uri, null, null)
                    success = true
                }
            }
        } catch (e: Exception) {
            // Hata yoksayılır
        }

        withContext(Dispatchers.Main) {
            if (success) {
                MainActivity.saveTrashedPaths(this@moveToAppTrash)
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
                showFsCustomToast(msg, R.drawable.ic_action_delete)
                if (MainActivity.displayedMediaList.isEmpty()) finish()
            } else {
                showFsCustomToast("Taşıma başarısız oldu", android.R.drawable.ic_menu_info_details)
            }
        }
    }
}

fun FullScreenActivity.deletePermanently() {
    if (currentPosition >= MainActivity.displayedMediaList.size) return
    val item = MainActivity.displayedMediaList[currentPosition]
    
    lifecycleScope.launch(Dispatchers.IO) {
        var success = false
        try {
            val file = File(item.path)
            if (file.exists() && file.delete()) {
                if (item.uri.scheme != "file") {
                    contentResolver.delete(item.uri, null, null)
                }
                MainActivity.trashedPaths.remove(item.path)
                MainActivity.trashedOriginalPaths.remove(item.path)
                MainActivity.trashedTimestamps.remove(item.path)
                MainActivity.trashedIsVideo.remove(item.path)
                MainActivity.trashedDurations.remove(item.path)
                MainActivity.trashedSizes.remove(item.path)
                success = true
            } else {
                if (item.uri.scheme != "file") {
                    val rows = contentResolver.delete(item.uri, null, null)
                    if (rows > 0) {
                        MainActivity.trashedPaths.remove(item.path)
                        MainActivity.trashedOriginalPaths.remove(item.path)
                        MainActivity.trashedTimestamps.remove(item.path)
                        MainActivity.trashedIsVideo.remove(item.path)
                        MainActivity.trashedDurations.remove(item.path)
                        MainActivity.trashedSizes.remove(item.path)
                        success = true
                    }
                }
            }
        } catch (e: Exception) {}

        withContext(Dispatchers.Main) {
            if (success) {
                MainActivity.saveTrashedPaths(this@deletePermanently)
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
                showFsCustomToast(msg, R.drawable.ic_action_delete)
                if (MainActivity.displayedMediaList.isEmpty()) finish()
            } else {
                showFsCustomToast("Silme başarısız oldu", android.R.drawable.ic_menu_info_details)
            }
        }
    }
}

fun FullScreenActivity.performHideMedia() {
    if (currentPosition >= MainActivity.displayedMediaList.size) return
    val item = MainActivity.displayedMediaList[currentPosition]

    lifecycleScope.launch(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(this@performHideMedia)
        val hiddenFolder = File(filesDir, "hidden_vault")
        if (!hiddenFolder.exists()) hiddenFolder.mkdirs()

        var success = false
        try {
            val sourceFile = File(item.path)
            if (sourceFile.exists()) {
                val originalDate = item.dateAdded * 1000L 
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
        } catch (e: Exception) {}

        withContext(Dispatchers.Main) {
            if (success) {
                val msg = if (item.isVideo) "1 video gizlendi" else "1 fotoğraf gizlendi"
                showFsCustomToast(msg, android.R.drawable.ic_secure)
                
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
                showFsCustomToast("Gizleme başarısız oldu", android.R.drawable.ic_menu_info_details)
            }
        }
    }
}

fun FullScreenActivity.showAlbumSelectionDialog(action: String, itemsToProcess: List<MediaItem>) {
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
        layoutManager = GridLayoutManager(this@showAlbumSelectionDialog, 3)
        adapter = FsDialogAlbumAdapter(this@showAlbumSelectionDialog, uniqueAlbums) { selectedAlbum ->
            dialog.dismiss()
            processCopyMove(action, itemsToProcess, File(selectedAlbum.locationName!!))
        }
    }
    layout.addView(recyclerView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    
    dialog.setContentView(layout)
    dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    dialog.show()
}

fun FullScreenActivity.processCopyMove(action: String, items: List<MediaItem>, destFolder: File) {
    showFsCustomToast("İşlem başlatıldı...", android.R.drawable.ic_menu_info_details)
    lifecycleScope.launch(Dispatchers.IO) {
        var pCount = 0
        var vCount = 0
        for (item in items) {
            try {
                val source = File(item.path)
                val dest = File(destFolder, source.name)
                if (source.exists() && source.absolutePath != dest.absolutePath) {
                    source.copyTo(dest, overwrite = true)
                    android.media.MediaScannerConnection.scanFile(this@processCopyMove, arrayOf(dest.absolutePath), null, null)
                    
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
            showFsCustomToast(msg, android.R.drawable.ic_menu_info_details)
            if (action == "MOVE") {
                MainActivity.forceReload = true
                finish()
            }
        }
    }
}

fun FullScreenActivity.showMoreMenu(view: View) {
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
                        } catch (e: Exception) { showFsCustomToast("Oynatıcı bulunamadı", android.R.drawable.ic_menu_info_details) }
                    }
                }
            }
        })
    }
    
    menuLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    popup.showAsDropDown(view, -50, -(menuLayout.measuredHeight + view.height + 30))
}

fun FullScreenActivity.showDateEditDialog() {
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

fun FullScreenActivity.saveNewDate(newTime: Long) {
    if (currentPosition >= MainActivity.displayedMediaList.size) return
    val item = MainActivity.displayedMediaList[currentPosition]
    val dateStr = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(java.util.Date(newTime))
    
    showFsCustomToast("Tarih güncelleniyor...", android.R.drawable.ic_menu_info_details)
    
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

            MediaScannerConnection.scanFile(this@saveNewDate, arrayOf(item.path), null, null)

            withContext(Dispatchers.Main) { 
                showFsCustomToast("Tarih başarıyla güncellendi", android.R.drawable.ic_menu_info_details)
                MainActivity.mediaList.sortByDescending { it.dateAdded }
                MainActivity.forceReload = true 
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { 
                showFsCustomToast("Hata: Tarih güncellenemedi", android.R.drawable.ic_menu_info_details)
            }
        }
    }
}

fun FullScreenActivity.showClearLocationConfirmationDialog(items: List<MediaItem>) {
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

fun FullScreenActivity.showLocationEditDialog(anchor: View, items: List<MediaItem>) {
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

fun FullScreenActivity.showInteractiveMapDialog(items: List<MediaItem>) {
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
                post { this@showInteractiveMapDialog.showFsCustomToast(msg, android.R.drawable.ic_menu_info_details) }
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
                showFsCustomToast("Lütfen haritadan bir konum seçin", android.R.drawable.ic_menu_info_details)
            }
        }
    }
    
    bottomBar.addView(btnCancel)
    bottomBar.addView(btnSave)
    layout.addView(bottomBar)

    dialog.setContentView(layout)
    dialog.show()
}

fun FullScreenActivity.clearLocationData(items: List<MediaItem>) {
    showFsCustomToast("Konum temizleniyor...", android.R.drawable.ic_menu_info_details)
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
            showFsCustomToast("$itemType konum verileri temizlendi", android.R.drawable.ic_menu_info_details)
        }
    }
}

fun convertFsDecimalToDMS(coord: Double): String {
    val absCoord = Math.abs(coord)
    val degree = absCoord.toInt()
    val minDouble = (absCoord - degree) * 60
    val minute = minDouble.toInt()
    val second = ((minDouble - minute) * 60 * 1000).toInt()
    return "$degree/1,$minute/1,$second/1000"
}

fun FullScreenActivity.updateLocationData(items: List<MediaItem>, lat: Double, lng: Double) {
    showFsCustomToast("Konum güncelleniyor...", android.R.drawable.ic_menu_info_details)
    lifecycleScope.launch(Dispatchers.IO) {
        val latStr = convertFsDecimalToDMS(lat)
        val lngStr = convertFsDecimalToDMS(lng)
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
            showFsCustomToast("$itemType konumu başarıyla güncellendi", android.R.drawable.ic_menu_info_details)
        }
    }
}

@Suppress("DEPRECATION")
fun FullScreenActivity.showModernDetailsBottomSheet() {
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
            val retriever = MediaMetadataRetriever().apply { setDataSource(this@showModernDetailsBottomSheet, item.uri) }
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
                        val geocoder = Geocoder(this@showModernDetailsBottomSheet, Locale.getDefault())
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
                val geocoder = Geocoder(this@showModernDetailsBottomSheet, Locale.getDefault())
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
