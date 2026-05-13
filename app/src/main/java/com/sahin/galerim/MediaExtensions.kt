@file:Suppress("DEPRECATION", "UNUSED_VARIABLE")

package com.sahin.galerim

import android.content.ContentUris
import android.content.Context
import android.location.Geocoder
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

fun isUnsupportedFormat(path: String): Boolean {
    val p = path.lowercase(Locale.getDefault())
    return p.endsWith(".tif") || p.endsWith(".tiff")
}

fun isAnimated(path: String): Boolean {
    val p = path.lowercase(Locale.getDefault())
    return p.endsWith(".gif") || p.endsWith(".webp")
}

fun formatDuration(d: Long): String { 
    val s = (d / 1000) % 60
    val m = (d / (1000 * 60)) % 60
    val h = d / (1000 * 60 * 60)
    
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s) 
    } else {
        String.format("%02d:%02d", m, s)
    }
}

fun MainActivity.loadMedia(uri: Uri, isImg: Boolean, list: MutableList<MediaItem>) {
    val activity = this
    val prefs = activity.getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
    val editedSet = prefs.getStringSet("manually_edited_media", emptySet()) ?: emptySet()
    val pr = mutableListOf(
        MediaStore.MediaColumns._ID, 
        MediaStore.MediaColumns.BUCKET_ID, 
        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, 
        MediaStore.MediaColumns.DATE_MODIFIED, 
        MediaStore.MediaColumns.SIZE, 
        MediaStore.MediaColumns.DATA, 
        "datetaken"
    )
    
    if (!isImg) {
        pr.add(MediaStore.MediaColumns.DURATION)
    }
    
    activity.contentResolver.query(uri, pr.toTypedArray(), null, null, null)?.use { c ->
        val iC = c.getColumnIndexOrThrow(pr[0])
        val bIdC = c.getColumnIndexOrThrow(pr[1])
        val bNC = c.getColumnIndexOrThrow(pr[2])
        val dC = c.getColumnIndexOrThrow(pr[3])
        val sC = c.getColumnIndexOrThrow(pr[4])
        val pC = c.getColumnIndexOrThrow(pr[5])
        val dtC = c.getColumnIndex("datetaken")
        val duC = if (!isImg) c.getColumnIndexOrThrow(pr[7]) else -1
        
        while (c.moveToNext()) {
            val path = c.getString(pC)
            var dateSecs = c.getLong(dC)
            
            if (!editedSet.contains(path)) {
                if (dtC != -1) {
                    val dtMillis = c.getLong(dtC)
                    if (dtMillis > 0) {
                        dateSecs = dtMillis / 1000L
                    }
                }
            }
            
            list.add(MediaItem(
                ContentUris.withAppendedId(uri, c.getLong(iC)), 
                !isImg, 
                c.getLong(bIdC), 
                c.getString(bNC) ?: "Diğer", 
                dateSecs, 
                if (!isImg) c.getLong(duC) else 0L, 
                c.getLong(sC), 
                path
            ))
        }
    }
}

fun MainActivity.loadAllMedia() {
    val activity = this
    activity.lifecycleScope.launch(Dispatchers.IO) {
        val tempMediaList = mutableListOf<MediaItem>()
        val tempTrashList = mutableListOf<MediaItem>()
        val temp = mutableListOf<MediaItem>()
        
        activity.loadMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, temp)
        activity.loadMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, false, temp)
        
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
        val currentTime = System.currentTimeMillis()
        var expiredCount = 0
        val pathsToPermanentDelete = mutableListOf<String>()
        
        for (path in MainActivity.trashedPaths.toList()) {
            val trashedTime = MainActivity.trashedTimestamps[path] ?: currentTime
            if (currentTime - trashedTime >= thirtyDaysInMillis) {
                pathsToPermanentDelete.add(path)
            }
        }
        
        for (path in pathsToPermanentDelete) {
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
            }
            
            MainActivity.trashedPaths.remove(path)
            MainActivity.trashedOriginalPaths.remove(path)
            MainActivity.trashedTimestamps.remove(path)
            MainActivity.trashedIsVideo.remove(path)
            MainActivity.trashedDurations.remove(path)
            MainActivity.trashedSizes.remove(path)
            expiredCount++
        }
        
        if (expiredCount > 0) {
            MainActivity.saveTrashedPaths(activity)
        }

        temp.distinctBy { it.path }.forEach { item ->
            if (!pathsToPermanentDelete.contains(item.path)) {
                if (MainActivity.trashedPaths.contains(item.path)) {
                    tempTrashList.add(item)
                } else {
                    tempMediaList.add(item)
                }
            }
        }
        
        for (path in MainActivity.trashedPaths) {
            if (path.contains(".galerim_trash")) {
                val file = File(path)
                if (file.exists()) {
                    tempTrashList.add(MediaItem(
                        Uri.fromFile(file),
                        MainActivity.trashedIsVideo[path] ?: false,
                        0L,
                        "Çöp Kutusu",
                        file.lastModified() / 1000L,
                        MainActivity.trashedDurations[path] ?: 0L,
                        MainActivity.trashedSizes[path] ?: file.length(),
                        path
                    ))
                }
            }
        }

        val prefs = activity.getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val sortOrder = prefs.getString("sort_order", "Değiştirilme (önce yeni)")
        
        when (sortOrder) {
            "Dosya adı (A - Z)" -> tempMediaList.sortBy { File(it.path).name.lowercase(Locale("tr")) }
            "Dosya adı (Z - A)" -> tempMediaList.sortByDescending { File(it.path).name.lowercase(Locale("tr")) }
            "Değiştirilme (önce yeni)" -> tempMediaList.sortByDescending { it.dateAdded }
            "Değiştirilme (önce eski)" -> tempMediaList.sortBy { it.dateAdded }
            "Tür (A - Z)" -> tempMediaList.sortBy { File(it.path).extension.lowercase(Locale("tr")) }
            "Tür (Z - A)" -> tempMediaList.sortByDescending { File(it.path).extension.lowercase(Locale("tr")) }
            "Boyut (önce en büyük)" -> tempMediaList.sortByDescending { it.size }
            "Boyut (önce en küçük)" -> tempMediaList.sortBy { it.size }
        }
        
        tempTrashList.sortByDescending { it.dateAdded }
        
        withContext(Dispatchers.Main) {
            MainActivity.mediaList.clear()
            MainActivity.mediaList.addAll(tempMediaList)
            
            MainActivity.trashList.clear()
            MainActivity.trashList.addAll(tempTrashList)
            
            activity.locationGroups = emptyMap()
            
            activity.buildAlbums()
            activity.loadDisplayedList()
            
            activity.autoPlayHandler.removeCallbacksAndMessages(null)
            if (activity.isActivityResumed) {
                activity.autoPlayHandler.postDelayed(activity.autoPlayRunnable, 1000)
            }
            
            if (expiredCount > 0) {
                Toast.makeText(activity, "Süresi dolan $expiredCount öğe çöp kutusundan kalıcı olarak silindi.", Toast.LENGTH_SHORT).show()
            }
            Unit
        }
    }
}

fun MainActivity.loadDisplayedList() {
    val activity = this
    activity.allRecycler.stopScroll()
    activity.albumsRecycler.stopScroll()
    
    activity.currentlyPlayingPosition = -1
    activity.releaseMediaPlayer()
    activity.autoPlayHandler.removeCallbacksAndMessages(null)
    
    activity.locationTask?.cancel()
    MainActivity.displayedMediaList.clear()
    
    if (activity.isSearchMode && activity.currentSearchQuery.isNotEmpty()) {
        val q = activity.currentSearchQuery.lowercase(Locale("tr"))
        MainActivity.displayedMediaList.addAll(MainActivity.mediaList.filter { item ->
            val fileName = File(item.path).name.lowercase(Locale("tr"))
            val cal = Calendar.getInstance()
            cal.timeInMillis = item.dateAdded * 1000L
            val dateFull = SimpleDateFormat("dd MMMM yyyy EEEE yyyy HH mm ss", Locale("tr")).format(cal.time).lowercase(Locale("tr"))
            
            fileName.contains(q) || dateFull.contains(q) || item.bucketName.lowercase(Locale("tr")).contains(q)
        })
    } else if (activity.isShowingTrash) {
        MainActivity.displayedMediaList.addAll(MainActivity.trashList)
    } else if (activity.isShowingFavorites) {
        MainActivity.displayedMediaList.addAll(MainActivity.mediaList.filter { MainActivity.favoritePaths.contains(it.path) })
    } else if (activity.isShowingPlaces) {
        MainActivity.displayedMediaList.addAll(MainActivity.mediaList) 
    } else if (activity.isShowingLocations && activity.filterLocation == null) {
        MainActivity.displayedMediaList.addAll(MainActivity.mediaList) 
    } else if (activity.filterBucketId != null) {
        MainActivity.displayedMediaList.addAll(MainActivity.mediaList.filter { it.bucketId == activity.filterBucketId })
    } else if (activity.filterLocation != null) {
        MainActivity.displayedMediaList.addAll(MainActivity.mediaList.filter { item ->
            val loc = MainActivity.itemLocationCache[item.path] ?: ""
            val k = if (loc.isEmpty()) "Bilinmeyen Konum" else loc
            k == activity.filterLocation
        })
    } else if (activity.bottomTabLayout.selectedTabPosition == 1) {
        MainActivity.displayedMediaList.addAll(MainActivity.mediaList.filter { it.isVideo })
    } else {
        MainActivity.displayedMediaList.addAll(MainActivity.mediaList.filter { !it.isVideo })
    }

    MainActivity.galleryItems.clear()

    if (activity.isShowingTrash) {
        for (item in MainActivity.displayedMediaList) {
            MainActivity.galleryItems.add(MediaContentItem(item))
        }
        activity.allRecycler.adapter?.notifyDataSetChanged()
        activity.updateEmptyStateUI()
        return
    }
    
    if (activity.isShowingLocations && activity.filterLocation == null && !activity.isSearchMode) {
        val locGrFast = mutableMapOf<String, MutableList<MediaItem>>()
        var hasMissing = false
        for (item in MainActivity.displayedMediaList) {
            val loc = MainActivity.itemLocationCache[item.path] ?: ""
            if (loc.isEmpty()) hasMissing = true
            val k = if (loc.isEmpty()) "Bilinmeyen Konum" else loc
            if (!locGrFast.containsKey(k)) locGrFast[k] = mutableListOf()
            locGrFast[k]?.add(item)
        }
        
        activity.locationGroups = locGrFast
        activity.albumList.clear()
        locGrFast.keys.sortedBy { if (it == "Bilinmeyen Konum") "zzz" else it }.forEach { k ->
            val items = locGrFast[k]!!
            activity.albumList.add(Album(null, k, k, items.first().uri, items.size))
        }
        activity.albumsRecycler.adapter?.notifyDataSetChanged()

        if (hasMissing) {
            val listCopy = MainActivity.displayedMediaList.toList()
            activity.locationTask = activity.lifecycleScope.launch(Dispatchers.IO) {
                val geocoder = Geocoder(activity.applicationContext, Locale.getDefault())
                val locGr = mutableMapOf<String, MutableList<MediaItem>>()
                
                for (item in listCopy) {
                    if (!isActive) break
                    yield()
                    
                    var loc: String = MainActivity.itemLocationCache[item.path] ?: ""
                    
                    if (loc.isEmpty()) {
                        var retriever: MediaMetadataRetriever? = null
                        try {
                            var lat: Double? = null
                            var lng: Double? = null
                            
                            if (item.isVideo) {
                                val r = MediaMetadataRetriever()
                                retriever = r
                                r.setDataSource(activity.applicationContext, item.uri)
                                val m = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                                if (m != null) {
                                    val mt = Pattern.compile("([+-][0-9.]+)([+-][0-9.]+)").matcher(m)
                                    if (mt.find()) {
                                        lat = mt.group(1)?.toDoubleOrNull()
                                        lng = mt.group(2)?.toDoubleOrNull()
                                    }
                                }
                            } else {
                                val ex = ExifInterface(item.path)
                                val ll = FloatArray(2)
                                if (ex.getLatLong(ll)) {
                                    lat = ll[0].toDouble()
                                    lng = ll[1].toDouble()
                                }
                            }
                            
                            val safeLat = lat
                            val safeLng = lng
                            
                            if (safeLat != null && safeLng != null) {
                                val cacheKey = "$safeLat,$safeLng"
                                val cachedAddress = MainActivity.geocodeCache[cacheKey]
                                if (cachedAddress != null) {
                                    loc = cachedAddress
                                } else {
                                    val ad = geocoder.getFromLocation(safeLat, safeLng, 1)
                                    if (!ad.isNullOrEmpty()) {
                                        val foundLoc = ad[0].locality ?: ad[0].subAdminArea ?: ad[0].adminArea ?: ""
                                        loc = foundLoc
                                        if (foundLoc.isNotEmpty()) {
                                            MainActivity.geocodeCache[cacheKey] = foundLoc
                                        }
                                    }
                                }
                            }
                        } catch (e: CancellationException) { 
                            throw e 
                        } catch (e: Exception) {
                        } finally { 
                            try { 
                                retriever?.release() 
                            } catch (e: Exception) {} 
                        }
                        
                        if (loc.isNotEmpty()) {
                            MainActivity.itemLocationCache[item.path] = loc
                        }
                    }
                    
                    val k = if (loc.isEmpty()) "Bilinmeyen Konum" else loc
                    if (!locGr.containsKey(k)) {
                        locGr[k] = mutableListOf()
                    }
                    locGr[k]?.add(item)
                }
                
                withContext(Dispatchers.Main) {
                    if (!isActive) return@withContext
                    activity.locationGroups = locGr
                    activity.albumList.clear()
                    locGr.keys.sortedBy { if (it == "Bilinmeyen Konum") "zzz" else it }.forEach { k ->
                        val items = locGr[k]!!
                        activity.albumList.add(Album(null, k, k, items.first().uri, items.size))
                    }
                    activity.albumsRecycler.adapter?.notifyDataSetChanged()
                }
            }
        }
    } else if (activity.isShowingPlaces && !activity.isSearchMode) {
        val df = SimpleDateFormat("d MMMM yyyy EEEE", Locale("tr"))
        val cal = Calendar.getInstance()
        
        val plGrFast = mutableListOf<Pair<String, MutableList<MediaItem>>>()
        var curKFast = ""
        var hasMissing = false
        
        for (item in MainActivity.displayedMediaList) {
            val loc = MainActivity.itemLocationCache[item.path] ?: ""
            if (loc.isEmpty()) hasMissing = true
            
            cal.timeInMillis = item.dateAdded * 1000L
            val d = df.format(cal.time)
            val locStr = if (loc.isEmpty()) null else loc
            val fk = if (locStr == null) d else "$d|$locStr"
            
            if (fk != curKFast) {
                curKFast = fk
                plGrFast.add(Pair(fk, mutableListOf()))
            }
            plGrFast.last().second.add(item)
        }
        
        MainActivity.galleryItems.clear()
        val tempDisplayed = mutableListOf<MediaItem>()
        
        plGrFast.forEach { g -> 
            val parts = g.first.split("|")
            val title = parts[0]
            val locationName = if (parts.size > 1) parts[1] else null
            MainActivity.galleryItems.add(HeaderItem(title, locationName))
            g.second.forEach { item -> 
                MainActivity.galleryItems.add(MediaContentItem(item))
                tempDisplayed.add(item) 
            } 
        }
        MainActivity.displayedMediaList.clear()
        MainActivity.displayedMediaList.addAll(tempDisplayed)
        
        activity.allRecycler.adapter?.notifyDataSetChanged()
        
        if (hasMissing) {
            val listCopy = tempDisplayed.toList()
            activity.locationTask = activity.lifecycleScope.launch(Dispatchers.IO) {
                val geocoder = Geocoder(activity.applicationContext, Locale.getDefault())
                val plGr = mutableListOf<Pair<String, MutableList<MediaItem>>>()
                var curK = ""
                
                for (item in listCopy) {
                    if (!isActive) break
                    yield()
                    
                    var loc: String = MainActivity.itemLocationCache[item.path] ?: ""
                    
                    if (loc.isEmpty()) {
                        var retriever: MediaMetadataRetriever? = null
                        try {
                            var lat: Double? = null
                            var lng: Double? = null
                            
                            if (item.isVideo) {
                                val r = MediaMetadataRetriever()
                                retriever = r
                                r.setDataSource(activity.applicationContext, item.uri)
                                val m = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                                if (m != null) {
                                    val mt = Pattern.compile("([+-][0-9.]+)([+-][0-9.]+)").matcher(m)
                                    if (mt.find()) {
                                        lat = mt.group(1)?.toDoubleOrNull()
                                        lng = mt.group(2)?.toDoubleOrNull()
                                    }
                                }
                            } else {
                                val ex = ExifInterface(item.path)
                                val ll = FloatArray(2)
                                if (ex.getLatLong(ll)) {
                                    lat = ll[0].toDouble()
                                    lng = ll[1].toDouble()
                                }
                            }
                            
                            val safeLat = lat
                            val safeLng = lng
                            
                            if (safeLat != null && safeLng != null) {
                                val cacheKey = "$safeLat,$safeLng"
                                val cachedAddress = MainActivity.geocodeCache[cacheKey]
                                if (cachedAddress != null) {
                                    loc = cachedAddress
                                } else {
                                    val ad = geocoder.getFromLocation(safeLat, safeLng, 1)
                                    if (!ad.isNullOrEmpty()) {
                                        val foundLoc = ad[0].locality ?: ad[0].subAdminArea ?: ad[0].adminArea ?: ""
                                        loc = foundLoc
                                        if (foundLoc.isNotEmpty()) {
                                            MainActivity.geocodeCache[cacheKey] = foundLoc
                                        }
                                    }
                                }
                            }
                        } catch (e: CancellationException) { 
                            throw e 
                        } catch (e: Exception) {
                        } finally { 
                            try { 
                                retriever?.release() 
                            } catch (e: Exception) {} 
                        }
                        
                        if (loc.isNotEmpty()) {
                            MainActivity.itemLocationCache[item.path] = loc
                        }
                    }
                    
                    cal.timeInMillis = item.dateAdded * 1000L
                    val d = df.format(cal.time)
                    val locStr = if (loc.isEmpty()) null else loc
                    val fk = if (locStr == null) d else "$d|$locStr"
                    
                    if (fk != curK) { 
                        curK = fk
                        plGr.add(Pair(fk, mutableListOf())) 
                    }
                    
                    plGr.last().second.add(item)
                }
                
                withContext(Dispatchers.Main) {
                    if (!isActive) return@withContext
                    
                    MainActivity.galleryItems.clear()
                    MainActivity.displayedMediaList.clear()
                    
                    plGr.forEach { g -> 
                        val parts = g.first.split("|")
                        val title = parts[0]
                        val locationName = if (parts.size > 1) parts[1] else null
                        
                        MainActivity.galleryItems.add(HeaderItem(title, locationName))
                        
                        g.second.forEach { item -> 
                            MainActivity.galleryItems.add(MediaContentItem(item))
                            MainActivity.displayedMediaList.add(item) 
                        } 
                    }
                    
                    activity.allRecycler.adapter?.notifyDataSetChanged()
                    
                    if (activity.isActivityResumed) {
                        activity.autoPlayHandler.postDelayed(activity.autoPlayRunnable, 1000)
                    }
                }
            }
        } else {
            if (activity.isActivityResumed) {
                activity.autoPlayHandler.postDelayed(activity.autoPlayRunnable, 1000)
            }
        }
    } else {
        val df = SimpleDateFormat("d MMMM yyyy EEEE", Locale("tr"))
        val cal = Calendar.getInstance()
        var curH = ""
        
        for (item in MainActivity.displayedMediaList) { 
            cal.timeInMillis = item.dateAdded * 1000L
            val h = df.format(cal.time)
            
            if (h != curH) { 
                MainActivity.galleryItems.add(HeaderItem(h, null))
                curH = h 
            }
            
            MainActivity.galleryItems.add(MediaContentItem(item)) 
        }
        
        activity.allRecycler.adapter?.notifyDataSetChanged()
        
        if (activity.isActivityResumed) {
            activity.autoPlayHandler.postDelayed(activity.autoPlayRunnable, 1000)
        }
        
        activity.updateEmptyStateUI()
    }
}

fun MainActivity.buildAlbums() { 
    val activity = this
    activity.albumList.clear() 
    
    MainActivity.mediaList.groupBy { it.bucketId }.forEach { (id, items) -> 
        val f = items.first()
        activity.albumList.add(Album(id, null, f.bucketName, f.uri, items.size)) 
    } 
    
    activity.albumList.sortByDescending { it.count } 
    activity.albumsRecycler.adapter?.notifyDataSetChanged() 
}
