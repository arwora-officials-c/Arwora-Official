package com.arwora.feature.system.shell

import java.util.UUID

/**
 * Represents the current built-in Wireless ADB status for Shell execution.
 */
enum class ShellStatus(val label: String) {
    READY("Ready"),
    ADB_REQUIRED("ADB Required"),
    WIFI_REQUIRED("Wi-Fi Required"),
    DISCONNECTED("Disconnected")
}

/**
 * Result of a shell command execution via built-in Wireless ADB.
 */
data class ShellCommandResult(
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val executionTimeMs: Long,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * Saved command history item.
 */
data class ShellHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val command: String,
    val timestamp: Long,
    val exitCode: Int,
    val executionTimeMs: Long
)

/**
 * Preset diagnostic shell commands for rapid execution.
 */
data class ShellPreset(
    val name: String,
    val command: String,
    val description: String,
    val category: String = "Diagnostics"
)

object ShellPresets {
    val list = listOf(
        ShellPreset(
            name = "id",
            command = "id",
            description = "Current user and group IDs"
        ),
        ShellPreset(
            name = "whoami",
            command = "whoami",
            description = "Current user name"
        ),
        ShellPreset(
            name = "uname",
            command = "uname -a",
            description = "Kernel and architecture info"
        ),
        ShellPreset(
            name = "Android Version",
            command = "getprop ro.build.version.release",
            description = "Android OS release version"
        ),
        ShellPreset(
            name = "Device Model",
            command = "getprop ro.product.model",
            description = "Device hardware model"
        ),
        ShellPreset(
            name = "List Packages",
            command = "pm list packages",
            description = "List all installed apps"
        ),
        ShellPreset(
            name = "Anim Scale",
            command = "settings get global window_animation_scale",
            description = "Window animation scale"
        ),
        ShellPreset(
            name = "Battery head",
            command = "dumpsys battery | head -n 10",
            description = "Battery status summary"
        )
    )
}
