package com.sahin.galerim

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sahin.galerim.utils.BiometricHelper

fun MainActivity.showMainMoreMenu(anchor: View) {
    val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
    val menuBgColor = getMenuBgColor()

    val menuLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 50f
            setColor(menuBgColor)
        }
        setPadding(0, 12, 0, 12)
    }

    val popup = PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
    popup.elevation = 30f

    val options = listOf("Sırala", "Görünüm")

    for (opt in options) {
        menuLayout.addView(TextView(this).apply {
            text = opt
            setTextColor(primaryColor)
            textSize = 16f
            setPadding(80, 32, 80, 32)
            setOnClickListener {
                popup.dismiss()
                if (opt == "Sırala") {
                    showSortBottomSheet()
                } else {
                    showAppearanceBottomSheet()
                }
            }
        })
    }

    popup.showAsDropDown(anchor, 0, 10, Gravity.END)
}

fun MainActivity.showTrashMoreMenu(anchor: View) {
    val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)
    val menuBgColor = getMenuBgColor()

    val menuLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 50f
            setColor(menuBgColor)
        }
        setPadding(0, 12, 0, 12)
    }

    val popup = PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
    popup.elevation = 30f

    menuLayout.addView(TextView(this).apply {
        text = "Boşalt"
        setTextColor(primaryColor)
        textSize = 15f
        setPadding(80, 24, 80, 24)
        setOnClickListener {
            popup.dismiss()
            if (MainActivity.trashList.isNotEmpty()) {
                showMultiDeleteConfirmationDialog(MainActivity.trashList.toList())
            }
        }
    })

    popup.showAsDropDown(anchor, 0, 10, Gravity.END)
}

fun MainActivity.showGalleryMenuBottomSheet() {
    val menuBgColor = getMenuBgColor()
    bottomSheetMenu = BottomSheetDialog(this)
    val view = layoutInflater.inflate(R.layout.bottom_sheet_gallery_menu, null)

    view.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadii = floatArrayOf(60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f)
        setColor(menuBgColor)
    }

    bottomSheetMenu?.setContentView(view)
    bottomSheetMenu?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)

    view.findViewById<View>(R.id.menu_item_places)?.setOnClickListener {
        resetStates()
        isShowingPlaces = true
        mainTitle.text = "Yerler"
        bottomSheetMenu?.dismiss()
        allRecycler.visibility = View.VISIBLE
        albumsRecycler.visibility = View.GONE
        loadDisplayedList()
    }

    view.findViewById<View>(R.id.menu_item_locations)?.setOnClickListener {
        resetStates()
        isShowingLocations = true
        mainTitle.text = "Konumlar"
        bottomSheetMenu?.dismiss()
        allRecycler.visibility = View.GONE
        albumsRecycler.visibility = View.VISIBLE
        loadDisplayedList()
    }

    view.findViewById<View>(R.id.menu_item_favorites)?.setOnClickListener {
        resetStates()
        isShowingFavorites = true
        bottomSheetMenu?.dismiss()
        loadDisplayedList()
    }

    view.findViewById<View>(R.id.menu_item_trash)?.setOnClickListener {
        resetStates()
        isShowingTrash = true
        bottomSheetMenu?.dismiss()
        loadDisplayedList()
    }

    view.findViewById<View>(R.id.menu_item_settings)?.setOnClickListener {
        bottomSheetMenu?.dismiss()
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    view.findViewById<View>(R.id.menu_item_fix_dates)?.setOnClickListener {
        bottomSheetMenu?.dismiss()
        repairMediaDates()
    }

    view.findViewById<View>(R.id.menu_item_hidden)?.setOnClickListener {
        bottomSheetMenu?.dismiss()
        BiometricHelper.authenticate(this,
            onSuccess = {
                startActivity(Intent(this, HiddenMediaActivity::class.java))
            },
            onError = { hata ->
                Toast.makeText(this, "Güvenlik Doğrulanamadı: $hata", Toast.LENGTH_SHORT).show()
            }
        )
    }

    bottomSheetMenu?.setOnDismissListener {
        if (!isShowingTrash && !isShowingFavorites && !isShowingPlaces && !isShowingLocations && !isSearchMode && bottomTabLayout.selectedTabPosition == 3) {
            bottomTabLayout.getTabAt(previousTabPosition)?.select()
        }
    }

    bottomSheetMenu?.show()
}

fun MainActivity.showSortBottomSheet() {
    val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
    var currentSort = prefs.getString("sort_order", "Değiştirilme (önce yeni)")
    val menuBgColor = getMenuBgColor()
    val accentColor = getAccentColor()
    val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)

    val isAw = accentColor == Color.WHITE || accentColor == Color.parseColor("#FFFFFF")
    val currentTheme = prefs.getString("appTheme", "Sistem Teması")
    val isDarkTheme = currentTheme == "Koyu Tema" || currentTheme == "Koyu Amoled Tema" || (currentTheme == "Sistem Teması" && (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)
    val displayAccent = if (isAw && !isDarkTheme) Color.BLACK else accentColor

    sortBottomSheetMenu = BottomSheetDialog(this)

    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 48, 0, 48)
        background = GradientDrawable().apply {
            setColor(menuBgColor)
            cornerRadii = floatArrayOf(60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f)
        }
    }

    val title = TextView(this).apply {
        text = "SIRALAMA ÖLÇÜTÜ"
        setTextColor(Color.parseColor("#888888"))
        textSize = 13f
        setPadding(64, 0, 0, 32)
    }
    layout.addView(title)

    val options = listOf("Dosya adı (A - Z)", "Dosya adı (Z - A)", "Değiştirilme (önce yeni)", "Değiştirilme (önce eski)", "Tür (A - Z)", "Tür (Z - A)", "Boyut (önce en büyük)", "Boyut (önce en küçük)")
    val itemViews = mutableListOf<Pair<String, Pair<TextView, TextView>>>()

    for (opt in options) {
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(64, 32, 64, 32)

            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
            setBackgroundResource(typedValue.resourceId)

            isClickable = true
            isFocusable = true
        }

        val icon = TextView(this).apply {
            text = "✓"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(56, 56)
            params.marginEnd = 32
            layoutParams = params

            if (opt == currentSort) {
                setTextColor(displayAccent)
                visibility = View.VISIBLE
            } else {
                visibility = View.INVISIBLE
            }
        }

        val text = TextView(this).apply {
            text = opt
            textSize = 15f
            setTextColor(if (opt == currentSort) displayAccent else primaryColor)
        }

        itemLayout.setOnClickListener {
            prefs.edit().putString("sort_order", opt).apply()
            currentSort = opt

            itemViews.forEach { (o, views) ->
                val (i, t) = views
                if (o == opt) {
                    i.visibility = View.VISIBLE
                    i.setTextColor(displayAccent)
                    t.setTextColor(displayAccent)
                } else {
                    i.visibility = View.INVISIBLE
                    t.setTextColor(primaryColor)
                }
            }

            loadAllMedia()
        }

        itemViews.add(Pair(opt, Pair(icon, text)))
        itemLayout.addView(icon)
        itemLayout.addView(text)
        layout.addView(itemLayout)
    }

    sortBottomSheetMenu?.setContentView(layout)
    sortBottomSheetMenu?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    sortBottomSheetMenu?.show()
}

fun MainActivity.showAppearanceBottomSheet() {
    val prefs = getSharedPreferences("GalleryPrefs", Context.MODE_PRIVATE)
    val menuBgColor = getMenuBgColor()
    var accentColor = getAccentColor()
    val primaryColor = ContextCompat.getColor(this, R.color.p_app_text_primary)

    appearanceBottomSheetMenu = BottomSheetDialog(this)

    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 48, 0, 48)
        background = GradientDrawable().apply {
            setColor(menuBgColor)
            cornerRadii = floatArrayOf(60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f)
        }
    }

    layout.addView(TextView(this).apply {
        text = "Tema"
        setTextColor(Color.parseColor("#888888"))
        textSize = 14f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(64, 24, 0, 24)
    })

    val themes = listOf("Sistem Teması", "Koyu Tema", "Açık Tema", "Koyu Amoled Tema")
    val themeScroll = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
    val themeLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(48, 0, 48, 48)
    }
    val themeButtons = mutableListOf<Pair<String, AppCompatButton>>()

    themes.forEach { t ->
        val btnText = when(t) {
            "Sistem Teması" -> "Sistem"
            "Koyu Tema" -> "Koyu"
            "Açık Tema" -> "Açık"
            "Koyu Amoled Tema" -> "Koyu Amoled"
            else -> t
        }

        val btn = AppCompatButton(this).apply {
            text = btnText
            textSize = 13f
            isAllCaps = false
            val isSelected = prefs.getString("appTheme", "Sistem Teması").equals(t, true)
            val isAccentWhite = accentColor == Color.WHITE || accentColor == Color.parseColor("#FFFFFF")

            setTextColor(if (isSelected) { if (isAccentWhite) Color.BLACK else Color.WHITE } else primaryColor)

            background = GradientDrawable().apply {
                cornerRadius = 30f
                setColor(if (isSelected) accentColor else Color.parseColor("#22888888"))
            }

            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 100).apply {
                setMargins(12, 0, 12, 0)
            }

            setPadding(32, 0, 32, 0)

            setOnClickListener {
                prefs.edit().putString("appTheme", t).apply()
                appearanceBottomSheetMenu?.dismiss()
                recreate()
            }
        }
        themeButtons.add(Pair(t, btn))
        themeLayout.addView(btn)
    }

    themeScroll.addView(themeLayout)
    layout.addView(themeScroll)

    layout.addView(TextView(this).apply {
        text = "Izgara Görünümü"
        setTextColor(Color.parseColor("#888888"))
        textSize = 14f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(64, 24, 0, 24)
    })

    val columns = listOf(2, 3, 4, 5)
    val gridLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(48, 0, 48, 48)
        gravity = Gravity.CENTER_HORIZONTAL
    }
    val gridButtons = mutableListOf<Pair<Int, AppCompatButton>>()

    columns.forEach { c ->
        val btn = AppCompatButton(this).apply {
            text = "$c sütun"
            textSize = 13f
            isAllCaps = false
            val isSelected = prefs.getInt("gridSpanCount", 4) == c
            val isAccentWhite = accentColor == Color.WHITE || accentColor == Color.parseColor("#FFFFFF")

            setTextColor(if (isSelected) { if (isAccentWhite) Color.BLACK else Color.WHITE } else primaryColor)

            background = GradientDrawable().apply {
                cornerRadius = 30f
                setColor(if (isSelected) accentColor else Color.parseColor("#22888888"))
            }

            layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply {
                setMargins(12, 0, 12, 0)
            }

            setOnClickListener {
                prefs.edit().putInt("gridSpanCount", c).apply()
                updateGridSpanCount(c)

                gridButtons.forEach { (col, b) ->
                    val isSel = col == c
                    val isAw = accentColor == Color.WHITE || accentColor == Color.parseColor("#FFFFFF")
                    b.setTextColor(if (isSel) { if (isAw) Color.BLACK else Color.WHITE } else primaryColor)
                    b.background = GradientDrawable().apply {
                        cornerRadius = 30f
                        setColor(if (isSel) accentColor else Color.parseColor("#22888888"))
                    }
                }
            }
        }
        gridButtons.add(Pair(c, btn))
        gridLayout.addView(btn)
    }

    layout.addView(gridLayout)

    layout.addView(TextView(this).apply {
        text = "Vurgu Rengi"
        setTextColor(Color.parseColor("#888888"))
        textSize = 14f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(64, 24, 0, 24)
    })

    val colors = mapOf(
        "Mavi" to "#5C94FF",
        "Turkuaz" to "#00CED1",
        "Kırmızı" to "#FF5252",
        "Mor" to "#9C27B0",
        "Yeşil" to "#4CAF50",
        "Turuncu" to "#FF9800",
        "Beyaz" to "#FFFFFF",
        "Eflatun" to "#EE82EE"
    )

    val colorScroll = android.widget.HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
    }

    val colorLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(48, 0, 48, 24)
    }

    val colorFrames = mutableListOf<Pair<String, FrameLayout>>()

    colors.forEach { (name, hex) ->
        val colorVal = Color.parseColor(hex)

        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(110, 110).apply {
                setMargins(12, 0, 12, 0)
            }
        }

        val circle = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorVal)
            }
            layoutParams = FrameLayout.LayoutParams(80, 80, Gravity.CENTER)
        }

        if (hex == prefs.getString("accentColor", "#5C94FF")) {
            frame.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(6, colorVal)
            }
        }

        frame.setOnClickListener {
            prefs.edit().putString("accentColor", hex).apply()
            accentColor = colorVal
            updateAccentColor(colorVal)

            colorFrames.forEach { (h, f) ->
                if (h == hex) {
                    f.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setStroke(6, Color.parseColor(h))
                    }
                } else {
                    f.background = null
                }
            }

            val currentGrid = prefs.getInt("gridSpanCount", 4)
            gridButtons.forEach { (col, b) ->
                val isSel = col == currentGrid
                val isAw = accentColor == Color.WHITE || accentColor == Color.parseColor("#FFFFFF")
                b.setTextColor(if (isSel) { if (isAw) Color.BLACK else Color.WHITE } else primaryColor)
                b.background = GradientDrawable().apply {
                    cornerRadius = 30f
                    setColor(if (isSel) accentColor else Color.parseColor("#22888888"))
                }
            }

            val currentTheme = prefs.getString("appTheme", "Sistem Teması")
            themeButtons.forEach { (tName, b) ->
                val isSel = tName == currentTheme
                val isAw = accentColor == Color.WHITE || accentColor == Color.parseColor("#FFFFFF")
                b.setTextColor(if (isSel) { if (isAw) Color.BLACK else Color.WHITE } else primaryColor)
                b.background = GradientDrawable().apply {
                    cornerRadius = 30f
                    setColor(if (isSel) accentColor else Color.parseColor("#22888888"))
                }
            }
        }

        frame.addView(circle)
        colorFrames.add(Pair(hex, frame))
        colorLayout.addView(frame)
    }

    colorScroll.addView(colorLayout)
    layout.addView(colorScroll)

    appearanceBottomSheetMenu?.setContentView(layout)
    appearanceBottomSheetMenu?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    appearanceBottomSheetMenu?.show()
}
