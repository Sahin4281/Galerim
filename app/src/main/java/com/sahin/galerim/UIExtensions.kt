package com.sahin.galerim

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog

fun MainActivity.applyDynamicColorsToUI() {
    val activity = this
    val prefs = activity.getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
    val bgColor = ContextCompat.getColor(activity, R.color.p_app_background)
    val primaryColor = ContextCompat.getColor(activity, R.color.p_app_text_primary)
    val secondaryColor = ContextCompat.getColor(activity, R.color.p_app_text_secondary)
    val iconTint = ContextCompat.getColor(activity, R.color.p_app_icon_tint)
    val accentColor = activity.getAccentColor()
    
    val actualBg = if (activity.isAmoledTheme) Color.BLACK else bgColor
    
    val rootView = activity.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)
    val bgType = prefs.getString("bg_type", "default")
    
    var isDarkBg = true
    
    if (bgType == "color") {
        val customColor = prefs.getInt("bg_color", actualBg)
        rootView?.setBackgroundColor(customColor)
        
        val r = Color.red(customColor)
        val g = Color.green(customColor)
        val b = Color.blue(customColor)
        isDarkBg = ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) < 0.6
    } else if (bgType == "image") {
        val imageUriStr = prefs.getString("bg_image", null)
        var bgSet = false
        if (imageUriStr != null) {
            try {
                val uri = Uri.parse(imageUriStr)
                
                val optionsBounds = android.graphics.BitmapFactory.Options()
                optionsBounds.inJustDecodeBounds = true
                activity.contentResolver.openInputStream(uri)?.use { 
                    android.graphics.BitmapFactory.decodeStream(it, null, optionsBounds)
                }

                var scale = 1
                val screenWidth = activity.resources.displayMetrics.widthPixels
                val screenHeight = activity.resources.displayMetrics.heightPixels
                
                val maxDim = Math.max(optionsBounds.outWidth, optionsBounds.outHeight)
                val reqDim = Math.max(screenWidth, screenHeight)
                
                while (maxDim / scale / 2 >= reqDim) {
                    scale *= 2
                }

                val optionsDecode = android.graphics.BitmapFactory.Options()
                optionsDecode.inSampleSize = scale
                val bitmap = activity.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, optionsDecode)
                }

                if (bitmap != null) {
                    var rotationDegrees = 0f
                    try {
                        activity.contentResolver.openInputStream(uri)?.use { inputStream ->
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
                    
                    val drawable = android.graphics.drawable.BitmapDrawable(activity.resources, finalBitmap)
                    rootView?.background = drawable
                    bgSet = true
                    
                    val pBmp = android.graphics.Bitmap.createScaledBitmap(finalBitmap, 1, 1, true)
                    val avgColor = pBmp.getPixel(0, 0)
                    val r = Color.red(avgColor)
                    val g = Color.green(avgColor)
                    val b = Color.blue(avgColor)
                    isDarkBg = ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) < 0.6
                }
            } catch (e: Throwable) { 
            }
        }
        if (!bgSet) {
            rootView?.setBackgroundColor(actualBg)
            val r = Color.red(actualBg)
            val g = Color.green(actualBg)
            val b = Color.blue(actualBg)
            isDarkBg = ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) < 0.6
        }
    } else {
        rootView?.setBackgroundColor(actualBg)
        val r = Color.red(actualBg)
        val g = Color.green(actualBg)
        val b = Color.blue(actualBg)
        isDarkBg = ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) < 0.6
    }
    
    val adaptiveTextColor = if (isDarkBg) Color.WHITE else Color.BLACK
    prefs.edit().putInt("dynamic_text_color", adaptiveTextColor).apply()
    
    activity.findViewById<View>(R.id.searchContainer)?.setBackgroundColor(actualBg)
    
    activity.trashTopBar.setBackgroundColor(actualBg)
    activity.favoritesTopBar.setBackgroundColor(actualBg)
    activity.selectionTopBar.setBackgroundColor(actualBg)
    activity.selectionBottomBar.setBackgroundColor(actualBg)
    activity.bottomTabLayout.setBackgroundColor(actualBg)
    
    activity.tvSelectionCount.setTextColor(primaryColor)
    activity.findViewById<ImageView>(R.id.btnCloseSelection)?.setColorFilter(iconTint)
    
    activity.fastScrollThumb.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 50f
        setColor(accentColor)
    }
    
    activity.fastScrollBubbleContainer.setCardBackgroundColor(accentColor)
    
    val isAccentWhite = accentColor == Color.WHITE || accentColor == Color.parseColor("#FFFFFF")
    activity.fastScrollBubble.setTextColor(if (isAccentWhite) Color.BLACK else Color.WHITE)
    
    activity.findViewById<ImageView>(R.id.btnSearchBack)?.setColorFilter(iconTint)
    activity.findViewById<ImageView>(R.id.btnClearSearch)?.setColorFilter(iconTint)
    
    val btnSelectAllLayout = activity.findViewById<LinearLayout>(R.id.btnSelectAll)
    (btnSelectAllLayout?.getChildAt(0) as? TextView)?.setTextColor(primaryColor)
    
    activity.findViewById<ImageView>(R.id.ivShareIcon)?.setColorFilter(iconTint)
    activity.findViewById<TextView>(R.id.tvShareText)?.setTextColor(primaryColor)
    
    activity.findViewById<ImageView>(R.id.ivRestoreIcon)?.setColorFilter(Color.parseColor("#4CAF50"))
    activity.findViewById<TextView>(R.id.tvRestoreText)?.setTextColor(Color.parseColor("#4CAF50"))
    
    activity.findViewById<ImageView>(R.id.ivMoreIcon)?.setColorFilter(iconTint)
    activity.findViewById<TextView>(R.id.tvMoreText)?.setTextColor(primaryColor)
    
    activity.findViewById<View>(R.id.btnBackFromTrash)?.let {
        if (it is ViewGroup && it.childCount > 0) {
            (it.getChildAt(0) as? TextView)?.setTextColor(iconTint)
        }
    }
    
    activity.findViewById<View>(R.id.btnBackFromFavorites)?.let {
        if (it is ViewGroup && it.childCount > 0) {
            (it.getChildAt(0) as? TextView)?.setTextColor(iconTint)
        }
    }
    
    activity.findViewById<TextView>(R.id.tvTrashTitleCount)?.setTextColor(secondaryColor)
    activity.findViewById<TextView>(R.id.btnTrashEdit)?.setTextColor(primaryColor)
    activity.findViewById<ImageView>(R.id.btnTrashMore)?.setColorFilter(iconTint)
    
    (activity.emptyTrashView as? LinearLayout)?.let { layout ->
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is TextView) child.setTextColor(adaptiveTextColor)
        }
    }
    
    (activity.emptyFavoritesView as? LinearLayout)?.let { layout ->
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is TextView) child.setTextColor(adaptiveTextColor)
        }
    }
    
    (activity.emptySearchView as? LinearLayout)?.let { layout ->
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is TextView) child.setTextColor(adaptiveTextColor)
        }
    }
    
    val ivSelectAll = activity.findViewById<ImageView>(R.id.ivSelectAllIcon)
    if (activity.selectedMedia.size == MainActivity.displayedMediaList.size && MainActivity.displayedMediaList.isNotEmpty()) {
        ivSelectAll?.setImageDrawable(CheckCircleDrawable(accentColor))
        ivSelectAll?.imageTintList = null
    } else {
        ivSelectAll?.setImageResource(R.drawable.ic_check_circle_off)
        ivSelectAll?.imageTintList = android.content.res.ColorStateList.valueOf(iconTint)
    }
    
    activity.allRecycler.adapter?.notifyDataSetChanged()
    activity.albumsRecycler.adapter?.notifyDataSetChanged()
}

fun MainActivity.updateEmptyStateUI() {
    val activity = this
    val coordinatorLayout = activity.findViewById<View>(R.id.coordinatorLayout)
    
    if (activity.isShowingTrash) {
        coordinatorLayout?.visibility = View.GONE
        activity.albumsRecycler.visibility = View.GONE
        activity.fastScrollContainer.visibility = View.GONE
        activity.trashTopBar.visibility = View.VISIBLE
        activity.favoritesTopBar.visibility = View.GONE
        activity.emptyFavoritesView.visibility = View.GONE
        activity.emptySearchView.visibility = View.GONE
        
        if (!activity.isSelectionMode) {
            activity.bottomTabLayout.visibility = View.GONE
            activity.trashTopBar.post {
                activity.allRecycler.setPadding(0, activity.trashTopBar.height, 0, 0)
            }
        }
        
        val tvTrashTitleCount = activity.findViewById<TextView>(R.id.tvTrashTitleCount)
        val btnTrashEdit = activity.findViewById<View>(R.id.btnTrashEdit)
        val btnTrashMore = activity.findViewById<View>(R.id.btnTrashMore)

        if (MainActivity.trashList.isEmpty()) {
            tvTrashTitleCount?.visibility = View.GONE
            activity.emptyTrashView.visibility = View.VISIBLE
            activity.allRecycler.visibility = View.GONE
            btnTrashEdit?.visibility = View.GONE
            btnTrashMore?.visibility = View.GONE
        } else {
            tvTrashTitleCount?.visibility = View.VISIBLE
            tvTrashTitleCount?.text = "${MainActivity.trashList.size} görüntü"
            activity.emptyTrashView.visibility = View.GONE
            coordinatorLayout?.visibility = View.VISIBLE
            activity.allRecycler.visibility = View.VISIBLE
            btnTrashEdit?.visibility = View.VISIBLE
            btnTrashMore?.visibility = View.VISIBLE
        }
        
    } else if (activity.isShowingFavorites) {
        coordinatorLayout?.visibility = View.GONE
        activity.albumsRecycler.visibility = View.GONE
        activity.fastScrollContainer.visibility = View.GONE
        activity.trashTopBar.visibility = View.GONE
        activity.emptyTrashView.visibility = View.GONE
        activity.favoritesTopBar.visibility = View.VISIBLE
        activity.emptySearchView.visibility = View.GONE
        
        if (!activity.isSelectionMode) {
            activity.bottomTabLayout.visibility = View.GONE
            activity.favoritesTopBar.post {
                activity.allRecycler.setPadding(0, activity.favoritesTopBar.height, 0, 0)
            }
        }

        if (MainActivity.displayedMediaList.isEmpty()) {
            activity.emptyFavoritesView.visibility = View.VISIBLE
            activity.allRecycler.visibility = View.GONE
        } else {
            activity.emptyFavoritesView.visibility = View.GONE
            coordinatorLayout?.visibility = View.VISIBLE
            activity.allRecycler.visibility = View.VISIBLE
        }
        
    } else if (activity.isSearchMode) {
        activity.emptyTrashView.visibility = View.GONE
        activity.emptyFavoritesView.visibility = View.GONE
        
        if (!activity.isSelectionMode) {
            val sc = activity.findViewById<View>(R.id.searchContainer)
            sc?.post {
                activity.allRecycler.setPadding(0, sc.height, 0, 0)
            }
        }

        if (MainActivity.displayedMediaList.isEmpty()) {
            activity.emptySearchView.visibility = View.VISIBLE
            activity.allRecycler.visibility = View.GONE
        } else {
            activity.emptySearchView.visibility = View.GONE
            activity.allRecycler.visibility = View.VISIBLE
        }
        
    } else {
        coordinatorLayout?.visibility = View.VISIBLE
        
        activity.fastScrollContainer.visibility = if (activity.bottomTabLayout.selectedTabPosition == 2) {
            View.GONE 
        } else {
            View.VISIBLE
        }
        
        activity.trashTopBar.visibility = View.GONE
        activity.emptyTrashView.visibility = View.GONE
        activity.favoritesTopBar.visibility = View.GONE
        activity.emptyFavoritesView.visibility = View.GONE
        activity.emptySearchView.visibility = View.GONE
        
        if (!activity.isSelectionMode) {
            activity.bottomTabLayout.visibility = View.VISIBLE
            activity.allRecycler.setPadding(0, 0, 0, 0)
        }
    }
}

fun MainActivity.exitSelectionMode() { 
    val activity = this
    activity.isSelectionMode = false
    activity.selectedMedia.clear()
    activity.selectionTopBar.visibility = View.GONE
    activity.selectionBottomBar.visibility = View.GONE
    activity.updateEmptyStateUI()
    activity.allRecycler.adapter?.notifyDataSetChanged() 
}

fun MainActivity.updateSelectionUI() { 
    val activity = this
    activity.selectionTopBar.visibility = View.VISIBLE
    activity.selectionBottomBar.visibility = View.VISIBLE
    activity.bottomTabLayout.visibility = View.GONE
    activity.tvSelectionCount.text = "${activity.selectedMedia.size} seçili" 
    
    activity.selectionTopBar.post {
        activity.allRecycler.setPadding(0, activity.selectionTopBar.height, 0, 0)
    }
    
    val btnShare = activity.findViewById<View>(R.id.btnShare)
    val btnMore = activity.findViewById<View>(R.id.btnMore)
    val btnRestore = activity.findViewById<View>(R.id.btnRestore)
    val btnDelete = activity.findViewById<View>(R.id.btnDelete)
    
    val ivDeleteIcon = activity.findViewById<ImageView>(R.id.ivDeleteIcon)
    val tvDeleteText = activity.findViewById<TextView>(R.id.tvDeleteText)
    
    val iconTint = ContextCompat.getColor(activity, R.color.p_app_icon_tint)
    val primaryColor = ContextCompat.getColor(activity, R.color.p_app_text_primary)
    
    if (activity.isShowingTrash) {
        btnShare?.visibility = View.GONE
        btnMore?.visibility = View.GONE
        btnRestore?.visibility = View.VISIBLE
        
        ivDeleteIcon?.setImageResource(R.drawable.ic_fs_trash)
        ivDeleteIcon?.setColorFilter(Color.parseColor("#FF5252"))
        tvDeleteText?.setTextColor(Color.parseColor("#FF5252"))
        tvDeleteText?.text = "Kalıcı Sil"
    } else {
        btnShare?.visibility = View.VISIBLE
        btnMore?.visibility = View.VISIBLE
        btnRestore?.visibility = View.GONE
        
        ivDeleteIcon?.setImageResource(R.drawable.ic_action_delete)
        ivDeleteIcon?.setColorFilter(iconTint)
        tvDeleteText?.setTextColor(primaryColor)
        tvDeleteText?.text = "Sil"
    }

    val ivSelectAll = activity.findViewById<ImageView>(R.id.ivSelectAllIcon)
    if (activity.selectedMedia.size == MainActivity.displayedMediaList.size && MainActivity.displayedMediaList.isNotEmpty()) {
        ivSelectAll?.setImageDrawable(CheckCircleDrawable(activity.getAccentColor()))
        ivSelectAll?.imageTintList = null
    } else {
        ivSelectAll?.setImageResource(R.drawable.ic_check_circle_off)
        ivSelectAll?.imageTintList = android.content.res.ColorStateList.valueOf(iconTint)
    }
}

fun MainActivity.resetStates() { 
    val activity = this
    // NOKTA ATIŞI: Sekme geri yüklenirken state'lerin sıfırlanmasını (kıpraşmayı ve albümden atmayı) engeller.
    if (activity.bottomTabLayout.tag == "restoring") return
    
    activity.isShowingTrash = false
    activity.isShowingFavorites = false
    activity.isShowingPlaces = false
    activity.isShowingLocations = false
    activity.filterBucketId = null
    activity.filterLocation = null
    activity.closeSearchMode() 
}

fun MainActivity.updateTabAppearance(selectedPos: Int) {
    val activity = this
    val primaryColor = ContextCompat.getColor(activity, R.color.p_app_text_primary)
    
    for (i in 0 until activity.bottomTabLayout.tabCount) {
        val tv = activity.bottomTabLayout.getTabAt(i)?.customView as? TextView
        tv?.setTextColor(if (i == selectedPos) primaryColor else Color.parseColor("#888888"))
        tv?.setTypeface(null, if (i == selectedPos) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }
}

fun MainActivity.shareSelectedMedia() {
    val activity = this
    if (activity.selectedMedia.isEmpty()) return
    
    val uris = ArrayList<Uri>()
    var hasImage = false
    var hasVideo = false
    
    for (item in activity.selectedMedia) {
        uris.add(item.uri)
        if (item.isVideo) {
            hasVideo = true 
        } else {
            hasImage = true
        }
    }
    
    val mimeType = when {
        hasImage && !hasVideo -> "image/*"
        !hasImage && hasVideo -> "video/*"
        else -> "*/*"
    }
    
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND_MULTIPLE
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        type = mimeType
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    activity.startActivity(Intent.createChooser(shareIntent, "Paylaş"))
}

fun MainActivity.showMultiDeleteConfirmationDialog(items: List<MediaItem>) {
    val activity = this
    val useTrashPref = activity.getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE).getBoolean("useTrash", true)
    val actuallyUseTrash = useTrashPref && !activity.isShowingTrash
    val primaryColor = ContextCompat.getColor(activity, R.color.p_app_text_primary)
    val menuBgColor = activity.getMenuBgColor()
    
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
        if (it.isVideo) {
            videoCount++ 
        } else {
            photoCount++ 
        }
    }
    
    val itemsText = when {
        photoCount > 0 && videoCount > 0 -> "$photoCount fotoğraf ve $videoCount video"
        photoCount > 0 -> "$photoCount fotoğraf"
        videoCount > 0 -> "$videoCount video"
        else -> ""
    }
    
    messageView.text = if (actuallyUseTrash) {
        "$itemsText çöp kutusuna gönderilsin mi?" 
    } else {
        "$itemsText kalıcı olarak silinsin mi?"
    }
    
    val btnConfirm = view.findViewById<AppCompatButton>(R.id.btnConfirm)
    val parentLayout = btnConfirm.parent as? ViewGroup
    
    if (parentLayout is LinearLayout) {
        parentLayout.removeAllViews()
        parentLayout.gravity = Gravity.CENTER
        
        val dp10 = (10 * activity.resources.displayMetrics.density).toInt()
        val btnWidth = (110 * activity.resources.displayMetrics.density).toInt() 
        val btnHeight = (42 * activity.resources.displayMetrics.density).toInt()
        
        val btnCancelNew = AppCompatButton(activity).apply {
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
            setOnClickListener { 
                dialog.dismiss() 
            }
        }
        
        val btnConfirmNew = AppCompatButton(activity).apply {
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
                dialog.dismiss()
                activity.performMultiDelete(items, actuallyUseTrash)
            }
        }
        
        parentLayout.addView(btnCancelNew)
        parentLayout.addView(btnConfirmNew)
    }

    dialog.show()
}

fun MainActivity.updateGridSpanCount(count: Int) {
    val activity = this
    val lmAll = activity.allRecycler.layoutManager as? GridLayoutManager
    lmAll?.spanCount = count
    lmAll?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            return if (MainActivity.galleryItems.getOrNull(position) is HeaderItem) count else 1
        }
    }
    
    val lmAlbums = activity.albumsRecycler.layoutManager as? GridLayoutManager
    lmAlbums?.spanCount = count
    
    activity.allRecycler.adapter?.notifyDataSetChanged()
    activity.albumsRecycler.adapter?.notifyDataSetChanged()
}

fun MainActivity.updateAccentColor(color: Int) {
    val activity = this
    val fastScrollThumb = activity.findViewById<View>(R.id.fastScrollThumb)
    val fastScrollBubbleContainer = activity.findViewById<CardView>(R.id.fastScrollBubbleContainer)
    
    fastScrollThumb?.background = GradientDrawable().apply { 
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 50f
        setColor(color) 
    }
    
    fastScrollBubbleContainer?.setCardBackgroundColor(color)
    
    val isAccentWhite = color == Color.WHITE || color == Color.parseColor("#FFFFFF")
    activity.fastScrollBubble.setTextColor(if (isAccentWhite) Color.BLACK else Color.WHITE)
    
    val ivSelectAll = activity.findViewById<ImageView>(R.id.ivSelectAllIcon)
    if (activity.selectedMedia.size == MainActivity.displayedMediaList.size && MainActivity.displayedMediaList.isNotEmpty()) {
        ivSelectAll?.setImageDrawable(CheckCircleDrawable(color))
        ivSelectAll?.imageTintList = null
    }
    
    activity.allRecycler.adapter?.notifyDataSetChanged()
}

fun MainActivity.showCustomToast(context: Context, message: String, iconResId: Int) {
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
            layoutParams = LinearLayout.LayoutParams(56, 56).apply { 
                setMargins(0, 0, 24, 0) 
            }
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
