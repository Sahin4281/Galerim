package com.sahin.galerim

import android.Manifest
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
    
    val options = listOf("Ayrıntılar", favoriteAction, "Gizle", "Albüme kopyala", "Albüme taşı", "Tarih ve saati düzenle", "Konumu düzenle")
    
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
                        if (msg.isNotEmpty()) showCustomToast(activity, msg, 0)
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
                        if (msg.isNotEmpty()) showCustomToast(activity, msg, 0)
                        if (isShowingFavorites) loadDisplayedList()
                    }
                    "Gizle" -> { 
                        performHideMedia(selectedMedia.toList()) 
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
    
    val layout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(actualBg)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    
    val topBar = android.widget.RelativeLayout(activity).apply {
        setPadding(40, 40, 40, 40)
        setBackgroundColor(actualBg)
    }
    
    val btnClose = ImageView(activity).apply {
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
    layout.addView(topBar)
    
    var selectedLat: Double? = null
    var selectedLng: Double? = null
    
    val webView = android.webkit.WebView(activity).apply {
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
                post { Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show() }
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
    
    val bottomBar = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(actualBg)
    }
    
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
            if (selectedLat != null && selectedLng != null) {
                dialog.dismiss()
                updateLocationData(items, selectedLat!!, selectedLng!!)
            } else {
                Toast.makeText(activity, "Lütfen haritadan bir konum seçin", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    bottomBar.addView(btnCancel)
    bottomBar.addView(btnSave)
    layout.addView(bottomBar)

    dialog.setContentView(layout)
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
