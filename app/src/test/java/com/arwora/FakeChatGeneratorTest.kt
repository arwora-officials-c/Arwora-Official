package com.arwora

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import com.arwora.feature.fakechat.ChatPlatform
import com.arwora.feature.fakechat.defaultMessagesForPlatform
import com.arwora.feature.fakechat.formatVoiceDuration
import com.arwora.ui.screens.FakeChatGeneratorScreen
import com.arwora.ui.theme.NameGenProTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class FakeChatGeneratorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chatPlatformMappingAndVoiceDurationFormatWorkCleanly() {
        assertEquals(ChatPlatform.WHATSAPP, ChatPlatform.fromLabel("WhatsApp"))
        assertEquals(ChatPlatform.INSTAGRAM, ChatPlatform.fromLabel("Instagram"))
        assertEquals(ChatPlatform.PINTEREST, ChatPlatform.fromLabel("Pinterest"))
        assertEquals("0:18", formatVoiceDuration(18))
        assertEquals("1:05", formatVoiceDuration(65))
        assertTrue(defaultMessagesForPlatform(ChatPlatform.WHATSAPP).isNotEmpty())
        assertTrue(defaultMessagesForPlatform(ChatPlatform.INSTAGRAM).isNotEmpty())
        assertTrue(defaultMessagesForPlatform(ChatPlatform.PINTEREST).isNotEmpty())
    }

    @Test
    fun whatsappThemeRendersHeaderWallpaperAndAddedVoiceCallMessage() {
        composeTestRule.setContent {
            val navController = rememberNavController()
            NameGenProTheme(darkTheme = true) {
                FakeChatGeneratorScreen(
                    navController = navController,
                    initialPlatform = "WhatsApp"
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("platform_tile_whatsapp").assertIsDisplayed()
        composeTestRule.onNodeWithTag("platform_tile_instagram").assertIsDisplayed()
        composeTestRule.onNodeWithTag("platform_tile_pinterest").assertIsDisplayed()

        // Scroll LazyColumn to composer card (index 3) and add a Voice Call message
        composeTestRule.onNodeWithTag("fake_chat_edit_list").performScrollToIndex(3)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("message_type_chip_voice_call")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("add_message_button")
            .performScrollTo()
            .performClick()

        // Switch to Live Preview
        composeTestRule.onNodeWithTag("fake_chat_tab_preview").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("whatsapp_chat_preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sarah Jenkins").assertIsDisplayed()
        composeTestRule.onNodeWithText("0:24").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Voice call").performScrollTo().assertIsDisplayed()

        // Toggle preview Light -> Dark mode
        composeTestRule.onNodeWithTag("preview_theme_mode_chip")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("Dark Mode").assertIsDisplayed()
    }

    @Test
    fun instagramThemeRendersQuotedReplySharedVideoAndSeenFooter() {
        composeTestRule.setContent {
            val navController = rememberNavController()
            NameGenProTheme(darkTheme = true) {
                FakeChatGeneratorScreen(
                    navController = navController,
                    initialPlatform = "Instagram"
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fake_chat_tab_preview").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("instagram_chat_preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Business chat · Active now").assertIsDisplayed()
        composeTestRule.onNodeWithText("You replied to Sarah Jenkins").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("reel_behind_scenes.mp4").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("0:16").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Seen").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pinterestThemeRendersProfileIntroCardFollowButtonAndDeletedCapsule() {
        composeTestRule.setContent {
            val navController = rememberNavController()
            NameGenProTheme(darkTheme = true) {
                FakeChatGeneratorScreen(
                    navController = navController,
                    initialPlatform = "Pinterest"
                )
            }
        }

        composeTestRule.waitForIdle()

        // Scroll LazyColumn to composer card (index 3) and add a custom text message
        composeTestRule.onNodeWithTag("fake_chat_edit_list").performScrollToIndex(3)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("composer_message_text_input")
            .performScrollTo()
            .performTextInput("Pinned your studio wall!")
        composeTestRule.onNodeWithTag("add_message_button")
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithTag("fake_chat_tab_preview").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pinterest_chat_preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("This could be the beginning of something good")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("You deleted a message")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Pinned your studio wall!")
            .performScrollTo()
            .assertIsDisplayed()

        // Interactive Follow button toggles to Following
        composeTestRule.onNodeWithText("Follow").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pinterest_follow_button")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("Following").assertIsDisplayed()
    }
}
