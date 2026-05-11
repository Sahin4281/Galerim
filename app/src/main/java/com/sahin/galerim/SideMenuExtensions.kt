package com.sahin.galerim

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

fun MainActivity.setupSideMenu() {
    val drawerLayout = findViewById<DrawerLayout>(R.id.mainDrawerLayout)
    val sideRecycler = findViewById<RecyclerView>(R.id.sideMenuAlbumsRecycler)
    val mainContent = drawerLayout.getChildAt(0)

    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
    drawerLayout.setScrimColor(Color.TRANSPARENT)
    drawerLayout.drawerElevation = 0f

    sideRecycler.layoutManager = LinearLayoutManager(this)
    sideRecycler.adapter = SideMenuAdapter(this)

    drawerLayout.setOnTouchListener { _, event ->
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            val sideMenu = drawerLayout.getChildAt(1)
            if (sideMenu != null && event.x > sideMenu.right) {
                mainContent.dispatchTouchEvent(event)
                return@setOnTouchListener true
            }
        }
        false
    }

    drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
        override fun onDrawerStateChanged(newState: Int) {
            val isInsideAlbum = filterBucketId != null || filterLocation != null
            if (!isInsideAlbum) {
                drawerLayout.closeDrawer(GravityCompat.START)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
                return
            }
        }

        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            val isInsideAlbum = filterBucketId != null || filterLocation != null
            if (!isInsideAlbum) {
                drawerLayout.closeDrawer(GravityCompat.START)
                return
            }
            val offset = (drawerView.width * slideOffset).toInt()
            mainContent.setPadding(offset, 0, 0, 0)
        }

        override fun onDrawerOpened(drawerView: View) {
            sideRecycler.post {
                try {
                    sideRecycler.adapter?.notifyDataSetChanged()
                } catch (e: Exception) {}
            }
            val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
            val originalSpan = prefs.getInt("gridSpanCount", 4)
            val layoutManager = allRecycler.layoutManager as? GridLayoutManager
            
            if (layoutManager != null) {
                val targetSpan = when {
                    originalSpan >= 5 -> 3
                    originalSpan == 4 -> 2
                    originalSpan == 3 -> 2
                    else -> 1
                }
                if (layoutManager.spanCount != targetSpan) {
                    allRecycler.post {
                        try {
                            layoutManager.spanCount = targetSpan
                            allRecycler.adapter?.notifyDataSetChanged()
                        } catch (e: Exception) {}
                    }
                }
            }
        }
        
        override fun onDrawerClosed(drawerView: View) {
            mainContent.setPadding(0, 0, 0, 0)
            val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
            val originalSpan = prefs.getInt("gridSpanCount", 4)
            val layoutManager = allRecycler.layoutManager as? GridLayoutManager
            
            if (layoutManager != null && layoutManager.spanCount != originalSpan) {
                allRecycler.post {
                    try {
                        layoutManager.spanCount = originalSpan
                        allRecycler.adapter?.notifyDataSetChanged()
                    } catch (e: Exception) {}
                }
            }
        }
    })
}

fun MainActivity.enableSideMenu(enable: Boolean) {
    val drawerLayout = findViewById<DrawerLayout>(R.id.mainDrawerLayout)
    if (enable) {
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
        val sideRecycler = findViewById<RecyclerView>(R.id.sideMenuAlbumsRecycler)
        sideRecycler.post {
            try {
                sideRecycler.adapter?.notifyDataSetChanged()
            } catch (e: Exception) {}
        }
    } else {
        drawerLayout.closeDrawer(GravityCompat.START)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
        drawerLayout.getChildAt(0).setPadding(0, 0, 0, 0)
        
        val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val originalSpan = prefs.getInt("gridSpanCount", 4)
        val layoutManager = allRecycler.layoutManager as? GridLayoutManager
        if (layoutManager != null && layoutManager.spanCount != originalSpan) {
            allRecycler.post {
                try {
                    layoutManager.spanCount = originalSpan
                    allRecycler.adapter?.notifyDataSetChanged()
                } catch (e: Exception) {}
            }
        }
    }
}

fun MainActivity.isSideMenuOpen(): Boolean {
    val drawerLayout = findViewById<DrawerLayout>(R.id.mainDrawerLayout)
    return drawerLayout?.isDrawerOpen(GravityCompat.START) == true
}

fun MainActivity.closeSideMenu() {
    val drawerLayout = findViewById<DrawerLayout>(R.id.mainDrawerLayout)
    drawerLayout?.closeDrawer(GravityCompat.START)
}

class SideMenuAdapter(private val activity: MainActivity) : RecyclerView.Adapter<SideMenuAdapter.ViewHolder>() {
    
    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.sideMenuThumb)
        val name: TextView = v.findViewById(R.id.sideMenuName)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(
        LayoutInflater.from(activity).inflate(R.layout.item_side_menu_album, p, false)
    )

    override fun onBindViewHolder(h: ViewHolder, pos: Int) {
        val a = activity.albumList.getOrNull(pos) ?: return
        val prefs = activity.getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val customName = prefs.getString("custom_name_${a.bucketId ?: a.locationName}", null)
        val rawName = customName ?: a.name
        
        val displayName = if (rawName.length > 8) {
            "${rawName.take(8)}..."
        } else {
            rawName
        }

        Glide.with(activity).load(a.thumbnail).centerCrop().into(h.thumb)
        h.name.text = displayName

        h.itemView.setOnClickListener {
            activity.closeSideMenu()
            
            if (a.locationName != null) {
                activity.filterLocation = a.locationName
                activity.loadDisplayedList()
            } else if (a.bucketId != null) {
                activity.filterBucketId = a.bucketId
                activity.loadDisplayedList()
            }
            
            activity.mainTitle.text = rawName
            var photoCount = 0
            var videoCount = 0
            MainActivity.displayedMediaList.forEach { if (it.isVideo) videoCount++ else photoCount++ }
            
            val subtitleParts = mutableListOf<String>()
            if (photoCount > 0) subtitleParts.add("$photoCount fotoğraf")
            if (videoCount > 0) subtitleParts.add("$videoCount video")
            activity.findViewById<TextView>(R.id.subTitle)?.text = subtitleParts.joinToString(", ")
        }
    }

    override fun getItemCount() = activity.albumList.size
}
