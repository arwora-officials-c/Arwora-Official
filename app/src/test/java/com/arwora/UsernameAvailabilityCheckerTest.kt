package com.arwora

import com.arwora.utils.AvailabilityState
import com.arwora.utils.UsernameAvailabilityChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameAvailabilityCheckerTest {

    @Test
    fun sanitizeHandleStripsPrefixAndWhitespace() {
        assertEquals("Arwora_Dev", UsernameAvailabilityChecker.sanitizeHandle(" @Arwora_Dev "))
        assertEquals("novauser", UsernameAvailabilityChecker.sanitizeHandle("@nova user"))
        assertEquals("nova_user", UsernameAvailabilityChecker.sanitizeHandle("@nova_user"))
        assertEquals("", UsernameAvailabilityChecker.sanitizeHandle("   @  "))
    }

    @Test
    fun supportedPlatformsIncludeRequiredMajorServicesAndUrls() {
        val ids = UsernameAvailabilityChecker.supportedPlatforms.map { it.id }.toSet()
        assertTrue(ids.contains("instagram"))
        assertTrue(ids.contains("tiktok"))
        assertTrue(ids.contains("github"))
        assertTrue(ids.contains("telegram"))
        assertTrue(ids.contains("youtube"))
        assertTrue(ids.contains("twitter_x"))

        val gh = UsernameAvailabilityChecker.supportedPlatforms.first { it.id == "github" }
        assertEquals("https://github.com/octocat", gh.profileUrl("octocat"))
        assertEquals("https://github.com/signup", gh.targetActionUrl(AvailabilityState.AVAILABLE, "octocat"))
        assertEquals("https://github.com/octocat", gh.targetActionUrl(AvailabilityState.TAKEN, "octocat"))
    }

    @Test
    fun classifyPlatformResponseDetectsAvailableTakenAndRateLimit() {
        val github = UsernameAvailabilityChecker.supportedPlatforms.first { it.id == "github" }
        assertEquals(
            AvailabilityState.AVAILABLE,
            UsernameAvailabilityChecker.classifyPlatformResponse(github, "arwora_free_991", 404, "").state
        )
        assertEquals(
            AvailabilityState.TAKEN,
            UsernameAvailabilityChecker.classifyPlatformResponse(github, "torvalds", 200, "<html>torvalds</html>").state
        )
        assertEquals(
            AvailabilityState.UNVERIFIED,
            UsernameAvailabilityChecker.classifyPlatformResponse(github, "torvalds", 429, "").state
        )

        val telegram = UsernameAvailabilityChecker.supportedPlatforms.first { it.id == "telegram" }
        assertEquals(
            AvailabilityState.TAKEN,
            UsernameAvailabilityChecker.classifyPlatformResponse(
                telegram,
                "telegram",
                200,
                "<div class=\"tgme_page_title\"><span>Telegram</span></div>"
            ).state
        )
        assertEquals(
            AvailabilityState.AVAILABLE,
            UsernameAvailabilityChecker.classifyPlatformResponse(
                telegram,
                "unused_handle_xyz",
                200,
                "<div class=\"tgme_page_icon\"></div><div>If you have Telegram, you can contact @unused_handle_xyz</div>"
            ).state
        )

        val youtube = UsernameAvailabilityChecker.supportedPlatforms.first { it.id == "youtube" }
        assertEquals(
            AvailabilityState.AVAILABLE,
            UsernameAvailabilityChecker.classifyPlatformResponse(youtube, "unused99", 404, "").state
        )
        assertEquals(
            AvailabilityState.TAKEN,
            UsernameAvailabilityChecker.classifyPlatformResponse(
                youtube,
                "google",
                200,
                "<meta property=\"og:title\" content=\"Google\"><script>\"@google\"</script>"
            ).state
        )
    }

    @Test
    fun formatValidationRejectsInvalidGithubAndShortTelegramHandles() {
        assertNotNull(UsernameAvailabilityChecker.validateFormat("github", "user_name"))
        assertNotNull(UsernameAvailabilityChecker.validateFormat("github", "user--name"))
        assertNull(UsernameAvailabilityChecker.validateFormat("github", "arwora-dev"))

        assertNotNull(UsernameAvailabilityChecker.validateFormat("telegram", "abc"))
        assertNull(UsernameAvailabilityChecker.validateFormat("telegram", "arwora_bot"))
    }

    @Test
    fun platformActionUrlsAndAdditionalClassificationsWorkForOverlayChecks() {
        val xPlatform = UsernameAvailabilityChecker.supportedPlatforms.first { it.id == "twitter_x" }
        assertEquals("https://x.com/i/flow/signup", xPlatform.targetActionUrl(AvailabilityState.AVAILABLE, "nova99"))
        assertEquals("https://x.com/nova99", xPlatform.targetActionUrl(AvailabilityState.TAKEN, "nova99"))

        val reddit = UsernameAvailabilityChecker.supportedPlatforms.first { it.id == "reddit" }
        assertEquals(
            AvailabilityState.AVAILABLE,
            UsernameAvailabilityChecker.classifyPlatformResponse(reddit, "arwora_unique_9", 404, "{\"error\": 404}").state
        )
        assertEquals(
            AvailabilityState.TAKEN,
            UsernameAvailabilityChecker.classifyPlatformResponse(reddit, "spez", 200, "{\"kind\": \"t2\", \"data\": {\"name\": \"spez\"}}").state
        )

        val tiktok = UsernameAvailabilityChecker.supportedPlatforms.first { it.id == "tiktok" }
        assertEquals(
            AvailabilityState.AVAILABLE,
            UsernameAvailabilityChecker.classifyPlatformResponse(tiktok, "unused_99", 404, "").state
        )
        assertEquals(
            AvailabilityState.UNVERIFIED,
            UsernameAvailabilityChecker.classifyPlatformResponse(tiktok, "unused_99", 403, "captcha").state
        )
    }
}
