package com.sahin.galerim

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

class AllMediaAdapter(private val activity: MainActivity) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    override fun getItemViewType(pos: Int): Int {
        return if (MainActivity.galleryItems[pos] is HeaderItem) 0 else 1
    }
    
    override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
        return if (t == 0) {
            HeaderViewHolder(activity.layoutInflater.inflate(R.layout.item_gallery_header, p, false)) 
        } else {
            MediaViewHolder(activity.layoutInflater.inflate(R.layout.item_media, p, false))
        }
    }
    
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is MediaViewHolder) {
            if (holder.bindingAdapterPosition == activity.currentlyPlayingPosition) {
                activity.currentlyPlayingPosition = -1
                activity.releaseMediaPlayer()
            }
            holder.texture.visibility = View.GONE
            holder.thumbnail.visibility = View.VISIBLE
            
            val drawable = holder.thumbnail.drawable
            if (drawable is Animatable) {
                drawable.stop()
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        val item = MainActivity.galleryItems[pos]
        val accentColor = activity.getAccentColor()
        val iconTint = ContextCompat.getColor(activity, R.color.p_app_icon_tint)
        
        // Akıllı Luminance (Parlaklık) Analizinden Gelen Yazı Rengini Çekiyoruz
        val adaptiveTextColor = activity.getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
            .getInt("dynamic_text_color", ContextCompat.getColor(activity, R.color.p_app_text_primary))
        
        if (holder is HeaderViewHolder && item is HeaderItem) {
            holder.title.text = item.title
            holder.title.setTextColor(adaptiveTextColor) // UYARLANMIŞ RENK EKLENDİ
            
            if (item.location != null) {
                holder.location.text = item.location
                holder.location.setTextColor(adaptiveTextColor) // UYARLANMIŞ RENK EKLENDİ
                holder.location.visibility = View.VISIBLE
            } else {
                holder.location.visibility = View.GONE
            }
            
            val itemsUnderHeader = mutableListOf<MediaItem>()
            
            for (i in pos + 1 until MainActivity.galleryItems.size) {
                val nextItem = MainActivity.galleryItems[i]
                if (nextItem is HeaderItem) break
                if (nextItem is MediaContentItem) {
                    itemsUnderHeader.add(nextItem.media)
                }
            }

            holder.selectionCheck.visibility = if (activity.isSelectionMode) View.VISIBLE else View.GONE
            
            val allSelected = itemsUnderHeader.isNotEmpty() && activity.selectedMedia.containsAll(itemsUnderHeader)

            if (allSelected) {
                holder.selectionCheck.setImageDrawable(CheckCircleDrawable(accentColor))
                holder.selectionCheck.imageTintList = null
            } else {
                holder.selectionCheck.setImageResource(R.drawable.ic_check_circle_off)
                holder.selectionCheck.imageTintList = android.content.res.ColorStateList.valueOf(iconTint)
            }

            holder.itemView.setOnClickListener {
                if (itemsUnderHeader.isEmpty()) return@setOnClickListener

                if (!activity.isSelectionMode) {
                    activity.isSelectionMode = true
                    activity.currentlyPlayingPosition = -1
                    activity.releaseMediaPlayer()
                }

                if (allSelected) {
                    activity.selectedMedia.removeAll(itemsUnderHeader) 
                    if (activity.selectedMedia.isEmpty()) activity.exitSelectionMode()
                } else {
                    activity.selectedMedia.addAll(itemsUnderHeader) 
                }

                activity.updateSelectionUI()
                notifyDataSetChanged()
            }
            
            holder.itemView.setOnLongClickListener {
                if (!activity.isSelectionMode && itemsUnderHeader.isNotEmpty()) {
                    activity.isSelectionMode = true
                    activity.currentlyPlayingPosition = -1
                    activity.releaseMediaPlayer()
                    activity.selectedMedia.addAll(itemsUnderHeader)
                    activity.updateSelectionUI()
                    notifyDataSetChanged()
                }
                true
            }
        } else if (holder is MediaViewHolder && item is MediaContentItem) {
            val m = item.media
            holder.selectionOverlay.visibility = if (activity.isSelectionMode) View.VISIBLE else View.GONE
            holder.selectionCheck.visibility = if (activity.isSelectionMode) View.VISIBLE else View.GONE
            
            if (activity.isSelectionMode) { 
                holder.selectionOverlay.setBackgroundColor(
                    if (activity.selectedMedia.contains(m)) Color.parseColor("#88000000") else Color.TRANSPARENT
                ) 
                
                if (activity.selectedMedia.contains(m)) {
                    holder.selectionCheck.setImageDrawable(CheckCircleDrawable(accentColor))
                    holder.selectionCheck.imageTintList = null
                } else {
                    holder.selectionCheck.setImageResource(R.drawable.ic_check_circle_off)
                    holder.selectionCheck.imageTintList = android.content.res.ColorStateList.valueOf(iconTint)
                }
            }
            
            val tvDaysLeft = holder.itemView.findViewById<TextView>(R.id.tvDaysLeft)
            val daysLeftContainer = holder.itemView.findViewById<View>(R.id.daysLeftContainer)
            
            if (activity.isShowingTrash) {
                tvDaysLeft?.visibility = View.VISIBLE
                daysLeftContainer?.visibility = View.VISIBLE
                val trashedTime = MainActivity.trashedTimestamps[m.path] ?: System.currentTimeMillis()
                val daysPassed = (System.currentTimeMillis() - trashedTime) / (1000L * 60 * 60 * 24)
                val daysLeft = 30L - daysPassed
                tvDaysLeft?.text = "${maxOf(1L, daysLeft)} gün"
            } else {
                tvDaysLeft?.visibility = View.GONE
                daysLeftContainer?.visibility = View.GONE
            }

            val isAnim = isAnimated(m.path)
            val isUnsupported = isUnsupportedFormat(m.path)

            if (isUnsupported) {
                holder.panel.visibility = View.GONE
                holder.texture.visibility = View.GONE
                holder.texture.surfaceTextureListener = null
                holder.texture.alpha = 1f
                holder.thumbnail.visibility = View.VISIBLE
                Glide.with(activity).clear(holder.thumbnail)
                holder.thumbnail.setImageDrawable(activity.getPlaceholder())
            } else if (m.isVideo) {
                holder.panel.visibility = View.VISIBLE
                holder.dur.text = formatDuration(m.duration)
                
                if (!activity.isSelectionMode && pos == activity.currentlyPlayingPosition && activity.isActivityResumed) {
                    holder.texture.visibility = View.VISIBLE
                    holder.texture.alpha = 0f
                    holder.thumbnail.visibility = View.VISIBLE
                    
                    fun startPlayer(st: SurfaceTexture) {
                        try {
                            activity.releaseMediaPlayer()
                            if (activity.isActivityResumed && holder.bindingAdapterPosition == activity.currentlyPlayingPosition) {
                                val surface = Surface(st) 
                                activity.mediaPlayer = MediaPlayer().apply {
                                    setSurface(surface)
                                    setDataSource(activity, m.uri)
                                    setVolume(0f, 0f)
                                    isLooping = true
                                    setOnErrorListener { _, _, _ -> 
                                        activity.releaseMediaPlayer()
                                        true 
                                    }
                                    setOnInfoListener { _, what, _ ->
                                        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                                            holder.texture.post {
                                                if (holder.bindingAdapterPosition == activity.currentlyPlayingPosition) {
                                                    holder.texture.alpha = 1f
                                                    holder.thumbnail.visibility = View.INVISIBLE
                                                }
                                            }
                                        }
                                        true
                                    }
                                    setOnPreparedListener { mp ->
                                        if (!activity.isActivityResumed || holder.bindingAdapterPosition != activity.currentlyPlayingPosition) { 
                                            activity.releaseMediaPlayer()
                                            return@setOnPreparedListener 
                                        }
                                        
                                        val viewWidth = holder.texture.width.toFloat()
                                        val viewHeight = holder.texture.height.toFloat()
                                        val videoWidth = mp.videoWidth.toFloat()
                                        val videoHeight = mp.videoHeight.toFloat()
                                        
                                        if (viewWidth > 0 && viewHeight > 0 && videoWidth > 0 && videoHeight > 0) {
                                            val scaleX = viewWidth / videoWidth
                                            val scaleY = viewHeight / videoHeight
                                            val scale = maxOf(scaleX, scaleY)
                                            val scaledWidth = scale * videoWidth
                                            val scaledHeight = scale * videoHeight
                                            val pivotX = viewWidth / 2f
                                            val pivotY = viewHeight / 2f
                                            val matrix = Matrix()
                                            matrix.setScale(scaledWidth / viewWidth, scaledHeight / viewHeight, pivotX, pivotY)
                                            holder.texture.setTransform(matrix)
                                        }
                                        
                                        try { 
                                            mp.start() 
                                        } catch (e: Exception) { 
                                            activity.releaseMediaPlayer() 
                                        }
                                    }
                                    prepareAsync()
                                }
                            }
                        } catch (e: Exception) { 
                            activity.releaseMediaPlayer() 
                        }
                    }

                    if (holder.texture.isAvailable) {
                        startPlayer(holder.texture.surfaceTexture!!)
                    } else {
                        holder.texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, hi: Int) { 
                                if (holder.bindingAdapterPosition == activity.currentlyPlayingPosition) {
                                    startPlayer(st) 
                                }
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, hi: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { 
                                return true 
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                } else { 
                    holder.texture.visibility = View.GONE
                    holder.texture.surfaceTextureListener = null 
                    holder.texture.alpha = 1f
                    holder.thumbnail.visibility = View.VISIBLE
                    Glide.with(activity).asBitmap().load(m.uri).error(activity.getPlaceholder()).centerCrop().into(holder.thumbnail) 
                }
            } else if (isAnim) {
                holder.panel.visibility = View.GONE
                holder.texture.visibility = View.GONE
                holder.thumbnail.visibility = View.VISIBLE
                
                if (!activity.isSelectionMode && pos == activity.currentlyPlayingPosition && activity.isActivityResumed) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        Glide.with(activity).clear(holder.thumbnail)
                        activity.lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val source = ImageDecoder.createSource(activity.contentResolver, m.uri)
                                val drawable = ImageDecoder.decodeDrawable(source)
                                withContext(Dispatchers.Main) {
                                    if (holder.bindingAdapterPosition == pos) {
                                        holder.thumbnail.setImageDrawable(drawable)
                                        if (drawable is Animatable) {
                                            drawable.start()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    if (holder.bindingAdapterPosition == pos) {
                                        Glide.with(activity).load(m.uri).error(activity.getPlaceholder()).centerCrop().into(holder.thumbnail)
                                    }
                                }
                            }
                        }
                    } else {
                        Glide.with(activity).load(m.uri).error(activity.getPlaceholder()).centerCrop().into(holder.thumbnail)
                    }
                } else {
                    val currentDrawable = holder.thumbnail.drawable
                    if (currentDrawable is Animatable) {
                        currentDrawable.stop()
                    }
                    Glide.with(activity).asBitmap().load(m.uri).error(activity.getPlaceholder()).centerCrop().into(holder.thumbnail)
                }
            } else { 
                holder.panel.visibility = View.GONE
                holder.texture.visibility = View.GONE
                holder.texture.surfaceTextureListener = null
                holder.texture.alpha = 1f
                holder.thumbnail.visibility = View.VISIBLE
                Glide.with(activity).asBitmap().load(m.uri).error(activity.getPlaceholder()).centerCrop().into(holder.thumbnail) 
            }
            
            holder.itemView.setOnLongClickListener { 
                if (!activity.isSelectionMode) { 
                    activity.isSelectionMode = true
                    activity.selectedMedia.add(m)
                    activity.currentlyPlayingPosition = -1
                    activity.releaseMediaPlayer()
                    activity.updateSelectionUI()
                    notifyDataSetChanged() 
                }
                true 
            }
            
            holder.itemView.setOnClickListener { 
                if (activity.isSelectionMode) { 
                    if (activity.selectedMedia.contains(m)) { 
                        activity.selectedMedia.remove(m)
                        if (activity.selectedMedia.isEmpty()) {
                            activity.exitSelectionMode() 
                        } else { 
                            activity.updateSelectionUI()
                            notifyItemChanged(pos) 
                            for (i in pos downTo 0) {
                                if (MainActivity.galleryItems[i] is HeaderItem) {
                                    notifyItemChanged(i)
                                    break
                                }
                            }
                        } 
                    } else { 
                        activity.selectedMedia.add(m)
                        activity.updateSelectionUI()
                        notifyItemChanged(pos) 
                        for (i in pos downTo 0) {
                            if (MainActivity.galleryItems[i] is HeaderItem) {
                                notifyItemChanged(i)
                                break
                            }
                        }
                    } 
                } else { 
                    val intent = Intent(activity, if (activity.isShowingTrash) TrashFullScreenActivity::class.java else FullScreenActivity::class.java)
                    intent.putExtra("position", MainActivity.displayedMediaList.indexOf(m))
                    activity.startActivity(intent) 
                }
            }
        }
    }
    
    override fun getItemCount(): Int = MainActivity.galleryItems.size
    
    inner class HeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) { 
        val title: TextView = v.findViewById(R.id.headerTitle) 
        val location: TextView = v.findViewById(R.id.headerLocation)
        val selectionCheck: ImageView = v.findViewById(R.id.headerSelectionCheck) 
    }
    
    inner class MediaViewHolder(v: View) : RecyclerView.ViewHolder(v) { 
        val thumbnail: ImageView = v.findViewById(R.id.mediaThumbnail)
        val texture: TextureView = v.findViewById(R.id.mediaTextureView)
        val panel: LinearLayout = v.findViewById(R.id.videoInfoPanel)
        val dur: TextView = v.findViewById(R.id.videoDuration)
        val selectionOverlay: View = v.findViewById(R.id.selectionOverlay)
        val selectionCheck: ImageView = v.findViewById(R.id.selectionCheck) 
    }
}

class AlbumsAdapter(private val activity: MainActivity) : RecyclerView.Adapter<AlbumsAdapter.ViewHolder>() {
    
    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) { 
        val thumb: ImageView = v.findViewById(R.id.thumbnail)
        val name: TextView = v.findViewById(R.id.albumName) 
    }
    
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(
        activity.layoutInflater.inflate(R.layout.item_album, p, false)
    )
    
    override fun onBindViewHolder(h: ViewHolder, pos: Int) { 
        val a = activity.albumList[pos]
        
        // Akıllı Luminance (Parlaklık) Analizinden Gelen Yazı Rengini Çekiyoruz
        val adaptiveTextColor = activity.getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
            .getInt("dynamic_text_color", ContextCompat.getColor(activity, R.color.p_app_text_primary))

        Glide.with(activity)
            .asBitmap()
            .load(a.thumbnail)
            .error(activity.getPlaceholder())
            .centerCrop()
            .into(h.thumb)
        
        h.name.text = "${a.name}\n${a.count}"
        h.name.setTextColor(adaptiveTextColor) // UYARLANMIŞ RENK EKLENDİ
        
        h.itemView.setOnClickListener { 
            activity.albumsRecycler.stopScroll()
            activity.allRecycler.stopScroll()
            
            if (a.locationName != null) {
                activity.filterLocation = a.locationName
                activity.loadDisplayedList()
                activity.albumsRecycler.visibility = View.GONE
                activity.coordinatorLayout.visibility = View.VISIBLE
                activity.allRecycler.visibility = View.VISIBLE
            } else if (a.bucketId != null) {
                activity.filterBucketId = a.bucketId
                activity.loadDisplayedList()
                activity.albumsRecycler.visibility = View.GONE
                activity.coordinatorLayout.visibility = View.VISIBLE
                activity.allRecycler.visibility = View.VISIBLE
            }
        } 
    }
    
    override fun getItemCount() = activity.albumList.size
}