@file:Suppress("DEPRECATION", "UNUSED_VARIABLE")

package com.sahin.galerim

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.util.Locale

fun formatFsTime(ms: Int): String {
    val s = (ms / 1000) % 60; val m = (ms / (1000 * 60)) % 60; val h = ms / (1000 * 60 * 60)
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

fun isFsUnsupportedFormat(path: String): Boolean {
    val p = path.lowercase(Locale.getDefault())
    return p.endsWith(".tif") || p.endsWith(".tiff")
}

fun ViewPager2.reduceFsDragSensitivity() {
    try {
        val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recyclerViewField.isAccessible = true
        val recyclerView = recyclerViewField.get(this) as RecyclerView
        val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int
        touchSlopField.set(recyclerView, touchSlop * 2)
    } catch (e: Exception) {}
}

fun Context.showFsCustomToast(message: String, iconResId: Int) {
    val activity = this as? androidx.appcompat.app.AppCompatActivity ?: return
    try {
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        val existing = rootView.findViewWithTag<View>("FS_CUSTOM_TOAST")
        if (existing != null) {
            rootView.removeView(existing)
        }
        
        val layout = LinearLayout(activity).apply {
            tag = "FS_CUSTOM_TOAST"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = 50f
            }
            setPadding(40, 24, 40, 24)
        }
        
        if (iconResId != 0) {
            val icon = ImageView(activity).apply {
                setImageResource(iconResId)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(56, 56).apply { setMargins(0, 0, 24, 0) }
            }
            layout.addView(icon)
        }
        
        val text = TextView(activity).apply {
            this.text = message
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        layout.addView(text)
        
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { 
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 150 
        }
        rootView.addView(layout, params)
        
        layout.startAnimation(AlphaAnimation(0.0f, 1.0f).apply { duration = 400 })
        Handler(Looper.getMainLooper()).postDelayed({
            layout.startAnimation(AlphaAnimation(1.0f, 0.0f).apply { duration = 400 })
            Handler(Looper.getMainLooper()).postDelayed({ rootView.removeView(layout) }, 400)
        }, 2000)
    } catch (e: Exception) {}
}
