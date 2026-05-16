package com.sahin.galerim

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class InteractiveMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var isAmoledTheme = false
    private val mediaWithLocation = mutableListOf<Pair<LatLng, MediaItem>>()
    private var loadingDialog: android.app.Dialog? = null
    private val thumbnailCache = LruCache<String, android.graphics.Bitmap>(50)

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val currentTheme = prefs.getString("appTheme", "Sistem Teması")
        isAmoledTheme = currentTheme == "Koyu Amoled Tema"
        when (currentTheme) {
            "Açık Tema" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Koyu Tema", "Koyu Amoled Tema" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_interactive_map)
        supportActionBar?.hide()

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        applyDynamicColors()
        showModernLoadingDialog()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun showModernLoadingDialog() {
        val dialogBgColor = ContextCompat.getColor(this, R.color.p_app_dialog_bg)
        val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
        val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val accentColorStr = prefs.getString("accentColor", "#5C94FF") ?: "#5C94FF"
        val accentColor = Color.parseColor(accentColorStr)
        
        val density = resources.displayMetrics.density
        val padHorizontal = (12 * density).toInt()
        val padVertical = (12 * density).toInt()
        val cornerRad = 16f * density
        val progSize = (0 * density).toInt()
        val progMargin = (12 * density).toInt()
        val dummySpaceWidth = 0

        loadingDialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(false)
            
            val layout = LinearLayout(this@InteractiveMapActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padHorizontal, padVertical, padHorizontal, padVertical)
                background = GradientDrawable().apply {
                    setColor(if (isAmoledTheme) Color.BLACK else dialogBgColor)
                    cornerRadius = cornerRad
                }
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val progressBar = ProgressBar(this@InteractiveMapActivity).apply {
                indeterminateTintList = android.content.res.ColorStateList.valueOf(accentColor)
                layoutParams = LinearLayout.LayoutParams(progSize, progSize).apply {
                    marginEnd = progMargin
                }
            }

            val textView = TextView(this@InteractiveMapActivity).apply {
                text = "Konum işleniyor, lütfen bekleyin..."
                setTextColor(primaryColor)
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val dummySpace = View(this@InteractiveMapActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dummySpaceWidth, 0)
            }

            layout.addView(progressBar)
            layout.addView(textView)
            layout.addView(dummySpace)

            setContentView(layout)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        loadingDialog?.show()
    }

    private fun applyDynamicColors() {
        val bgColor = ContextCompat.getColor(this, R.color.p_app_background)
        val actualBg = if (isAmoledTheme) Color.BLACK else bgColor
        findViewById<View>(R.id.rootLayout).setBackgroundColor(actualBg)
        findViewById<LinearLayout>(R.id.topBar).setBackgroundColor(actualBg)
        
        val r = Color.red(actualBg)
        val g = Color.green(actualBg)
        val b = Color.blue(actualBg)
        val isDarkBg = ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) < 0.6
        val adaptiveTextColor = if (isDarkBg) Color.WHITE else Color.BLACK
        
        findViewById<TextView>(R.id.tvTitle).setTextColor(adaptiveTextColor)
        findViewById<ImageView>(R.id.btnBack).setColorFilter(adaptiveTextColor)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        setupCustomInfoWindow()
        setupInfoWindowClickListener()
        loadLocations()
    }

    private fun setupInfoWindowClickListener() {
        mMap.setOnInfoWindowClickListener { marker ->
            val item = marker.tag as? MediaItem ?: return@setOnInfoWindowClickListener
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_album_id", item.bucketId)
                putExtra("open_album_name", item.bucketName)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun setupCustomInfoWindow() {
        mMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? = null

            override fun getInfoContents(marker: Marker): View? {
                val item = marker.tag as? MediaItem ?: return null
                val context = this@InteractiveMapActivity
                val density = context.resources.displayMetrics.density

                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                    background = GradientDrawable().apply {
                        setColor(if (isAmoledTheme) Color.BLACK else ContextCompat.getColor(context, R.color.p_app_dialog_bg))
                        cornerRadius = 8f * density
                    }
                }

                val imageView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((60 * density).toInt(), (60 * density).toInt()).apply {
                        marginEnd = (10 * density).toInt()
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }

                val textView = TextView(context).apply {
                    text = item.bucketName
                    setTextColor(ContextCompat.getColor(context, R.color.p_app_text_primary))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                container.addView(imageView)
                container.addView(textView)

                val cacheKey = item.path
                val cachedBitmap = thumbnailCache.get(cacheKey)
                if (cachedBitmap != null) {
                    imageView.setImageBitmap(cachedBitmap)
                } else {
                    imageView.setImageResource(android.R.color.darker_gray)
                    lifecycleScope.launch(Dispatchers.IO) {
                        val bitmap = try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                context.contentResolver.loadThumbnail(item.uri, android.util.Size(120, 120), null)
                            } else {
                                if (item.isVideo) {
                                    android.media.ThumbnailUtils.createVideoThumbnail(item.path, android.provider.MediaStore.Video.Thumbnails.MINI_KIND)
                                } else {
                                    val options = android.graphics.BitmapFactory.Options().apply {
                                        inSampleSize = 4
                                    }
                                    android.graphics.BitmapFactory.decodeFile(item.path, options)
                                }
                            }
                        } catch (e: Exception) {
                            null
                        }

                        if (bitmap != null) {
                            thumbnailCache.put(cacheKey, bitmap)
                            withContext(Dispatchers.Main) {
                                if (marker.isInfoWindowShown) {
                                    marker.showInfoWindow()
                                }
                            }
                        }
                    }
                }
                return container
            }
        })
    }

    private fun loadLocations() {
        lifecycleScope.launch(Dispatchers.IO) {
            val allMedia = MainActivity.mediaList.toList()
            
            for (item in allMedia) {
                var lat: Double? = null
                var lng: Double? = null
                
                try {
                    if (item.isVideo) {
                        val r = MediaMetadataRetriever()
                        r.setDataSource(applicationContext, item.uri)
                        val m = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                        if (m != null) {
                            val mt = Pattern.compile("([+-][0-9.]+)([+-][0-9.]+)").matcher(m)
                            if (mt.find()) {
                                lat = mt.group(1)?.toDoubleOrNull()
                                lng = mt.group(2)?.toDoubleOrNull()
                            }
                        }
                        r.release()
                    } else {
                        val ex = ExifInterface(item.path)
                        val ll = FloatArray(2)
                        if (ex.getLatLong(ll)) {
                            lat = ll[0].toDouble()
                            lng = ll[1].toDouble()
                        }
                    }
                } catch (e: Exception) {}
                
                if (lat != null && lng != null) {
                    mediaWithLocation.add(Pair(LatLng(lat, lng), item))
                }
            }
            
            withContext(Dispatchers.Main) {
                loadingDialog?.dismiss()
                
                if (mediaWithLocation.isNotEmpty()) {
                    mediaWithLocation.forEach { (latLng, item) ->
                        mMap.addMarker(MarkerOptions().position(latLng).title(item.bucketName))?.tag = item
                    }
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mediaWithLocation.first().first, 5f))
                }
            }
        }
    }
}
