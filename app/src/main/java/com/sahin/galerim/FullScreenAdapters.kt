package com.sahin.galerim

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView

class FsDialogAlbumAdapter(private val activity: FullScreenActivity, private val albums: List<Album>, private val onClick: (Album) -> Unit) : RecyclerView.Adapter<FsDialogAlbumAdapter.ViewHolder>() {
    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.thumbnail)
        val name: TextView = v.findViewById(R.id.albumName)
        init { v.setOnClickListener { onClick(albums[bindingAdapterPosition]) } }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_album, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val a = albums[position]
        Glide.with(activity).asBitmap().load(a.thumbnail).centerCrop().into(holder.thumb)
        holder.name.text = "${a.name}\n${a.count}"
        holder.name.setTextColor(ContextCompat.getColor(activity, R.color.p_app_text_primary))
    }
    override fun getItemCount() = albums.size
}

class FsFilmstripAdapter(private val activity: FullScreenActivity, private val list: List<MediaItem>) : RecyclerView.Adapter<FsFilmstripAdapter.ViewHolder>() {
    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) { val img: ImageView = v.findViewById(R.id.filmstripImage) }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_filmstrip, p, false))
    override fun onBindViewHolder(h: ViewHolder, p: Int) {
        Glide.with(activity).load(list[p].uri).centerCrop().into(h.img)
        h.img.alpha = if (p == activity.currentPosition) 1f else 0.5f
        h.itemView.setOnClickListener { activity.viewPager.setCurrentItem(p, true) }
    }
    override fun getItemCount() = list.size
}

class FsFullScreenAdapter(private val activity: FullScreenActivity, private val list: List<MediaItem>) : RecyclerView.Adapter<FsFullScreenAdapter.ViewHolder>() {
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val photoView: PhotoView = view.findViewById(R.id.fullImage)
        val videoContainer: RelativeLayout = view.findViewById(R.id.videoContainer)
        val videoView: VideoView = view.findViewById(R.id.fullVideo)
        val videoThumbnail: ImageView = view.findViewById(R.id.videoThumbnail)
        val controlsLayout: View = view.findViewById(R.id.videoControlsLayout)
        val videoSeekBar: SeekBar = view.findViewById(R.id.videoSeekBar)
        val btnBottomPlayPause: ImageButton = view.findViewById(R.id.btnBottomPlayPause)
        val btnPrev: ImageButton = view.findViewById(R.id.btnPrev)
        val btnNext: ImageButton = view.findViewById(R.id.btnNext)
        val tvCurrentTime: TextView = view.findViewById(R.id.tvCurrentTime)
        val tvTotalTime: TextView = view.findViewById(R.id.tvTotalTime)
        val tvSeekPreview: TextView = view.findViewById(R.id.tvSeekPreview)
        val tvSwipeFeedback: TextView = view.findViewById(R.id.tvSwipeFeedback)
        val btnMuteToggle: ImageButton = view.findViewById(R.id.btnMuteToggle)
        var mediaPlayerRef: MediaPlayer? = null
        var lastSeekTime = 0L 
        
        var scaleFactor = 1f
        lateinit var scaleGestureDetector: ScaleGestureDetector
        var isDragging = false
        var activePointerId = -1
        var lastRawX = 0f
        var lastRawY = 0f
        
        var swipeStartX = 0f
        var swipeStartY = 0f

        var isVolumeSwipe = false
        var isBrightnessSwipe = false
        var startVolume = 0
        var startBrightness = 0f
        
        init {
            tvSeekPreview.background = GradientDrawable().apply { 
                setColor(Color.parseColor("#B3000000"))
                cornerRadius = 20f 
            }
            tvSwipeFeedback.background = GradientDrawable().apply {
                setColor(Color.parseColor("#B3000000"))
                cornerRadius = 30f
            }
        }
        
        fun clampTranslation() {
            if (scaleFactor <= 1.0f) {
                videoView.translationX = 0f
                videoView.translationY = 0f
                videoThumbnail.translationX = 0f
                videoThumbnail.translationY = 0f
                return
            }

            val view = videoView
            val parent = videoContainer
            if (view.width == 0 || view.height == 0) return

            val maxTransX = ((view.width * scaleFactor - parent.width) / 2f).coerceAtLeast(0f)
            val maxTransY = ((view.height * scaleFactor - parent.height) / 2f).coerceAtLeast(0f)

            val clampedX = view.translationX.coerceIn(-maxTransX, maxTransX)
            val clampedY = view.translationY.coerceIn(-maxTransY, maxTransY)

            videoView.translationX = clampedX
            videoView.translationY = clampedY
            videoThumbnail.translationX = clampedX
            videoThumbnail.translationY = clampedY
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = 
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_fullscreen, parent, false))
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        if (item.isVideo) {
            holder.photoView.visibility = View.GONE
            holder.videoContainer.visibility = View.VISIBLE
            
            holder.controlsLayout.visibility = if (activity.isUiHidden) View.GONE else View.VISIBLE
            holder.controlsLayout.alpha = if (activity.isUiHidden) 0f else 1f

            holder.videoThumbnail.scaleType = ImageView.ScaleType.FIT_CENTER
            Glide.with(holder.itemView.context).load(item.uri).into(holder.videoThumbnail)
            
            val uriString = item.uri.toString()
            if (holder.videoView.tag != uriString) {
                holder.videoView.stopPlayback()
                holder.videoView.tag = uriString
                holder.videoView.setVideoURI(item.uri)
            }

            holder.videoView.setOnErrorListener { _, _, _ -> true }

            holder.videoView.setOnPreparedListener { mp: MediaPlayer? ->
                holder.mediaPlayerRef = mp
                holder.tvCurrentTime.text = "00:00"
                holder.tvTotalTime.text = formatFsTime(mp?.duration ?: 0)
                holder.videoSeekBar.max = mp?.duration ?: 0
                
                if (activity.isGlobalMuted) mp?.setVolume(0f, 0f) else mp?.setVolume(1f, 1f)
                holder.btnMuteToggle.setImageResource(if (activity.isGlobalMuted) R.drawable.ic_modern_mute else R.drawable.ic_modern_unmute)

                if (holder.bindingAdapterPosition == activity.currentPosition) {
                    val autoPlay = holder.itemView.context.getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE).getBoolean("autoPlayVideos", false)
                    if (autoPlay || activity.isSlideshowActive) {
                        holder.videoView.start()
                        holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_pause)
                        activity.startProgressUpdater(holder)
                        holder.videoView.postDelayed({
                            holder.videoThumbnail.visibility = View.GONE
                        }, 250)
                    } else {
                        holder.videoThumbnail.visibility = View.VISIBLE
                        holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
                    }
                } else {
                    holder.videoThumbnail.visibility = View.VISIBLE
                    holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
                }
            }
            
            holder.scaleGestureDetector = ScaleGestureDetector(holder.itemView.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    activity.viewPager.isUserInputEnabled = false
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val oldScale = holder.scaleFactor
                    holder.scaleFactor *= detector.scaleFactor
                    holder.scaleFactor = holder.scaleFactor.coerceIn(1.0f, 5.0f)

                    val scaleRatio = holder.scaleFactor / oldScale

                    val view = holder.videoView
                    val focusX = detector.focusX - (view.left + view.width / 2f)
                    val focusY = detector.focusY - (view.top + view.height / 2f)

                    val dx = focusX * (1 - scaleRatio)
                    val dy = focusY * (1 - scaleRatio)

                    holder.videoView.translationX += dx
                    holder.videoView.translationY += dy
                    holder.videoThumbnail.translationX += dx
                    holder.videoThumbnail.translationY += dy

                    holder.videoView.scaleX = holder.scaleFactor
                    holder.videoView.scaleY = holder.scaleFactor
                    holder.videoThumbnail.scaleX = holder.scaleFactor
                    holder.videoThumbnail.scaleY = holder.scaleFactor

                    holder.clampTranslation()
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    holder.clampTranslation()
                    if (holder.scaleFactor <= 1.0f) {
                        activity.viewPager.isUserInputEnabled = true
                    } else {
                        activity.viewPager.isUserInputEnabled = false
                    }
                }
            })

            holder.videoContainer.setOnTouchListener { _, event ->
                holder.scaleGestureDetector.onTouchEvent(event)
                
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        holder.activePointerId = event.getPointerId(0)
                        holder.lastRawX = event.x
                        holder.lastRawY = event.y
                        holder.swipeStartX = event.rawX
                        holder.swipeStartY = event.rawY
                        holder.isDragging = false
                        holder.isVolumeSwipe = false
                        holder.isBrightnessSwipe = false

                        val audioManager = holder.itemView.context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                        holder.startVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                        
                        val window = activity.window
                        holder.startBrightness = window.attributes.screenBrightness
                        if (holder.startBrightness < 0) {
                            try {
                                holder.startBrightness = android.provider.Settings.System.getInt(activity.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
                            } catch (e: Exception) {
                                holder.startBrightness = 0.5f
                            }
                        }

                        holder.tvSwipeFeedback.animate().cancel()
                        holder.tvSwipeFeedback.visibility = View.GONE
                        holder.tvSwipeFeedback.alpha = 1f
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        val actionIndex = event.actionIndex
                        holder.activePointerId = event.getPointerId(actionIndex)
                        holder.lastRawX = event.getX(actionIndex)
                        holder.lastRawY = event.getY(actionIndex)
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        val actionIndex = event.actionIndex
                        if (event.getPointerId(actionIndex) == holder.activePointerId) {
                            val newIndex = if (actionIndex == 0) 1 else 0
                            if (newIndex < event.pointerCount) {
                                holder.activePointerId = event.getPointerId(newIndex)
                                holder.lastRawX = event.getX(newIndex)
                                holder.lastRawY = event.getY(newIndex)
                            }
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val pointerIndex = event.findPointerIndex(holder.activePointerId)
                        if (pointerIndex != -1) {
                            val x = event.getX(pointerIndex)
                            val y = event.getY(pointerIndex)
                            
                            if (event.pointerCount == 1 && !holder.scaleGestureDetector.isInProgress) {
                                val deltaY = holder.swipeStartY - event.rawY
                                val deltaX = event.rawX - holder.swipeStartX

                                if (holder.scaleFactor <= 1.0f) {
                                    val screenWidth = holder.videoContainer.width
                                    val isLeftEdge = holder.swipeStartX < screenWidth * 0.3f
                                    val isRightEdge = holder.swipeStartX > screenWidth * 0.7f

                                    if (!holder.isVolumeSwipe && !holder.isBrightnessSwipe && !holder.isDragging) {
                                        if (Math.abs(deltaY) > 40 && Math.abs(deltaY) > Math.abs(deltaX)) {
                                            if (isRightEdge) {
                                                holder.isVolumeSwipe = true
                                                holder.videoContainer.parent.requestDisallowInterceptTouchEvent(true)
                                            } else if (isLeftEdge) {
                                                holder.isBrightnessSwipe = true
                                                holder.videoContainer.parent.requestDisallowInterceptTouchEvent(true)
                                            }
                                        }
                                    }

                                    if (holder.isVolumeSwipe) {
                                        val audioManager = holder.itemView.context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                                        val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                        val diff = (deltaY / holder.videoContainer.height) * maxVolume * 2
                                        var newVolume = holder.startVolume + diff.toInt()
                                        newVolume = newVolume.coerceIn(0, maxVolume)
                                        
                                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVolume, 0)
                                        
                                        val percent = ((newVolume.toFloat() / maxVolume) * 100).toInt()
                                        holder.tvSwipeFeedback.text = "Ses: %$percent"
                                        holder.tvSwipeFeedback.visibility = View.VISIBLE
                                    } else if (holder.isBrightnessSwipe) {
                                        val diff = (deltaY / holder.videoContainer.height) * 2f
                                        var newBrightness = holder.startBrightness + diff
                                        newBrightness = newBrightness.coerceIn(0.01f, 1f)
                                        val layoutParams = activity.window.attributes
                                        layoutParams.screenBrightness = newBrightness
                                        activity.window.attributes = layoutParams
                                        
                                        val percent = (newBrightness * 100).toInt()
                                        holder.tvSwipeFeedback.text = "Parlaklık: %$percent"
                                        holder.tvSwipeFeedback.visibility = View.VISIBLE
                                    }
                                }

                                if (holder.scaleFactor > 1.0f) {
                                    val dx = x - holder.lastRawX
                                    val dy = y - holder.lastRawY
                                    
                                    holder.videoView.translationX += dx
                                    holder.videoView.translationY += dy
                                    holder.videoThumbnail.translationX += dx
                                    holder.videoThumbnail.translationY += dy
                                    
                                    holder.clampTranslation()
                                    holder.isDragging = true
                                }
                            }
                            holder.lastRawX = x
                            holder.lastRawY = y
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        holder.clampTranslation()
                        holder.activePointerId = -1

                        if (holder.tvSwipeFeedback.visibility == View.VISIBLE) {
                            holder.tvSwipeFeedback.animate().alpha(0f).setDuration(300).withEndAction {
                                holder.tvSwipeFeedback.visibility = View.GONE
                                holder.tvSwipeFeedback.alpha = 1f
                            }.start()
                        }

                        if (holder.scaleFactor <= 1.0f) {
                            val diffY = event.rawY - holder.swipeStartY
                            val diffX = event.rawX - holder.swipeStartX
                            val isSwipeUp = Math.abs(diffX) < Math.abs(diffY) && diffY < -150

                            if (event.actionMasked == MotionEvent.ACTION_UP && isSwipeUp && !holder.isVolumeSwipe && !holder.isBrightnessSwipe) {
                                activity.showModernDetailsBottomSheet()
                            } else if (event.actionMasked == MotionEvent.ACTION_UP && !holder.isDragging && !holder.isVolumeSwipe && !holder.isBrightnessSwipe && event.eventTime - event.downTime < 200) {
                                activity.toggleUIVisibility()
                                val hidden = activity.isUiHidden
                                
                                TransitionManager.beginDelayedTransition(holder.videoContainer as ViewGroup)
                                holder.controlsLayout.visibility = if (hidden) View.GONE else View.VISIBLE
                            }
                        }
                        holder.isDragging = false
                        holder.isVolumeSwipe = false
                        holder.isBrightnessSwipe = false
                    }
                }
                true
            }

            val togglePlayPause = {
                try {
                    if (holder.videoView.isPlaying) {
                        holder.videoView.pause()
                        holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
                        activity.timeHandler.removeCallbacks(activity.updateTimeRunnable)
                    } else {
                        holder.videoView.start()
                        holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_pause)
                        activity.startProgressUpdater(holder)
                        holder.videoView.postDelayed({
                            holder.videoThumbnail.visibility = View.GONE
                        }, 250)
                    }
                } catch (e: Exception) {}
            }
            
            holder.btnBottomPlayPause.setOnClickListener { togglePlayPause() }

            holder.videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        holder.tvCurrentTime.text = formatFsTime(progress)
                        holder.tvSeekPreview.text = formatFsTime(progress)
                        
                        seekBar?.let { sb ->
                            val width = sb.width - sb.paddingLeft - sb.paddingRight
                            val thumbPos = sb.paddingLeft + (width * progress / sb.max.toFloat())
                            holder.tvSeekPreview.translationX = thumbPos - (holder.tvSeekPreview.width / 2f)
                        }
                        
                        val now = System.currentTimeMillis()
                        if (now - holder.lastSeekTime > 200) { 
                            holder.lastSeekTime = now
                            try { holder.videoView.seekTo(progress) } catch (e: Exception) {}
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    activity.isUserSeeking = true 
                    activity.viewPager.isUserInputEnabled = false 
                    holder.tvSeekPreview.visibility = View.VISIBLE 
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    activity.isUserSeeking = false 
                    activity.viewPager.isUserInputEnabled = true 
                    holder.tvSeekPreview.visibility = View.GONE 
                    try { holder.videoView.seekTo(seekBar?.progress ?: 0) } catch (e: Exception) {}
                    activity.startProgressUpdater(holder)
                }
            })

            holder.videoView.setOnCompletionListener { 
                holder.btnBottomPlayPause.setImageResource(R.drawable.ic_modern_play)
                holder.videoThumbnail.visibility = View.VISIBLE
                activity.timeHandler.removeCallbacks(activity.updateTimeRunnable)
            }

            holder.btnPrev.setOnClickListener {
                val currentPos = activity.viewPager.currentItem
                if (currentPos > 0) activity.viewPager.setCurrentItem(currentPos - 1, true)
            }
            holder.btnNext.setOnClickListener {
                val currentPos = activity.viewPager.currentItem
                if (currentPos < list.size - 1) activity.viewPager.setCurrentItem(currentPos + 1, true)
            }
            holder.btnMuteToggle.setOnClickListener {
                activity.isGlobalMuted = !activity.isGlobalMuted
                holder.mediaPlayerRef?.setVolume(if (activity.isGlobalMuted) 0f else 1f, if (activity.isGlobalMuted) 0f else 1f)
                holder.btnMuteToggle.setImageResource(if (activity.isGlobalMuted) R.drawable.ic_modern_mute else R.drawable.ic_modern_unmute)
            }
            
        } else {
            holder.videoContainer.visibility = View.GONE
            holder.controlsLayout.visibility = View.GONE
            holder.photoView.visibility = View.VISIBLE
            
            if (isFsUnsupportedFormat(item.path)) {
                Glide.with(holder.photoView.context).clear(holder.photoView)
                holder.photoView.setImageDrawable(activity.getPlaceholder())
            } else {
                Glide.with(holder.photoView.context)
                    .load(item.uri)
                    .error(activity.getPlaceholder())
                    .into(holder.photoView)
            }
            
            holder.photoView.setOnPhotoTapListener { _, _, _ ->
                activity.toggleUIVisibility()
            }
            
            holder.photoView.setOnSingleFlingListener { e1, e2, velocityX, velocityY ->
                if (e1 != null && e2 != null) {
                    val diffY = e2.rawY - e1.rawY
                    val diffX = e2.rawX - e1.rawX
                    
                    if (Math.abs(diffX) < Math.abs(diffY) && diffY < -150 && Math.abs(velocityY) > 200) {
                        activity.showModernDetailsBottomSheet()
                        return@setOnSingleFlingListener true
                    }
                }
                false
            }
        }
    }
    
    override fun getItemCount() = list.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        try { 
            holder.videoView.stopPlayback()
            holder.videoView.tag = null
            holder.mediaPlayerRef?.release()
            holder.mediaPlayerRef = null
        } catch (e: Exception) {}
        try {
            holder.scaleFactor = 1f
            holder.videoView.scaleX = 1f
            holder.videoView.scaleY = 1f
            holder.videoThumbnail.scaleX = 1f
            holder.videoThumbnail.scaleY = 1f
            holder.videoView.translationX = 0f
            holder.videoView.translationY = 0f
            holder.videoThumbnail.translationX = 0f
            holder.videoThumbnail.translationY = 0f
            holder.isDragging = false
            holder.activePointerId = -1
            holder.lastRawX = 0f
            holder.lastRawY = 0f
            holder.swipeStartX = 0f
            holder.swipeStartY = 0f
            holder.isVolumeSwipe = false
            holder.isBrightnessSwipe = false
        } catch (e: Exception) {}
    }
}
