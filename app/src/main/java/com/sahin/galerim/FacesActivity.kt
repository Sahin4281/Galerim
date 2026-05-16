package com.sahin.galerim

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FacesActivity : AppCompatActivity() {

    private lateinit var recyclerFaces: RecyclerView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var rootLayout: View
    private lateinit var topBar: LinearLayout
    
    private var isAmoledTheme = false
    private val facesList = mutableListOf<FaceGroup>()

    data class FaceGroup(val name: String, val faceBitmap: Bitmap, val sourceUri: Uri)

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
        setContentView(R.layout.activity_faces)
        supportActionBar?.hide()

        rootLayout = findViewById(R.id.rootLayout)
        topBar = findViewById(R.id.topBar)
        recyclerFaces = findViewById(R.id.recyclerFaces)
        loadingLayout = findViewById(R.id.loadingLayout)
        tvProgress = findViewById(R.id.tvProgress)
        progressBar = findViewById(R.id.progressBar)
        btnBack = findViewById(R.id.btnBack)
        tvTitle = findViewById(R.id.tvTitle)

        applyDynamicColors()

        btnBack.setOnClickListener { finish() }

        recyclerFaces.layoutManager = GridLayoutManager(this, 3)
        recyclerFaces.adapter = FacesAdapter()

        scanFaces()
    }

    private fun applyDynamicColors() {
        val bgColor = ContextCompat.getColor(this, R.color.p_app_background)
        val actualBg = if (isAmoledTheme) Color.BLACK else bgColor
        rootLayout.setBackgroundColor(actualBg)
        topBar.setBackgroundColor(actualBg)
        
        val r = Color.red(actualBg)
        val g = Color.green(actualBg)
        val b = Color.blue(actualBg)
        val isDarkBg = ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) < 0.6
        val adaptiveTextColor = if (isDarkBg) Color.WHITE else Color.BLACK
        
        tvTitle.setTextColor(adaptiveTextColor)
        btnBack.setColorFilter(adaptiveTextColor)
        tvProgress.setTextColor(adaptiveTextColor)
        progressBar.indeterminateTintList = android.content.res.ColorStateList.valueOf(adaptiveTextColor)
    }

    private fun scanFaces() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
            
        val detector = FaceDetection.getClient(options)
        
        lifecycleScope.launch(Dispatchers.IO) {
            val photos = MainActivity.mediaList.filter { !it.isVideo }.take(100) 
            val totalPhotos = photos.size
            var processed = 0
            val tempFaces = mutableListOf<FaceGroup>()
            var personCounter = 1

            for (item in photos) {
                try {
                    val bitmap = getBitmapFromUri(item.uri)
                    if (bitmap != null) {
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val faces = detector.process(image).await()
                        
                        for (face in faces) {
                            val bounds = face.boundingBox
                            try {
                                val faceX = Math.max(0, bounds.left)
                                val faceY = Math.max(0, bounds.top)
                                val faceWidth = Math.min(bitmap.width - faceX, bounds.width())
                                val faceHeight = Math.min(bitmap.height - faceY, bounds.height())
                                
                                if (faceWidth > 0 && faceHeight > 0) {
                                    val croppedFace = Bitmap.createBitmap(bitmap, faceX, faceY, faceWidth, faceHeight)
                                    tempFaces.add(FaceGroup("Kişi $personCounter", croppedFace, item.uri))
                                    personCounter++
                                }
                            } catch (e: Exception) {}
                        }
                    }
                } catch (e: Exception) {}
                
                processed++
                withContext(Dispatchers.Main) {
                    tvProgress.text = "Fotoğraflar taranıyor... ($processed / $totalPhotos)\n(Bu işlem biraz sürebilir)"
                }
            }
            
            withContext(Dispatchers.Main) {
                facesList.clear()
                facesList.addAll(tempFaces)
                
                loadingLayout.visibility = View.GONE
                
                if (facesList.isEmpty()) {
                    loadingLayout.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    tvProgress.text = "Fotoğraflarda hiç yüz bulunamadı."
                } else {
                    recyclerFaces.visibility = View.VISIBLE
                    recyclerFaces.adapter?.notifyDataSetChanged()
                }
            }
        }
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    inner class FacesAdapter : RecyclerView.Adapter<FacesAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivFaceThumbnail: ImageView = view.findViewById(R.id.ivFaceThumbnail)
            val tvFaceName: TextView = view.findViewById(R.id.tvFaceName)
            val tvFaceCount: TextView = view.findViewById(R.id.tvFaceCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_face_group, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val faceGroup = facesList[position]
            
            Glide.with(this@FacesActivity)
                .load(faceGroup.faceBitmap)
                .centerCrop()
                .into(holder.ivFaceThumbnail)
                
            holder.tvFaceName.text = faceGroup.name
            holder.tvFaceCount.text = "1 fotoğraf"
            
            holder.itemView.setOnClickListener {
                val item = MainActivity.mediaList.find { it.uri == faceGroup.sourceUri }
                if (item != null) {
                    MainActivity.displayedMediaList.clear()
                    MainActivity.displayedMediaList.add(item)
                    
                    val intent = Intent(this@FacesActivity, FullScreenActivity::class.java)
                    intent.putExtra("position", 0)
                    intent.putExtra("isSlideshow", false)
                    startActivity(intent)
                }
            }
        }

        override fun getItemCount(): Int = facesList.size
    }
}
