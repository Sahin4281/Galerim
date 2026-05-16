package com.sahin.galerim.utils

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.sahin.galerim.FacesActivity
import com.sahin.galerim.InteractiveMapActivity
import com.sahin.galerim.MemoriesActivity
import com.sahin.galerim.R
import com.sahin.galerim.SmartCategoriesActivity
import com.sahin.galerim.SpaceCleanerActivity

object PopupMenuHelper {
    fun showMoreMenu(context: Context, anchor: View) {
        val prefs = context.getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val currentTheme = prefs.getString("appTheme", "Sistem Teması")
        val isAmoled = currentTheme == "Koyu Amoled Tema"
        
        val dialogBgColor = ContextCompat.getColor(context, R.color.p_app_dialog_bg)
        val primaryColor = ContextCompat.getColor(context, R.color.p_app_text_primary)
        
        val menuLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { 
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 45f
                setColor(if (isAmoled) Color.BLACK else dialogBgColor) 
            }
            setPadding(0, 24, 0, 24)
        }
        
        val popup = PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.elevation = 30f 

        val options = listOf(
            "Kişiler" to FacesActivity::class.java,
            "Akıllı Kategoriler" to SmartCategoriesActivity::class.java,
            "Anılar" to MemoriesActivity::class.java,
            "Alan Temizleyici" to SpaceCleanerActivity::class.java,
            "İnteraktif Harita" to InteractiveMapActivity::class.java
        )

        for ((title, activityClass) in options) {
            menuLayout.addView(TextView(context).apply {
                text = title
                setTextColor(primaryColor)
                textSize = 15f
                setPadding(64, 32, 64, 32)
                setOnClickListener {
                    popup.dismiss()
                    context.startActivity(Intent(context, activityClass))
                }
            })
        }

        menuLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        
        val xOffset = anchor.width - menuLayout.measuredWidth
        val yOffset = -(menuLayout.measuredHeight + anchor.height + 30)
        
        popup.showAsDropDown(anchor, xOffset, yOffset, Gravity.NO_GRAVITY)
    }
}
