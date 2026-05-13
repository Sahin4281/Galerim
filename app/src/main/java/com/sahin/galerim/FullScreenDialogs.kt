package com.sahin.galerim

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

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

fun FullScreenActivity.showHideConfirmationDialog(items: List<MediaItem>) {
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
            layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply { marginEnd = dp10 }
            setOnClickListener { dialog.dismiss() }
        }
        
        val btnConfirmNew = AppCompatButton(this).apply {
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
                performHideMedia()
            }
        }
        
        parentLayout.addView(btnCancelNew)
        parentLayout.addView(btnConfirmNew)
    }

    dialog.show()
}

fun FullScreenActivity.showAlbumSelectionDialog(action: String, itemsToProcess: List<MediaItem>) {
    val dialogBgColor = ContextCompat.getColor(this, R.color.p_app_dialog_bg)
    val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
    val iconTint = ContextCompat.getColor(this, R.color.p_app_icon_tint)
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
        setPadding(24, 0, 0, 20)
    }
    layout.addView(title)

    val btnCreateAlbum = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(24, 32, 24, 32)
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
        setBackgroundResource(typedValue.resourceId)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            dialog.dismiss()
            showCreateAlbumDialog(action, itemsToProcess)
        }
    }

    btnCreateAlbum.addView(ImageView(this).apply {
        setImageResource(android.R.drawable.ic_input_add)
        setColorFilter(iconTint)
        layoutParams = LinearLayout.LayoutParams(64, 64).apply { setMargins(0, 0, 32, 0) }
    })

    btnCreateAlbum.addView(TextView(this).apply {
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

fun FullScreenActivity.showCreateAlbumDialog(action: String, itemsToProcess: List<MediaItem>) {
    val dialog = BottomSheetDialog(this)
    val dialogBgColor = ContextCompat.getColor(this, R.color.p_app_dialog_bg)
    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 48, 48, 48)
        background = GradientDrawable().apply {
            setColor(if (isAmoledTheme) Color.BLACK else dialogBgColor)
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

    val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
    val accentColorStr = prefs.getString("accentColor", "#5C94FF") ?: "#5C94FF"
    val accentColor = Color.parseColor(accentColorStr)

    val input = android.widget.EditText(this).apply {
        hint = "Albüm Adı"
        setHintTextColor(Color.parseColor("#888888"))
        setTextColor(ContextCompat.getColor(this@showCreateAlbumDialog, R.color.p_app_text_primary))
        backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    layout.addView(input)

    val btnSave = AppCompatButton(this).apply {
        text = "Oluştur ve ${if (action == "COPY") "Kopyala" else "Taşı"}"
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 32 }
        background = GradientDrawable().apply {
            setColor(accentColor)
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
    
    val item = MainActivity.displayedMediaList[currentPosition]
    val isFavorite = MainActivity.favoritePaths.contains(item.path)
    val favOption = if (isFavorite) "Favorilerden çıkar" else "Favorilere ekle"
    
    val options = mutableListOf("Ayrıntılar", "Yeniden isimlendir", "Gizle", favOption, "Albüme kopyala", "Albüme taşı", "Tarih ve saati düzenle", "Konumu düzenle")
    
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
                    "Yeniden isimlendir" -> { 
                        val dialogActivity = this@showMoreMenu
                        try {
                            val dialogClass = Class.forName("com.sahin.galerim.DialogExtensionsKt")
                            val method = dialogClass.getMethod("showRenameMediaDialogFs", FullScreenActivity::class.java, MediaItem::class.java)
                            method.invoke(null, dialogActivity, item)
                        } catch (e: Exception) {
                            try {
                                val method2 = dialogActivity.javaClass.getMethod("showRenameMediaDialogFs", MediaItem::class.java)
                                method2.invoke(dialogActivity, item)
                            } catch (e2: Exception) {}
                        }
                    }
                    "Gizle" -> { showHideConfirmationDialog(listOf(item)) }
                    "Favorilere ekle", "Favorilerden çıkar" -> { toggleFavorite() }
                    "Albüme kopyala" -> { showAlbumSelectionDialog("COPY", listOf(item)) }
                    "Albüme taşı" -> { showAlbumSelectionDialog("MOVE", listOf(item)) }
                    "Tarih ve saati düzenle" -> { showDateEditDialog() }
                    "Konumu düzenle" -> { showLocationEditDialog(view, listOf(item)) }
                    "Video oynatıcıda aç" -> { 
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(item.uri, "video/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                            startActivity(intent)
                        } catch (e: Exception) { showFsCustomToast("Oynatıcı bulunamadı", 0) }
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
            npDay.clearFocus()
            npMonth.clearFocus()
            npYear.clearFocus()
            npHour.clearFocus()
            npMin.clearFocus()
            
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
    var currentMarker: com.google.android.gms.maps.model.Marker? = null
    var googleMapRef: com.google.android.gms.maps.GoogleMap? = null
    
    val mapView = com.google.android.gms.maps.MapView(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
    }
    
    try {
        mapView.onCreate(null)
        mapView.onResume()
        
        mapView.getMapAsync { googleMap ->
            googleMapRef = googleMap
            if (ContextCompat.checkSelfPermission(this@showInteractiveMapDialog, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    layout.addView(mapView)
    
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
            val finalLat = selectedLat ?: googleMapRef?.cameraPosition?.target?.latitude
            val finalLng = selectedLng ?: googleMapRef?.cameraPosition?.target?.longitude
            
            if (finalLat != null && finalLng != null) {
                dialog.dismiss()
                updateLocationData(items, finalLat, finalLng)
            } else {
                showFsCustomToast("Lütfen haritadan bir konum seçin", 0)
            }
        }
    }
    
    bottomBar.addView(btnCancel)
    bottomBar.addView(btnSave)
    layout.addView(bottomBar)

    dialog.setOnDismissListener {
        try { mapView.onDestroy() } catch (e: Exception) {}
    }

    dialog.setContentView(layout)
    dialog.show()
}

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
    
    val cachedLoc = MainActivity.itemLocationCache[item.path]
    var tempLat: Double? = null
    var tempLng: Double? = null
    
    if (cachedLoc != null) {
        val parts = cachedLoc.split(",")
        if (parts.size == 2) {
            tempLat = parts[0].toDoubleOrNull()
            tempLng = parts[1].toDoubleOrNull()
        }
    }

    try {
        if (item.isVideo) {
            if (tempLat == null || tempLng == null) {
                try {
                    contentResolver.query(item.uri, arrayOf(MediaStore.Video.Media.LATITUDE, MediaStore.Video.Media.LONGITUDE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val latIdx = cursor.getColumnIndex(MediaStore.Video.Media.LATITUDE)
                            val lngIdx = cursor.getColumnIndex(MediaStore.Video.Media.LONGITUDE)
                            if (latIdx >= 0 && lngIdx >= 0 && !cursor.isNull(latIdx) && !cursor.isNull(lngIdx)) {
                                val lat = cursor.getDouble(latIdx)
                                val lng = cursor.getDouble(lngIdx)
                                if (lat != 0.0 || lng != 0.0) {
                                    tempLat = lat
                                    tempLng = lng
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
            
            if (tempLat == null || tempLng == null) {
                var retriever: MediaMetadataRetriever? = null
                try {
                    retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(item.path)
                    } catch(e: Exception) {
                        retriever.setDataSource(this@showModernDetailsBottomSheet, item.uri)
                    }
                    
                    val locMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                    if (locMeta != null) {
                        val matcher = Pattern.compile("([+-][0-9]*\\.?[0-9]+)([+-][0-9]*\\.?[0-9]+)").matcher(locMeta)
                        if (matcher.find()) {
                            tempLat = matcher.group(1)?.toDoubleOrNull()
                            tempLng = matcher.group(2)?.toDoubleOrNull()
                        }
                    }
                    
                    if (tempLat == null || tempLng == null) {
                        val locMeta2 = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                        if (locMeta2 != null) {
                            val parts = locMeta2.split(" ")
                            if (parts.size >= 2) {
                                tempLat = parts[0].toDoubleOrNull()
                                tempLng = parts[1].toDoubleOrNull()
                            }
                        }
                    }
                } catch (e: Exception) {
                } finally {
                    try { retriever?.release() } catch (e: Exception) {}
                }
            }
            
            try {
                val retrieverRes = MediaMetadataRetriever()
                try {
                    retrieverRes.setDataSource(this@showModernDetailsBottomSheet, item.uri)
                    val w = retrieverRes.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
                    val h = retrieverRes.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
                    if (w > 0 && h > 0) resolutionStr = "${w}x${h}  |  ${String.format("%.1f", (w * h) / 1000000.0)}MP"
                } catch (e: Exception) {}
                finally { try { retrieverRes.release() } catch (e: Exception) {} }
            } catch (e: Exception) {}
            
        } else {
            contentResolver.query(item.uri, arrayOf(MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val w = cursor.getInt(0); val h = cursor.getInt(1)
                    if (w > 0 && h > 0) resolutionStr = "${w}x${h}  |  ${String.format("%.1f", (w * h) / 1000000.0)}MP"
                }
            }
            
            if (tempLat == null || tempLng == null) {
                val ext = File(item.path).extension.lowercase(Locale("tr"))
                if (listOf("jpg", "jpeg", "png", "webp").contains(ext)) {
                    try {
                        val exif = ExifInterface(item.path)
                        val latLong = FloatArray(2)
                        if (exif.getLatLong(latLong)) {
                            tempLat = latLong[0].toDouble()
                            tempLng = latLong[1].toDouble()
                        }
                    } catch (e: Exception) {}
                }
            }
        }
        
        val finalLat = tempLat
        val finalLng = tempLng
        
        if (finalLat != null && finalLng != null) {
            try {
                val geocoder = Geocoder(this@showModernDetailsBottomSheet, Locale.getDefault())
                val addresses = geocoder.getFromLocation(finalLat, finalLng, 1)
                if (!addresses.isNullOrEmpty()) {
                    locationStr = addresses[0].getAddressLine(0) ?: "$finalLat, $finalLng"
                } else {
                    locationStr = "$finalLat, $finalLng"
                }
            } catch (e: Exception) {
                locationStr = "$finalLat, $finalLng"
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
