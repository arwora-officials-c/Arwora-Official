package com.arwora

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.arwora.data.datastore.SettingsRepository
import com.arwora.data.datastore.dataStore
import com.arwora.data.db.AppDatabase
import com.arwora.data.repository.FavoriteRepository
import com.arwora.data.repository.HistoryRepository
import com.arwora.data.repository.SavedTextRepository
import com.arwora.ui.MainViewModel
import com.arwora.ui.screens.StylishTextGenScreen
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.FontStyles
import com.arwora.utils.StylishTextEngine
import com.arwora.utils.SymbolCategory
import com.arwora.utils.SymbolPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StylishTextDecoratorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun symbolCatalogHasAtLeast150UniqueSymbolsAndAllPromptExamples() {
        val allSymbols = StylishTextEngine.symbolsFor(SymbolCategory.ALL)
        assertTrue("Expected at least 150 symbols, found ${allSymbols.size}", allSymbols.size >= 150)

        val uniqueCount = allSymbols.map { it.symbol }.toSet().size
        assertEquals("Symbol catalog must not contain duplicates", allSymbols.size, uniqueCount)

        for (category in SymbolCategory.entries) {
            assertTrue("Category ${category.label} must not be empty", StylishTextEngine.symbolsFor(category).isNotEmpty())
        }

        val requiredExamples = listOf(
            "♛", "♔", "⚡", "★", "☆", "❖", "么", "ツ", "×͜×", "⸙",
            "༒", "꧁", "꧂", "亗", "〆", "乡", "✿", "❀", "♡", "✦",
            "✧", "⚔️", "☠️", "◈", "➢", "➤", "『", "』", "【", "】"
        )
        val availableSet = allSymbols.map { it.symbol }.toSet()
        for (expected in requiredExamples) {
            assertTrue("Missing required symbol '$expected'", expected in availableSet)
        }
    }

    @Test
    fun applySymbolToTextHandlesFirstMiddleLastAndSurrogatePairs() {
        // Empty symbol leaves styled text unchanged
        assertEquals("𝕬𝖗𝖜𝖔𝖗𝖆", StylishTextEngine.applySymbolToText("𝕬𝖗𝖜𝖔𝖗𝖆", "", SymbolPosition.FIRST))
        assertEquals("", StylishTextEngine.applySymbolToText("", "⚡", SymbolPosition.MIDDLE))

        // First & Last
        assertEquals("♛ Arwora", StylishTextEngine.applySymbolToText("Arwora", "♛", SymbolPosition.FIRST))
        assertEquals("Arwora ♛", StylishTextEngine.applySymbolToText("Arwora", "♛", SymbolPosition.LAST))

        // Middle with space
        assertEquals(
            "Arwora ⚡ Toolkit",
            StylishTextEngine.applySymbolToText("Arwora Toolkit", "⚡", SymbolPosition.MIDDLE)
        )

        // Middle on multi-byte surrogate pair single word (Bold Fraktur: "𝕸𝖆𝖍𝖋𝖚𝖟")
        val frakturName = FontStyles.apply("Mahfuz", "Bold Fraktur")
        val decoratedMiddle = StylishTextEngine.applySymbolToText(frakturName, "⚡", SymbolPosition.MIDDLE)
        assertEquals(
            "${FontStyles.apply("Mah", "Bold Fraktur")} ⚡ ${FontStyles.apply("fuz", "Bold Fraktur")}",
            decoratedMiddle
        )
    }

    @Test
    fun generateAllVariantsProducesAllFontStylesWithDecoration() {
        val variants = StylishTextEngine.generateAllVariants(
            inputText = "Arwora",
            selectedSymbol = "★",
            position = SymbolPosition.LAST,
            prioritizedStyle = "Bold"
        )
        assertEquals(FontStyles.styles.size, variants.size)
        assertEquals("Bold", variants.first().styleName)
        assertEquals("${FontStyles.apply("Arwora", "Bold")} ★", variants.first().decoratedText)
        assertTrue(StylishTextEngine.generateAllVariants("   ", "★", SymbolPosition.FIRST).isEmpty())
    }

    @Test
    fun stylishTextScreenUiDecoratesLiveCopiesAndSharesExactString() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val viewModel = MainViewModel(
            HistoryRepository(db.historyDao()),
            FavoriteRepository(db.favoriteDao()),
            SettingsRepository(context.dataStore),
            SavedTextRepository(db.savedTextDao())
        )

        try {
            composeTestRule.setContent {
                NameGenProTheme(darkTheme = true) {
                    val navController = rememberNavController()
                    StylishTextGenScreen(navController = navController, viewModel = viewModel)
                }
            }

            composeTestRule.waitForIdle()

            // Verify Symbol Control Panel and controls are present
            composeTestRule.onNodeWithTag("symbol_control_panel").assertIsDisplayed()
            composeTestRule.onNodeWithTag("symbol_position_first").assertIsDisplayed()
            composeTestRule.onNodeWithTag("symbol_position_middle").assertIsDisplayed()
            composeTestRule.onNodeWithTag("symbol_position_last").assertIsDisplayed()
            composeTestRule.onNodeWithTag("clear_symbol_button").assertIsDisplayed()

            composeTestRule.onNodeWithTag("stylish_variants_list").performScrollToKey("empty_state")
            composeTestRule.onNodeWithTag("stylish_empty_state").assertIsDisplayed()

            // Type input text -> generates variants live on Dispatchers.Default
            composeTestRule.onNodeWithTag("stylish_text_input").performTextInput("Arwora")
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                composeTestRule.onAllNodesWithTag("styled_text_Default")
                    .fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("stylish_variants_list").performScrollToKey("Default")
            composeTestRule.onNodeWithTag("styled_text_Default").assertTextEquals("Arwora")

            // Select Gaming & Pro category and choose "⚡" symbol -> First mode updates live
            composeTestRule.onNodeWithTag("stylish_variants_list").performScrollToKey("symbol_control_panel")
            composeTestRule.onNodeWithTag("symbol_category_gaming_pro").performScrollTo().performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("symbol_tile_⚡").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("stylish_variants_list").performScrollToKey("Default")
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                runCatching {
                    composeTestRule.onNodeWithTag("styled_text_Default").assertTextEquals("⚡ Arwora")
                }.isSuccess
            }

            // Switch to Middle mode -> inserts in middle ("Arw ⚡ ora")
            composeTestRule.onNodeWithTag("stylish_variants_list").performScrollToKey("symbol_control_panel")
            composeTestRule.onNodeWithTag("symbol_position_middle").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("stylish_variants_list").performScrollToKey("Default")
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                runCatching {
                    composeTestRule.onNodeWithTag("styled_text_Default").assertTextEquals("Arw ⚡ ora")
                }.isSuccess
            }

            // Switch to Last mode -> appends at end
            composeTestRule.onNodeWithTag("stylish_variants_list").performScrollToKey("symbol_control_panel")
            composeTestRule.onNodeWithTag("symbol_position_last").performClick()
            composeTestRule.waitForIdle()

            val expectedBoldLast = "${FontStyles.apply("Arwora", "Bold")} ⚡"
            composeTestRule.onNodeWithTag("stylish_variants_list").performScrollToKey("Bold")
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                runCatching {
                    composeTestRule.onNodeWithTag("styled_text_Bold").assertTextEquals(expectedBoldLast)
                }.isSuccess
            }

            // Tap Copy on Bold card -> clipboard receives exact decorated string
            composeTestRule.onNodeWithTag("copy_styled_Bold").performScrollTo().performClick()
            composeTestRule.waitForIdle()

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(
                expectedBoldLast,
                clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            )

            // Tap Share on Bold card -> launches ACTION_SEND chooser with exact decorated string
            val app = ApplicationProvider.getApplicationContext<Application>()
            composeTestRule.onNodeWithTag("share_styled_Bold").performScrollTo().performClick()
            composeTestRule.waitForIdle()

            val launchedChooser = shadowOf(app).nextStartedActivity
            assertNotNull("Expected share chooser intent to be launched", launchedChooser)
            assertEquals(Intent.ACTION_CHOOSER, launchedChooser.action)
            @Suppress("DEPRECATION")
            val wrappedSend = launchedChooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertNotNull(wrappedSend)
            assertEquals(Intent.ACTION_SEND, wrappedSend?.action)
            assertEquals(expectedBoldLast, wrappedSend?.getStringExtra(Intent.EXTRA_TEXT))

            // Tap Clear Symbol -> removes symbol from all live cards
            composeTestRule.onNodeWithTag("stylish_variants_list").performScrollToKey("symbol_control_panel")
            composeTestRule.onNodeWithTag("clear_symbol_button").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("stylish_variants_list").performScrollToKey("Bold")
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                runCatching {
                    composeTestRule.onNodeWithTag("styled_text_Bold")
                        .assertTextEquals(FontStyles.apply("Arwora", "Bold"))
                }.isSuccess
            }
        } finally {
            db.close()
        }
    }
}
