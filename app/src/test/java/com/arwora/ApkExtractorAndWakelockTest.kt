package com.arwora

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.arwora.utils.ApkExtractorUtils
import com.arwora.utils.ProcessWakelockManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApkExtractorAndWakelockTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testApkExtractorTargetDirectoryAndFormatting() {
        val targetDir = ApkExtractorUtils.getTargetDirectory()
        assertNotNull(targetDir)
        assertTrue(targetDir.path.contains("Arwora hyper toolkit"))

        val formattedZero = ApkExtractorUtils.formatFileSize(0)
        assertEquals("0 B", formattedZero)

        val formattedMb = ApkExtractorUtils.formatFileSize(15 * 1024 * 1024L)
        assertTrue(formattedMb.contains("MB"))
    }

    @Test
    fun testProcessWakelockManagerRoutines() {
        val processes = kotlinx.coroutines.runBlocking {
            ProcessWakelockManager.getRunningProcesses(context)
        }
        assertNotNull(processes)
    }
}
