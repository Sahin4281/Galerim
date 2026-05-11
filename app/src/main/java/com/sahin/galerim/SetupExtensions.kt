package com.sahin.galerim

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun MainActivity.setupSearchFunctionality() {
    
    btnSearchBack.setOnClickListener {
        closeSearchMode()
    }
    
    btnClearSearch.setOnClickListener {
        etSearch.text.clear()
    }
    
    etSearch.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            currentSearchQuery = s?.toString() ?: ""
            btnClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
            loadDisplayedList()
        }
        
        override fun afterTextChanged(s: android.text.Editable?) {}
    })
}

fun MainActivity.setupFastScroller() {
    var lastFastScrollY = 0f 

    fastScrollThumb.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 50f
        setColor(getAccentColor())
    }
    
    fastScrollBubbleContainer.setCardBackgroundColor(getAccentColor())

    hideScrollerRunnable = Runnable {
        fastScrollContainer.animate().alpha(0f).setDuration(300).start()
    }

    allRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            
            val offset = recyclerView.computeVerticalScrollOffset()
            val extent = recyclerView.computeVerticalScrollExtent()
            val range = recyclerView.computeVerticalScrollRange()

            if (range <= extent) {
                fastScrollContainer.visibility = View.GONE
                return
            } else {
                if (bottomTabLayout.selectedTabPosition != 2 && !isShowingTrash && !isShowingFavorites && !isSearchMode) {
                    fastScrollContainer.visibility = View.VISIBLE
                }
            }

            if (isFastScrolling) return

            fastScrollContainer.alpha = 1f
            scrollerHandler.removeCallbacks(hideScrollerRunnable!!)
            scrollerHandler.postDelayed(hideScrollerRunnable!!, 1500)

            val proportion = offset.toFloat() / (range - extent).toFloat()
            val maxScroll = fastScrollContainer.height - fastScrollThumb.height
            fastScrollThumb.translationY = proportion * maxScroll

            val layoutManager = recyclerView.layoutManager as? GridLayoutManager
            val firstVisiblePosition = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
            
            if (firstVisiblePosition in 0 until MainActivity.galleryItems.size) {
                val item = MainActivity.galleryItems[firstVisiblePosition]
                val timestamp = when (item) {
                    is HeaderItem -> {
                        val parts = item.title.split(" ")
                        if (parts.size >= 3) "${parts[1]} ${parts[2]}" else item.title
                    }
                    is MediaContentItem -> {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = item.media.dateAdded * 1000L
                        SimpleDateFormat("MMMM yyyy", Locale("tr")).format(cal.time)
                    }
                }
                fastScrollBubble.text = timestamp
                
                val bubbleOffset = (fastScrollThumb.height - fastScrollBubbleContainer.height) / 2f
                fastScrollBubbleContainer.translationY = (proportion * maxScroll) + bubbleOffset
            }
        }
    })

    fastScrollContainer.setOnTouchListener { _, event ->
        val range = allRecycler.computeVerticalScrollRange()
        val extent = allRecycler.computeVerticalScrollExtent()
        
        if (range <= extent) return@setOnTouchListener false

        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                val thumbTop = fastScrollThumb.translationY
                val thumbBottom = thumbTop + fastScrollThumb.height
                
                if (event.y < thumbTop || event.y > thumbBottom) {
                    return@setOnTouchListener false 
                }

                isFastScrolling = true
                scrollerHandler.removeCallbacks(hideScrollerRunnable!!)
                fastScrollContainer.alpha = 1f
                fastScrollBubbleContainer.visibility = View.VISIBLE

                lastFastScrollY = event.y

                val thumbHeight = fastScrollThumb.height.toFloat()
                val halfThumb = thumbHeight / 2f
                val containerHeight = fastScrollContainer.height.toFloat()
                
                val y = event.y.coerceIn(halfThumb, containerHeight - halfThumb)
                val scrollRange = containerHeight - thumbHeight
                val proportion = if (scrollRange > 0) (y - halfThumb) / scrollRange else 0f
                
                fastScrollThumb.translationY = proportion * scrollRange
                val bubbleOffset = (thumbHeight - fastScrollBubbleContainer.height) / 2f
                fastScrollBubbleContainer.translationY = (proportion * scrollRange) + bubbleOffset

                val layoutManager = allRecycler.layoutManager as? GridLayoutManager
                val itemCount = layoutManager?.itemCount ?: 1
                val targetPos = (proportion * itemCount).toInt().coerceIn(0, itemCount - 1)
                layoutManager?.scrollToPositionWithOffset(targetPos, 0)
                
                if (targetPos in 0 until MainActivity.galleryItems.size) {
                    val item = MainActivity.galleryItems[targetPos]
                    val timestamp = when (item) {
                        is HeaderItem -> {
                            val parts = item.title.split(" ")
                            if (parts.size >= 3) "${parts[1]} ${parts[2]}" else item.title
                        }
                        is MediaContentItem -> {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = item.media.dateAdded * 1000L
                            SimpleDateFormat("MMMM yyyy", Locale("tr")).format(cal.time)
                        }
                    }
                    fastScrollBubble.text = timestamp
                }
                true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val currentY = event.y
                val dy = currentY - lastFastScrollY
                lastFastScrollY = currentY

                val thumbHeight = fastScrollThumb.height.toFloat()
                val halfThumb = thumbHeight / 2f
                val containerHeight = fastScrollContainer.height.toFloat()
                
                val boundedY = currentY.coerceIn(halfThumb, containerHeight - halfThumb)
                val scrollRange = containerHeight - thumbHeight
                val proportion = if (scrollRange > 0) (boundedY - halfThumb) / scrollRange else 0f

                fastScrollThumb.translationY = proportion * scrollRange
                val bubbleOffset = (thumbHeight - fastScrollBubbleContainer.height) / 2f
                fastScrollBubbleContainer.translationY = (proportion * scrollRange) + bubbleOffset

                val maxRecyclerScroll = range - extent
                val ratio = maxRecyclerScroll.toFloat() / scrollRange

                val layoutManager = allRecycler.layoutManager as? GridLayoutManager

                if (proportion <= 0.01f) {
                    layoutManager?.scrollToPositionWithOffset(0, 0)
                } else if (proportion >= 0.99f) {
                    val itemCount = layoutManager?.itemCount ?: 1
                    layoutManager?.scrollToPositionWithOffset(itemCount - 1, 0)
                } else {
                    if (ratio > 0 && Math.abs(dy) > 0) {
                        allRecycler.scrollBy(0, (dy * ratio).toInt())
                    }
                }

                val firstVisiblePosition = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                if (firstVisiblePosition in 0 until MainActivity.galleryItems.size) {
                    val item = MainActivity.galleryItems[firstVisiblePosition]
                    val timestamp = when (item) {
                        is HeaderItem -> {
                            val parts = item.title.split(" ")
                            if (parts.size >= 3) "${parts[1]} ${parts[2]}" else item.title
                        }
                        is MediaContentItem -> {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = item.media.dateAdded * 1000L
                            SimpleDateFormat("MMMM yyyy", Locale("tr")).format(cal.time)
                        }
                    }
                    fastScrollBubble.text = timestamp
                }
                true
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                isFastScrolling = false
                fastScrollBubbleContainer.visibility = View.GONE
                scrollerHandler.postDelayed(hideScrollerRunnable!!, 1500)
                true
            }
            else -> false
        }
    }
}

fun MainActivity.setupElegantBottomTabs() {
    val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
    val tabNames = arrayOf("Fotoğraflar", "Videolar", "Albümler", "Daha fazla")
    
    bottomTabLayout.removeAllTabs()
    bottomTabLayout.setSelectedTabIndicatorHeight(0) 
    
    for (i in tabNames.indices) {
        val tab = bottomTabLayout.newTab()
        tab.customView = TextView(this).apply {
            text = tabNames[i]
            textSize = 14.5f
            gravity = android.view.Gravity.CENTER
        }
        bottomTabLayout.addTab(tab)
    }
    
    updateTabAppearance(0)
    
    bottomTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) {
            allRecycler.stopScroll()
            albumsRecycler.stopScroll()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                allRecycler.stopNestedScroll()
                albumsRecycler.stopNestedScroll()
            }

            if (previousTabPosition == 0 || previousTabPosition == 1) {
                tabScrollStates[previousTabPosition] = allRecycler.layoutManager?.onSaveInstanceState()
            }

            updateTabAppearance(tab.position)
            
            if (isSearchMode) {
                closeSearchMode()
            }
            
            when (tab.position) {
                0, 1 -> { 
                    resetStates()
                    fastScrollContainer.visibility = View.VISIBLE
                    allRecycler.visibility = View.VISIBLE
                    albumsRecycler.visibility = View.GONE
                    loadDisplayedList() 
                    
                    tabScrollStates[tab.position]?.let {
                        allRecycler.layoutManager?.onRestoreInstanceState(it)
                    } ?: run {
                        allRecycler.scrollToPosition(0)
                    }
                }
                2 -> { 
                    resetStates()
                    fastScrollContainer.visibility = View.GONE
                    
                    if (bottomTabLayout.tag == "restoring" && (filterBucketId != null || filterLocation != null || isSearchMode)) {
                        allRecycler.visibility = View.VISIBLE
                        albumsRecycler.visibility = View.GONE
                    } else {
                        buildAlbums()
                        allRecycler.visibility = View.GONE
                        albumsRecycler.visibility = View.VISIBLE 
                    }
                }
                3 -> {
                    showGalleryMenuBottomSheet()
                }
            }
            
            if (tab.position != 3) {
                previousTabPosition = tab.position
            }
        }
        
        override fun onTabUnselected(tab: TabLayout.Tab) {}
        
        override fun onTabReselected(tab: TabLayout.Tab) { 
            if (tab.position == 3) {
                showGalleryMenuBottomSheet() 
            }
        }
    })
}

fun MainActivity.setupSelectionButtons() {
    findViewById<View>(R.id.btnCloseSelection)?.setOnClickListener { 
        exitSelectionMode() 
    }
    
    findViewById<View>(R.id.btnShare)?.setOnClickListener { 
        shareSelectedMedia() 
    }
    
    findViewById<View>(R.id.btnMore)?.setOnClickListener { 
        showSelectionMoreMenu() 
    }
    
    findViewById<View>(R.id.btnSelectAll)?.setOnClickListener {
        if (selectedMedia.size == MainActivity.displayedMediaList.size) {
            selectedMedia.clear()
            exitSelectionMode()
        } else {
            selectedMedia.clear()
            selectedMedia.addAll(MainActivity.displayedMediaList)
            updateSelectionUI()
            allRecycler.adapter?.notifyDataSetChanged()
        }
    }

    findViewById<View>(R.id.btnRestore)?.setOnClickListener {
        if (selectedMedia.isNotEmpty() && isShowingTrash) {
            performTrashRestore(selectedMedia.toList())
        }
    }
    
    findViewById<View>(R.id.btnDelete)?.setOnClickListener { 
        if (selectedMedia.isNotEmpty()) {
            showMultiDeleteConfirmationDialog(selectedMedia.toList())
        }
    }
}
