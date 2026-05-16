package com.sahin.galerim

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.bottomsheet.BottomSheetDialog

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
        val currentTheme = prefs.getString("appTheme", "Sistem Teması")
        when (currentTheme) {
            "Açık Tema" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Koyu Tema", "Koyu Amoled Tema" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()

        val bgColor = ContextCompat.getColor(this, R.color.p_app_background)
        val settingsRoot = findViewById<LinearLayout>(R.id.settingsRoot)
        settingsRoot?.setBackgroundColor(if (currentTheme == "Koyu Amoled Tema") Color.BLACK else bgColor)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val switchAutoPlay = findViewById<SwitchMaterial>(R.id.switchAutoPlay)
        val switchTrash = findViewById<SwitchMaterial>(R.id.switchTrash)
        val tvCurrentTheme = findViewById<TextView>(R.id.tvCurrentTheme)

        tvCurrentTheme?.text = currentTheme

        switchAutoPlay.isChecked = prefs.getBoolean("autoPlayVideos", false)
        switchTrash.isChecked = prefs.getBoolean("useTrash", true)

        switchAutoPlay.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("autoPlayVideos", isChecked).apply()
        }

        switchTrash.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("useTrash", isChecked).apply()
        }

        findViewById<View>(R.id.layoutTheme)?.setOnClickListener {
            showThemeDialog(prefs, tvCurrentTheme)
        }

        findViewById<View>(R.id.layoutAbout).setOnClickListener {
            val dialogBgColor = ContextCompat.getColor(this, R.color.p_app_dialog_bg)
            val primaryTextColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
            val secondaryTextColor = ContextCompat.getColor(this, R.color.p_app_text_secondary)
            val accentColor = ContextCompat.getColor(this, R.color.p_app_accent)
            
            val isAmoled = currentTheme == "Koyu Amoled Tema"

            val dialog = android.app.Dialog(this)
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(64, 64, 64, 48)
                background = GradientDrawable().apply {
                    setColor(if (isAmoled) Color.parseColor("#000000") else dialogBgColor)
                    cornerRadius = 60f
                }
            }
            
            val title = TextView(this).apply {
                text = "Galerim"
                setTextColor(primaryTextColor)
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 32)
            }
            
            val message = TextView(this).apply {
                text = "Bu uygulama Şahin Bekâr tarafından geliştirilmiştir. Öneri, istek, sorun ve şikâyetleriniz için yazabilirsiniz."
                setTextColor(secondaryTextColor)
                textSize = 15f
                setPadding(0, 0, 0, 16)
                setLineSpacing(6f, 1f)
            }
            
            val email = TextView(this).apply {
                text = "sahinbekar@hotmail.com"
                setTextColor(accentColor)
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 32)
                setOnClickListener {
                    try {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:sahinbekar@hotmail.com")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@SettingsActivity, "E-posta uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            val version = TextView(this).apply {
                text = "Sürüm 1.0"
                setTextColor(Color.parseColor("#888888"))
                textSize = 14f
                setPadding(0, 0, 0, 32)
            }
            
            val btnOk = TextView(this).apply {
                text = "TAMAM"
                setTextColor(accentColor)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.END
                setPadding(32, 16, 16, 16)
                setOnClickListener { dialog.dismiss() }
            }
            
            layout.addView(title)
            layout.addView(message)
            layout.addView(email)
            layout.addView(version)
            layout.addView(btnOk)
            
            dialog.setContentView(layout)
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            
            val displayMetrics = resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.85).toInt()
            dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            
            dialog.show()
        }
    }

    private fun showThemeDialog(prefs: android.content.SharedPreferences, tvCurrentTheme: TextView?) {
        val dialogBgColor = ContextCompat.getColor(this, R.color.p_app_dialog_bg)
        val primaryTextColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
        val accentColor = ContextCompat.getColor(this, R.color.p_app_accent)
        
        val dialog = BottomSheetDialog(this)
        val isAmoled = prefs.getString("appTheme", "Sistem Teması") == "Koyu Amoled Tema"
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 64)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 60f
                setColor(if (isAmoled) Color.parseColor("#000000") else dialogBgColor)
            }
        }

        val title = TextView(this).apply {
            text = "Tema Seçin"
            setTextColor(primaryTextColor)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(24, 0, 0, 40)
        }
        layout.addView(title)

        val themes = listOf("Sistem Teması", "Koyu Tema", "Açık Tema", "Koyu Amoled Tema")
        val currentTheme = prefs.getString("appTheme", "Sistem Teması")

        for (theme in themes) {
            val tv = TextView(this).apply {
                text = theme
                textSize = 16f
                setTextColor(if (theme == currentTheme) accentColor else primaryTextColor)
                setPadding(24, 32, 24, 32)
                setOnClickListener {
                    dialog.dismiss()
                    if (theme != currentTheme) {
                        prefs.edit()
                            .putString("appTheme", theme)
                            .putString("bg_type", "default")
                            .apply()
                        tvCurrentTheme?.text = theme
                        recreate() 
                    }
                }
            }
            layout.addView(tv)
        }

        dialog.setContentView(layout)
        (layout.parent as View).setBackgroundColor(Color.TRANSPARENT)
        dialog.show()
    }
}