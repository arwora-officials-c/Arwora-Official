package com.arwora.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Jetpack Compose wrapper for the custom Floating (Detached) Bottom Navigation Bar.
 * Respects windowInsets and navigationBarsPadding so it floats cleanly above system bars.
 */
@Composable
fun FloatingBottomNavigationBar(
    selectedItem: FloatingBottomNavView.NavItem,
    onItemSelected: (FloatingBottomNavView.NavItem) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .wrapContentHeight(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                FloatingBottomNavView(context).apply {
                    setOnItemSelectedListener { item ->
                        onItemSelected(item)
                        true
                    }
                    setOnCreateClickListener {
                        onCreateClick()
                    }
                }
            },
            update = { view ->
                if (view.selectedItem != selectedItem) {
                    view.selectItem(selectedItem)
                }
            }
        )
    }
}
