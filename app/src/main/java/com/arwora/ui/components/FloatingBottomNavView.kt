package com.arwora.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.arwora.R
import com.google.android.material.card.MaterialCardView

/**
 * Custom Floating (Detached) Bottom Navigation Bar inspired by modern detached pill designs.
 * Features 5 distinct items: Home, Search, prominent centered overflowing Create FAB, Inbox, Profile.
 */
class FloatingBottomNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class NavItem(val id: Int) {
        HOME(0),
        SEARCH(1),
        CREATE(2),
        INBOX(3),
        PROFILE(4)
    }

    private var activeColor: Int = ContextCompat.getColor(context, R.color.nav_active_color)
    private var inactiveColor: Int = ContextCompat.getColor(context, R.color.nav_inactive_color)

    // View references
    private val itemHome: LinearLayout
    private val ivHome: ImageView
    private val tvHome: TextView

    private val itemSearch: LinearLayout
    private val ivSearch: ImageView
    private val tvSearch: TextView

    private val btnCreate: MaterialCardView
    private val tvCreate: TextView

    private val itemInbox: LinearLayout
    private val ivInbox: ImageView
    private val tvInbox: TextView

    private val itemProfile: LinearLayout
    private val ivProfile: ImageView
    private val tvProfile: TextView

    var selectedItem: NavItem = NavItem.HOME
        private set

    private var onItemSelectedListener: ((NavItem) -> Boolean)? = null
    private var onCreateClickListener: (() -> Unit)? = null

    init {
        clipChildren = false
        clipToPadding = false

        LayoutInflater.from(context).inflate(R.layout.bottom_nav_floating, this, true)

        itemHome = findViewById(R.id.nav_item_home)
        ivHome = findViewById(R.id.iv_nav_home)
        tvHome = findViewById(R.id.tv_nav_home)

        itemSearch = findViewById(R.id.nav_item_search)
        ivSearch = findViewById(R.id.iv_nav_search)
        tvSearch = findViewById(R.id.tv_nav_search)

        btnCreate = findViewById(R.id.btn_nav_create)
        tvCreate = findViewById(R.id.tv_nav_create)

        itemInbox = findViewById(R.id.nav_item_inbox)
        ivInbox = findViewById(R.id.iv_nav_inbox)
        tvInbox = findViewById(R.id.tv_nav_inbox)

        itemProfile = findViewById(R.id.nav_item_profile)
        ivProfile = findViewById(R.id.iv_nav_profile)
        tvProfile = findViewById(R.id.tv_nav_profile)

        setupClickListeners()
        updateItemVisuals(selectedItem, animate = false)
    }

    private fun setupClickListeners() {
        itemHome.setOnClickListener {
            handleItemSelection(NavItem.HOME)
        }

        itemSearch.setOnClickListener {
            handleItemSelection(NavItem.SEARCH)
        }

        btnCreate.setOnClickListener {
            animateFabClick()
            onCreateClickListener?.invoke()
            onItemSelectedListener?.invoke(NavItem.CREATE)
        }

        itemInbox.setOnClickListener {
            handleItemSelection(NavItem.INBOX)
        }

        itemProfile.setOnClickListener {
            handleItemSelection(NavItem.PROFILE)
        }
    }

    private fun handleItemSelection(item: NavItem) {
        if (selectedItem == item) return
        val handled = onItemSelectedListener?.invoke(item) ?: true
        if (handled) {
            selectItem(item)
        }
    }

    /**
     * Programmatically select an item and update visual highlights.
     */
    fun selectItem(item: NavItem) {
        if (item == NavItem.CREATE) return
        val previous = selectedItem
        selectedItem = item
        updateItemVisuals(item, animate = true)
    }

    /**
     * Set listener for navigation item selection.
     */
    fun setOnItemSelectedListener(listener: (NavItem) -> Boolean) {
        this.onItemSelectedListener = listener
    }

    /**
     * Set listener specifically for the prominent center "Create" FAB.
     */
    fun setOnCreateClickListener(listener: () -> Unit) {
        this.onCreateClickListener = listener
    }

    private fun updateItemVisuals(activeItem: NavItem, animate: Boolean) {
        applyVisualState(itemHome, ivHome, tvHome, activeItem == NavItem.HOME, animate)
        applyVisualState(itemSearch, ivSearch, tvSearch, activeItem == NavItem.SEARCH, animate)
        applyVisualState(itemInbox, ivInbox, tvInbox, activeItem == NavItem.INBOX, animate)
        applyVisualState(itemProfile, ivProfile, tvProfile, activeItem == NavItem.PROFILE, animate)
    }

    private fun applyVisualState(
        container: View,
        icon: ImageView,
        label: TextView,
        isActive: Boolean,
        animate: Boolean
    ) {
        val targetColor = if (isActive) activeColor else inactiveColor
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(targetColor))
        label.setTextColor(targetColor)
        label.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)

        if (animate && isActive) {
            container.scaleX = 0.85f
            container.scaleY = 0.85f
            container.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(2.0f))
                .start()
        }
    }

    private fun animateFabClick() {
        btnCreate.scaleX = 0.88f
        btnCreate.scaleY = 0.88f
        btnCreate.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(200)
            .setInterpolator(OvershootInterpolator(2.5f))
            .start()
    }
}
