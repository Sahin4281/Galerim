package com.sahin.galerim

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.*
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import android.content.Context
import android.content.Intent

class DialogAlbumAdapter(
    private val albums: List<Album>,
    private val context: MainActivity,
    private val onClick: (Album) -> Unit
) : RecyclerView.Adapter<DialogAlbumAdapter.ViewHolder>() {

    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.thumbnail)
        val name: TextView = v.findViewById(R.id.albumName)
        init {
            v.setOnClickListener {
                onClick(albums[bindingAdapterPosition])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        android.view.LayoutInflater.from(context).inflate(R.layout.item_album, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val a = albums[position]
        Glide.with(context)
            .asBitmap()
            .load(a.thumbnail)
            .error(context.getPlaceholder())
            .centerCrop()
            .into(holder.thumb)
        holder.name.text = "${a.name}\n${a.count}"
        holder.name.setTextColor(ContextCompat.getColor(context, R.color.p_app_text_primary))
    }

    override fun getItemCount() = albums.size
}

fun MainActivity.showAlbumSelectionDialog(action: String, itemsToProcess: List<MediaItem>) {
    val activity = this
    val primaryColor = ContextCompat.getColor(activity, R.color.p_app_text_primary)
    val menuBgColor = getMenuBgColor()
    val iconTint = ContextCompat.getColor(activity, R.color.p_app_icon_tint)
    
    val dialog = BottomSheetDialog(activity)
    val layout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(40, 48, 40, 64)
        background = GradientDrawable().apply { 
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 60f
            setColor(menuBgColor) 
        }
    }
    
    val title = TextView(activity).apply {
        text = if (action == "COPY") "Kopyalanacak Albümü Seçin" else "Taşınacak Albümü Seçin"
        setTextColor(primaryColor)
        textSize = 18f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(24, 0, 0, 20)
    }
    layout.addView(title)

    val btnCreateAlbum = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(24, 32, 24, 32)
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
        setBackgroundResource(typedValue.resourceId)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            dialog.dismiss()
            showCreateAlbumDialog(action, itemsToProcess)
        }
    }

    btnCreateAlbum.addView(ImageView(activity).apply {
        setImageResource(android.R.drawable.ic_input_add)
        setColorFilter(iconTint)
        layoutParams = LinearLayout.LayoutParams(64, 64).apply { setMargins(0, 0, 32, 0) }
    })

    btnCreateAlbum.addView(TextView(activity).apply {
        text = "Yeni Albüm Oluştur"
        setTextColor(primaryColor)
        textSize = 16f
    })

    layout.addView(btnCreateAlbum)
    
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
    
    val recyclerView = RecyclerView(activity).apply {
        layoutManager = GridLayoutManager(activity, 3)
        adapter = DialogAlbumAdapter(uniqueAlbums, activity) { selectedAlbum ->
            dialog.dismiss()
            processCopyMove(action, itemsToProcess, File(selectedAlbum.locationName!!))
        }
    }
    
    layout.addView(recyclerView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    
    dialog.setContentView(layout)
    dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    dialog.show()
}

fun MainActivity.showCreateAlbumDialog(action: String, itemsToProcess: List<MediaItem>) {
    val dialog = BottomSheetDialog(this)
    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 48, 48, 48)
        background = GradientDrawable().apply {
            setColor(getMenuBgColor())
            cornerRadii = floatArrayOf(60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f)
        }
    }

    val title = TextView(this).apply {
        text = "Yeni Albüm Oluştur"
        setTextColor(Color.parseColor("#888888"))
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, 32)
    }
    layout.addView(title)

    val input = android.widget.EditText(this).apply {
        hint = "Albüm Adı"
        setHintTextColor(Color.parseColor("#888888"))
        setTextColor(ContextCompat.getColor(this@showCreateAlbumDialog, R.color.p_app_text_primary))
        backgroundTintList = android.content.res.ColorStateList.valueOf(getAccentColor())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    layout.addView(input)

    val btnSave = AppCompatButton(this).apply {
        text = "Oluştur ve ${if (action == "COPY") "Kopyala" else "Taşı"}"
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 32 }
        background = GradientDrawable().apply {
            setColor(getAccentColor())
            cornerRadius = 30f
        }
        setTextColor(Color.WHITE)
        setOnClickListener {
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val newAlbumFolder = File(picturesDir, newName)
                if (!newAlbumFolder.exists()) {
                    newAlbumFolder.mkdirs()
                }
                dialog.dismiss()
                processCopyMove(action, itemsToProcess, newAlbumFolder)
            }
        }
    }
    layout.addView(btnSave)

    dialog.setContentView(layout)
    dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    dialog.show()
}

fun MainActivity.showAlbumDeleteConfirmationDialog(albums: List<Album>) {
    val activity = this
    val primaryColor = ContextCompat.getColor(activity, R.color.p_app_text_primary)
    val menuBgColor = getMenuBgColor()
    
    val dialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.dialog_trash_confirmation, null)
    dialog.setContentView(view)
    
    view.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 60f
        setColor(menuBgColor)
    }
    
    dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    
    val messageView = view.findViewById<TextView>(R.id.dialogMessage)
    messageView.gravity = Gravity.CENTER
    messageView.setTextColor(primaryColor)

    val albumCount = albums.size
    messageView.text = "$albumCount seçili albüm içindeki tüm dosyalar Çöp Kutusuna taşınsın mı?"
    
    val btnConfirm = view.findViewById<AppCompatButton>(R.id.btnConfirm)
    val parentLayout = btnConfirm.parent as? ViewGroup
    
    if (parentLayout is LinearLayout) {
        parentLayout.removeAllViews()
        parentLayout.gravity = Gravity.CENTER
        
        val dp10 = (10 * activity.resources.displayMetrics.density).toInt()
        val btnWidth = (110 * activity.resources.displayMetrics.density).toInt() 
        val btnHeight = (42 * activity.resources.displayMetrics.density).toInt()
        
        val btnCancelNew = AppCompatButton(activity).apply {
            text = "İptal"
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
        
        val btnConfirmNew = AppCompatButton(activity).apply {
            text = "Sil"
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
                val itemsToDelete = mutableListOf<MediaItem>()
                albums.forEach { album ->
                    if (album.locationName != null) {
                        itemsToDelete.addAll(MainActivity.mediaList.filter { File(it.path).parentFile?.absolutePath == album.locationName })
                    } else if (album.bucketId != null) {
                        itemsToDelete.addAll(MainActivity.mediaList.filter { it.bucketId == album.bucketId })
                    }
                }
                if (itemsToDelete.isNotEmpty()) {
                    performMultiDelete(itemsToDelete, true)
                }
                exitAlbumSelectionMode()
            }
        }
        
        parentLayout.addView(btnCancelNew)
        parentLayout.addView(btnConfirmNew)
    }

    dialog.show()
}

fun MainActivity.showRenameMediaDialog(item: MediaItem) {
    val dialog = BottomSheetDialog(this)
    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 48, 48, 48)
        background = GradientDrawable().apply {
            setColor(getMenuBgColor())
            cornerRadii = floatArrayOf(60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f)
        }
    }

    val title = TextView(this).apply {
        text = "Yeniden İsimlendir"
        setTextColor(Color.parseColor("#888888"))
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, 32)
    }
    layout.addView(title)

    val currentFile = File(item.path)
    val extension = currentFile.extension
    val nameWithoutExtension = currentFile.nameWithoutExtension

    val input = android.widget.EditText(this).apply {
        setText(nameWithoutExtension)
        setTextColor(ContextCompat.getColor(this@showRenameMediaDialog, R.color.p_app_text_primary))
        backgroundTintList = android.content.res.ColorStateList.valueOf(getAccentColor())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    layout.addView(input)

    val btnSave = AppCompatButton(this).apply {
        text = "Kaydet"
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 32 }
        background = GradientDrawable().apply {
            setColor(getAccentColor())
            cornerRadius = 30f
        }
        setTextColor(Color.WHITE)
        setOnClickListener {
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty() && newName != nameWithoutExtension) {
                dialog.dismiss()
                renameMediaFile(item, "$newName.$extension")
            }
        }
    }
    layout.addView(btnSave)

    dialog.setContentView(layout)
    dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    dialog.show()
}

fun FullScreenActivity.showRenameMediaDialogFs(item: MediaItem) {
    val dialog = BottomSheetDialog(this)
    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 48, 48, 48)
        
        val themePrefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val currentTheme = themePrefs.getString("appTheme", "Sistem Teması")
        val isAmoled = currentTheme == "Koyu Amoled Tema"
        val menuBgColor = if (isAmoled) Color.parseColor("#121212") else ContextCompat.getColor(this@showRenameMediaDialogFs, R.color.p_app_dialog_bg)
        
        background = GradientDrawable().apply {
            setColor(menuBgColor)
            cornerRadii = floatArrayOf(60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f)
        }
    }

    val title = TextView(this).apply {
        text = "Yeniden İsimlendir"
        setTextColor(Color.parseColor("#888888"))
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, 32)
    }
    layout.addView(title)

    val currentFile = File(item.path)
    val extension = currentFile.extension
    val nameWithoutExtension = currentFile.nameWithoutExtension

    val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
    val accentColorStr = prefs.getString("accentColor", "#5C94FF") ?: "#5C94FF"
    val accentColor = Color.parseColor(accentColorStr)

    val input = android.widget.EditText(this).apply {
        setText(nameWithoutExtension)
        setTextColor(ContextCompat.getColor(this@showRenameMediaDialogFs, R.color.p_app_text_primary))
        backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    layout.addView(input)

    val btnSave = AppCompatButton(this).apply {
        text = "Kaydet"
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 32 }
        background = GradientDrawable().apply {
            setColor(accentColor)
            cornerRadius = 30f
        }
        setTextColor(Color.WHITE)
        setOnClickListener {
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty() && newName != nameWithoutExtension) {
                dialog.dismiss()
                renameMediaFileFs(item, "$newName.$extension")
            }
        }
    }
    layout.addView(btnSave)

    dialog.setContentView(layout)
    dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    dialog.show()
}

fun MainActivity.showHideConfirmationDialog(items: List<MediaItem>) {
    val activity = this
    val primaryColor = ContextCompat.getColor(activity, R.color.p_app_text_primary)
    val menuBgColor = getMenuBgColor()
    
    val dialog = BottomSheetDialog(activity)
    val view = activity.layoutInflater.inflate(R.layout.dialog_trash_confirmation, null)
    dialog.setContentView(view)
    
    view.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 60f
        setColor(menuBgColor)
    }
    
    dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    
    val messageView = view.findViewById<TextView>(R.id.dialogMessage)
    messageView.gravity = Gravity.CENTER
    messageView.setTextColor(primaryColor)

    var photoCount = 0
    var videoCount = 0
    
    items.forEach { 
        if (it.isVideo) videoCount++ else photoCount++ 
    }
    
    val itemsText = when {
        photoCount > 0 && videoCount > 0 -> "$photoCount fotoğraf ve $videoCount video"
        photoCount > 0 -> "$photoCount fotoğraf"
        videoCount > 0 -> "$videoCount video"
        else -> ""
    }
    
    messageView.text = "$itemsText gizli klasöre taşınsın mı?"
    
    val btnConfirm = view.findViewById<AppCompatButton>(R.id.btnConfirm)
    val parentLayout = btnConfirm.parent as? ViewGroup
    
    if (parentLayout is LinearLayout) {
        parentLayout.removeAllViews()
        parentLayout.gravity = Gravity.CENTER
        
        val dp10 = (10 * activity.resources.displayMetrics.density).toInt()
        val btnWidth = (110 * activity.resources.displayMetrics.density).toInt() 
        val btnHeight = (42 * activity.resources.displayMetrics.density).toInt()
        
        val btnCancelNew = AppCompatButton(activity).apply {
            text = "İptal"
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
        
        val btnConfirmNew = AppCompatButton(activity).apply {
            text = "Gizle"
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
                activity.performHideMedia(items)
            }
        }
        
        parentLayout.addView(btnCancelNew)
        parentLayout.addView(btnConfirmNew)
    }

    dialog.show()
}

fun MainActivity.showSelectionMoreMenu() {
    val activity = this
    val btnMore = findViewById<View>(R.id.btnMore) ?: return
    val primaryColor = ContextCompat.getColor(activity, R.color.p_app_text_primary)
    val menuBgColor = getMenuBgColor()
    
    val menuLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { 
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 45f
            setColor(menuBgColor) 
        }
        setPadding(0, 24, 0, 24)
    }
    
    moreMenuPopup = PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
    moreMenuPopup?.elevation = 30f 
    
    val allAreFavorites = selectedMedia.all { MainActivity.favoritePaths.contains(it.path) }
    val favoriteAction = if (allAreFavorites) "Favorilerden çıkar" else "Favorilere ekle"
    
    val options = mutableListOf("Ayrıntılar", favoriteAction, "Gizle", "Albüme kopyala", "Albüme taşı", "Tarih ve saati düzenle", "Konumu düzenle")
    
    if (selectedMedia.size == 1) {
        options.add(1, "Yeniden isimlendir")
    }
    
    for (opt in options) {
        menuLayout.addView(TextView(activity).apply {
            text = opt
            setTextColor(primaryColor)
            textSize = 15f
            setPadding(64, 32, 64, 32)
            setOnClickListener {
                moreMenuPopup?.dismiss()
                
                when(opt) {
                    "Ayrıntılar" -> {
                        if (selectedMedia.size == 1) {
                            showSingleItemDetailsBottomSheet(selectedMedia.first())
                        } else {
                            showMultiDetailsBottomSheet(selectedMedia.toList())
                        }
                    }
                    "Yeniden isimlendir" -> {
                        if (selectedMedia.size == 1) {
                            showRenameMediaDialog(selectedMedia.first())
                        }
                    }
                    "Favorilere ekle" -> {
                        var photoCount = 0
                        var videoCount = 0
                        selectedMedia.forEach { 
                            MainActivity.favoritePaths.add(it.path) 
                            if (it.isVideo) videoCount++ else photoCount++
                        }
                        MainActivity.saveFavoritePaths(activity)
                        
                        val msg = when {
                            photoCount > 0 && videoCount > 0 -> "$photoCount fotoğraf, $videoCount video favorilere eklendi"
                            photoCount > 0 -> "$photoCount fotoğraf favorilere eklendi"
                            videoCount > 0 -> "$videoCount video favorilere eklendi"
                            else -> ""
                        }
                        
                        exitSelectionMode()
                        if (msg.isNotEmpty()) activity.showNoIconToast(msg)
                    }
                    "Favorilerden çıkar" -> {
                        var photoCount = 0
                        var videoCount = 0
                        selectedMedia.forEach { 
                            MainActivity.favoritePaths.remove(it.path) 
                            if (it.isVideo) videoCount++ else photoCount++
                        }
                        MainActivity.saveFavoritePaths(activity)
                        
                        val msg = when {
                            photoCount > 0 && videoCount > 0 -> "$photoCount fotoğraf, $videoCount video favorilerden çıkarıldı"
                            photoCount > 0 -> "$photoCount fotoğraf favorilerden çıkarıldı"
                            videoCount > 0 -> "$videoCount video favorilerden çıkarıldı"
                            else -> ""
                        }
                        
                        exitSelectionMode()
                        if (msg.isNotEmpty()) activity.showNoIconToast(msg)
                        if (isShowingFavorites) loadDisplayedList()
                    }
                    "Gizle" -> { 
                        showHideConfirmationDialog(selectedMedia.toList())
                    }
                    "Albüme kopyala" -> {
                        showAlbumSelectionDialog("COPY", selectedMedia.toList())
                    }
                    "Albüme taşı" -> {
                        showAlbumSelectionDialog("MOVE", selectedMedia.toList())
                    }
                    "Tarih ve saati düzenle" -> {
                        showDateEditDialog(selectedMedia.toList())
                    }
                    "Konumu düzenle" -> {
                        showLocationEditDialog(btnMore, selectedMedia.toList())
                    }
                }
            }
        })
    }
    
    menuLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    moreMenuPopup?.showAsDropDown(btnMore, -50, -(menuLayout.measuredHeight + btnMore.height + 30))
}

fun MainActivity.showDateEditDialog(items: List<MediaItem>) {
    val activity = this
    val cal = Calendar.getInstance()
    val primaryColor = ContextCompat.getColor(activity, R.color.p_app_text_primary)
    val menuBgColor = getMenuBgColor()
    
    val dialog = BottomSheetDialog(activity)
    val layout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(40, 48, 40, 64)
        background = GradientDrawable().apply { 
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 60f
            setColor(menuBgColor) 
        }
    }

    layout.addView(TextView(activity).apply {
        text = "Tarih ve Saati Düzenle"
        setTextColor(primaryColor)
        textSize = 18f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(24, 0, 0, 40)
    })

    val pickersLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, 20, 0, 40)
    }

    val npDay = NumberPicker(activity).apply { 
        minValue = 1
        maxValue = 31
        value = cal.get(Calendar.DAY_OF_MONTH) 
    }
    
    val npMonth = NumberPicker(activity).apply { 
        minValue = 0
        maxValue = 11
        displayedValues = arrayOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")
        value = cal.get(Calendar.MONTH) 
    }
    
    val npYear = NumberPicker(activity).apply { 
        minValue = 1970
        maxValue = 2050
        value = cal.get(Calendar.YEAR) 
    }
    
    val npHour = NumberPicker(activity).apply { 
        minValue = 0
        maxValue = 23
        value = cal.get(Calendar.HOUR_OF_DAY) 
    }
    
    val npMin = NumberPicker(activity).apply { 
        minValue = 0
        maxValue = 59
        value = cal.get(Calendar.MINUTE) 
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        npDay.textColor = primaryColor
        npMonth.textColor = primaryColor
        npYear.textColor = primaryColor
        npHour.textColor = primaryColor
        npMin.textColor = primaryColor
    }

    pickersLayout.addView(npDay)
    pickersLayout.addView(npMonth)
    pickersLayout.addView(npYear)
    
    pickersLayout.addView(TextView(activity).apply { text = "  " })
    pickersLayout.addView(npHour)
    
    pickersLayout.addView(TextView(activity).apply { 
        text = ":" 
        setTextColor(primaryColor)
        textSize = 20f
        setPadding(10,0,10,0) 
    })
    
    pickersLayout.addView(npMin)
    layout.addView(pickersLayout)

    val btnLayout = LinearLayout(activity).apply { 
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END 
    }
    
    btnLayout.addView(TextView(activity).apply { 
        text = "İptal"
        setTextColor(Color.parseColor("#888888"))
        textSize = 16f
        setPadding(32, 24, 32, 24)
        setOnClickListener { dialog.dismiss() } 
    })
    
    btnLayout.addView(TextView(activity).apply {
        text = "Kaydet"
        setTextColor(Color.parseColor("#FF9800"))
        textSize = 16f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(32, 24, 32, 24)
        setOnClickListener {
            npDay.clearFocus()
            npMonth.clearFocus()
            npYear.clearFocus()
            npHour.clearFocus()
            npMin.clearFocus()
            
            dialog.dismiss()
            cal.set(npYear.value, npMonth.value, npDay.value, npHour.value, npMin.value)
            saveNewDateToItems(items, cal)
        }
    })
    
    layout.addView(btnLayout)

    dialog.setContentView(layout)
    dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    dialog.show()
}

fun MainActivity.showClearLocationConfirmationDialog(items: List<MediaItem>) {
    val activity = this
    val primaryColor = ContextCompat.getColor(activity, R.color.p_app_text_primary)
    val menuBgColor = getMenuBgColor()
    
    val dialog = BottomSheetDialog(activity)
    val view = layoutInflater.inflate(R.layout.dialog_trash_confirmation, null)
    dialog.setContentView(view)
    
    view.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 60f
        setColor(menuBgColor)
    }
    
    dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    
    val messageView = view.findViewById<TextView>(R.id.dialogMessage)
    messageView.gravity = Gravity.CENTER
    messageView.setTextColor(primaryColor)

    var photoCount = 0
    var videoCount = 0
    
    items.forEach { 
        if (it.isVideo) videoCount++ else photoCount++ 
    }
    
    val itemsText = when {
        photoCount > 0 && videoCount > 0 -> "$photoCount fotoğraf ve $videoCount videonun"
        photoCount > 0 -> "$photoCount fotoğrafın"
        videoCount > 0 -> "$videoCount videonun"
        else -> ""
    }
    
    messageView.text = "$itemsText konum bilgisi temizlensin mi?"
    
    val btnConfirm = view.findViewById<AppCompatButton>(R.id.btnConfirm)
    val parentLayout = btnConfirm.parent as? ViewGroup
    
    if (parentLayout is LinearLayout) {
        parentLayout.removeAllViews()
        parentLayout.gravity = Gravity.CENTER
        
        val dp10 = (10 * resources.displayMetrics.density).toInt()
        val btnWidth = (110 * resources.displayMetrics.density).toInt() 
        val btnHeight = (42 * resources.displayMetrics.density).toInt()
        
        val btnCancelNew = AppCompatButton(activity).apply {
            text = "İptal"
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
        
        val btnConfirmNew = AppCompatButton(activity).apply {
            text = "Temizle"
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
                clearLocationData(items)
            }
        }
        
        parentLayout.addView(btnCancelNew)
        parentLayout.addView(btnConfirmNew)
    }

    dialog.show()
}

fun MainActivity.showLocationEditDialog(anchor: View, items: List<MediaItem>) {
    val activity = this
    val primaryColor = ContextCompat.getColor(activity, R.color.p_app_text_primary)
    val iconTint = ContextCompat.getColor(activity, R.color.p_app_icon_tint)
    val menuBgColor = getMenuBgColor()

    val menuLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { 
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 45f
            setColor(menuBgColor) 
        }
        setPadding(0, 24, 0, 24)
    }
    
    val popup = PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
    popup.elevation = 30f 

    val btnSelect = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(64, 32, 64, 32)
        setOnClickListener {
            popup.dismiss()
            showInteractiveMapDialog(items)
        }
    }
    
    btnSelect.addView(ImageView(activity).apply {
        setImageResource(R.drawable.ic_menu_locations)
        setColorFilter(iconTint)
        layoutParams = LinearLayout.LayoutParams(64, 64).apply { setMargins(0, 0, 32, 0) }
    })
    
    btnSelect.addView(TextView(activity).apply {
        text = "Konum seç"
        setTextColor(primaryColor)
        textSize = 15f
    })
    
    menuLayout.addView(btnSelect)

    val btnClear = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(64, 32, 64, 32)
        setOnClickListener {
            popup.dismiss()
            showClearLocationConfirmationDialog(items)
        }
    }
    
    btnClear.addView(ImageView(activity).apply {
        setImageResource(R.drawable.ic_action_delete)
        setColorFilter(Color.parseColor("#FF5252"))
        layoutParams = LinearLayout.LayoutParams(64, 64).apply { setMargins(0, 0, 32, 0) }
    })
    
    btnClear.addView(TextView(activity).apply {
        text = "Konum temizle"
        setTextColor(Color.parseColor("#FF5252"))
        textSize = 15f
    })
    
    menuLayout.addView(btnClear)

    menuLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    popup.showAsDropDown(anchor, -50, -(menuLayout.measuredHeight + anchor.height + 30))
}

fun MainActivity.showInteractiveMapDialog(items: List<MediaItem>) {
    val activity = this
    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        }
    }
    
    val primaryColor = ContextCompat.getColor(activity, R.color.p_app_text_primary)
    val iconTint = ContextCompat.getColor(activity, R.color.p_app_icon_tint)
    val bgColor = ContextCompat.getColor(activity, R.color.p_app_background)
    val actualBg = if (isAmoledTheme) Color.BLACK else bgColor

    val dialog = android.app.Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    
    val rootLayout = RelativeLayout(activity).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(actualBg)
    }
    
    val topBarId = View.generateViewId()
    val topBar = android.widget.RelativeLayout(activity).apply {
        id = topBarId
        setPadding(40, 40, 40, 40)
        setBackgroundColor(actualBg)
    }
    
    val btnClose = ImageView(activity).apply {
        layoutParams = android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
            android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
        }
        setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        setColorFilter(iconTint)
        setOnClickListener { dialog.dismiss() }
    }
    
    val topTitle = TextView(activity).apply {
        text = "Haritadan Konum Seç"
        setTextColor(primaryColor)
        textSize = 18f
        setTypeface(null, android.graphics.Typeface.BOLD)
    }
    
    val paramsTitle = android.widget.RelativeLayout.LayoutParams(
        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT, 
        android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        addRule(android.widget.RelativeLayout.CENTER_IN_PARENT)
    }
    
    topBar.addView(btnClose)
    topBar.addView(topTitle, paramsTitle)
    
    val bottomBarId = View.generateViewId()
    val bottomBar = LinearLayout(activity).apply {
        id = bottomBarId
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(actualBg)
    }
    
    var selectedLat: Double? = null
    var selectedLng: Double? = null
    var currentMarker: com.google.android.gms.maps.model.Marker? = null
    var gMap: com.google.android.gms.maps.GoogleMap? = null
    
    val btnCancel = TextView(activity).apply {
        text = "İptal"
        setTextColor(primaryColor)
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(0, 40, 0, 40)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener { dialog.dismiss() }
    }
    
    val btnSave = TextView(activity).apply {
        text = "Tamamlandı"
        setTextColor(Color.parseColor("#FF9800"))
        textSize = 16f
        setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, 40, 0, 40)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener {
            val target = gMap?.cameraPosition?.target
            val finalLat = selectedLat ?: target?.latitude
            val finalLng = selectedLng ?: target?.longitude

            if (finalLat != null && finalLng != null) {
                dialog.dismiss()
                updateLocationData(items, finalLat, finalLng)
            } else {
                activity.showNoIconToast("Lütfen haritadan bir konum seçin")
            }
        }
    }
    
    bottomBar.addView(btnCancel)
    bottomBar.addView(btnSave)
    
    val mapView = com.google.android.gms.maps.MapView(activity)
    
    topBar.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
        addRule(RelativeLayout.ALIGN_PARENT_TOP)
    }
    
    bottomBar.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
        addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
    }
    
    mapView.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT).apply {
        addRule(RelativeLayout.BELOW, topBarId)
        addRule(RelativeLayout.ABOVE, bottomBarId)
    }
    
    mapView.onCreate(null)
    mapView.onResume()
    
    mapView.getMapAsync { googleMap ->
        gMap = googleMap
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
        }
        
        val turkey = com.google.android.gms.maps.model.LatLng(39.0, 35.0)
        googleMap.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(turkey, 5f))
        
        googleMap.setOnMapLongClickListener { latLng ->
            if (currentMarker == null) {
                currentMarker = googleMap.addMarker(com.google.android.gms.maps.model.MarkerOptions().position(latLng).draggable(true))
            } else {
                currentMarker?.position = latLng
            }
            selectedLat = latLng.latitude
            selectedLng = latLng.longitude
        }
        
        googleMap.setOnMarkerDragListener(object : com.google.android.gms.maps.GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: com.google.android.gms.maps.model.Marker) {}
            override fun onMarkerDrag(marker: com.google.android.gms.maps.model.Marker) {}
            override fun onMarkerDragEnd(marker: com.google.android.gms.maps.model.Marker) {
                selectedLat = marker.position.latitude
                selectedLng = marker.position.longitude
            }
        })
    }
    
    rootLayout.addView(topBar)
    rootLayout.addView(mapView)
    rootLayout.addView(bottomBar)
    
    dialog.setOnDismissListener {
        mapView.onDestroy()
    }

    dialog.setContentView(rootLayout)
    dialog.show()
}

fun MainActivity.showSingleItemDetailsBottomSheet(item: MediaItem) {
    val activity = this
    val primaryColor = ContextCompat.getColor(activity, R.color.p_app_text_primary)
    val menuBgColor = getMenuBgColor()
    
    val dialog = BottomSheetDialog(activity)
    val layout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(64, 48, 64, 64)
        background = GradientDrawable().apply { 
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 60f
            setColor(menuBgColor) 
        }
    }
    
    val dateStr = SimpleDateFormat("d MMMM yyyy HH:mm", Locale("tr")).format(java.util.Date(item.dateAdded * 1000))
    
    layout.addView(TextView(activity).apply {
        text = dateStr
        setTextColor(primaryColor)
        textSize = 18f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, 32)
    })
    
    val file = File(item.path)
    var readablePath = (file.parent ?: "").replace("/storage/emulated/0", "Dahili depolama")
    if (!readablePath.endsWith("/")) readablePath += "/"
    
    var resolutionStr = "Bilinmiyor"
    var locationStr = "Bilinmiyor"

    try {
        if (item.isVideo) {
            val retriever = MediaMetadataRetriever().apply { setDataSource(activity, item.uri) }
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
                        val geocoder = Geocoder(activity, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(lat, lng, 1)
                        if (!addresses.isNullOrEmpty()) locationStr = addresses[0].getAddressLine(0) ?: "$lat, $lng"
                    }
                }
            }
            retriever.release()
        } else {
            contentResolver.query(item.uri, arrayOf(MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val w = cursor.getInt(0)
                    val h = cursor.getInt(1)
                    if (w > 0 && h > 0) resolutionStr = "${w}x${h}  |  ${String.format("%.1f", (w * h) / 1000000.0)}MP"
                }
            }
            
            val exif = ExifInterface(item.path)
            val latLong = FloatArray(2)
            if (exif.getLatLong(latLong)) {
                val lat = latLong[0].toDouble()
                val lng = latLong[1].toDouble()
                val geocoder = Geocoder(activity, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) locationStr = addresses[0].getAddressLine(0) ?: "$lat, $lng"
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
        layout.addView(TextView(activity).apply { 
            text = info[i]
            setTextColor(Color.parseColor("#888888"))
            textSize = 13f 
        })
        layout.addView(TextView(activity).apply { 
            text = info[i+1]
            setTextColor(primaryColor)
            textSize = 15f
            setPadding(0, 0, 0, if (i == info.size - 2) 0 else 32) 
        })
    }
    
    dialog.setContentView(layout)
    dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    dialog.show()
}

fun MainActivity.showMultiDetailsBottomSheet(items: List<MediaItem>) {
    val activity = this
    val primaryColor = ContextCompat.getColor(activity, R.color.p_app_text_primary)
    val menuBgColor = getMenuBgColor()
    
    val dialog = BottomSheetDialog(activity)
    val layout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(64, 48, 64, 64)
        background = GradientDrawable().apply { 
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 60f
            setColor(menuBgColor) 
        }
    }
    
    var totalSize = 0L
    var videoCount = 0
    var imageCount = 0
    
    for (item in items) {
        totalSize += item.size
        if (item.isVideo) videoCount++ else imageCount++
    }
    
    val info = arrayOf(
        "Seçilen Öğeler:", "${items.size} adet",
        "İçerik:", "$imageCount Fotoğraf, $videoCount Video",
        "Toplam Boyut:", String.format("%.2f MB", totalSize / (1024.0 * 1024.0))
    )
    
    for (i in info.indices step 2) {
        layout.addView(TextView(activity).apply { 
            text = info[i]
            setTextColor(Color.parseColor("#888888"))
            textSize = 13f 
        })
        layout.addView(TextView(activity).apply { 
            text = info[i+1]
            setTextColor(primaryColor)
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, if (i == info.size - 2) 0 else 32) 
        })
    }
    
    dialog.setContentView(layout)
    dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    dialog.show()
}

fun androidx.appcompat.app.AppCompatActivity.showNoIconToast(message: String) {
    try {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = 50f
            }
            setPadding(40, 24, 40, 24)
        }
        val text = TextView(this).apply {
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
