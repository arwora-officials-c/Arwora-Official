package com.arwora.utils

import androidx.compose.runtime.Immutable

enum class SymbolPosition(val label: String) {
    FIRST("First"),
    MIDDLE("Middle"),
    LAST("Last")
}

enum class SymbolCategory(val label: String) {
    ALL("All"),
    AESTHETIC("Aesthetic"),
    CROWN_VIP("Crown & VIP"),
    GAMING_PRO("Gaming & Pro"),
    STARS_SPARKLES("Stars & Sparkles"),
    BRACKETS_DIVIDERS("Brackets & Dividers"),
    FACES_KANJI("Faces & Kanji")
}

@Immutable
data class SymbolItem(
    val symbol: String,
    val category: SymbolCategory,
    val label: String
)

@Immutable
data class StylishTextVariant(
    val styleName: String,
    val rawStyledText: String,
    val decoratedText: String
)

object StylishTextEngine {

    val symbols: List<SymbolItem> = listOf(
        // Crown & VIP (25)
        SymbolItem("♛", SymbolCategory.CROWN_VIP, "Black Queen Crown"),
        SymbolItem("♔", SymbolCategory.CROWN_VIP, "White King Crown"),
        SymbolItem("♕", SymbolCategory.CROWN_VIP, "White Queen Crown"),
        SymbolItem("♚", SymbolCategory.CROWN_VIP, "Black King Crown"),
        SymbolItem("👑", SymbolCategory.CROWN_VIP, "Royal Crown"),
        SymbolItem("⚜", SymbolCategory.CROWN_VIP, "Fleur-de-lis"),
        SymbolItem("🏆", SymbolCategory.CROWN_VIP, "Champion Trophy"),
        SymbolItem("💎", SymbolCategory.CROWN_VIP, "Diamond Gem"),
        SymbolItem("🎖", SymbolCategory.CROWN_VIP, "Honor Medal"),
        SymbolItem("🥇", SymbolCategory.CROWN_VIP, "First Place"),
        SymbolItem("💠", SymbolCategory.CROWN_VIP, "Diamond Emblem"),
        SymbolItem("🔱", SymbolCategory.CROWN_VIP, "Trident"),
        SymbolItem("♾", SymbolCategory.CROWN_VIP, "Infinity Crest"),
        SymbolItem("⛨", SymbolCategory.CROWN_VIP, "Shield Crest"),
        SymbolItem("⍟", SymbolCategory.CROWN_VIP, "Circle Star"),
        SymbolItem("❖", SymbolCategory.CROWN_VIP, "Four Diamond Emblem"),
        SymbolItem("◈", SymbolCategory.CROWN_VIP, "Core Diamond"),
        SymbolItem("◆", SymbolCategory.CROWN_VIP, "Solid Diamond"),
        SymbolItem("◇", SymbolCategory.CROWN_VIP, "Hollow Diamond"),
        SymbolItem("♜", SymbolCategory.CROWN_VIP, "Black Rook"),
        SymbolItem("♝", SymbolCategory.CROWN_VIP, "Black Bishop"),
        SymbolItem("♞", SymbolCategory.CROWN_VIP, "Black Knight"),
        SymbolItem("♠", SymbolCategory.CROWN_VIP, "Spade"),
        SymbolItem("♣", SymbolCategory.CROWN_VIP, "Club"),
        SymbolItem("♟", SymbolCategory.CROWN_VIP, "Pawn"),

        // Aesthetic (28)
        SymbolItem("✿", SymbolCategory.AESTHETIC, "Flower Outline"),
        SymbolItem("❀", SymbolCategory.AESTHETIC, "Petal Flower"),
        SymbolItem("♡", SymbolCategory.AESTHETIC, "Hollow Heart"),
        SymbolItem("♥", SymbolCategory.AESTHETIC, "Solid Heart"),
        SymbolItem("❥", SymbolCategory.AESTHETIC, "Rotated Heart"),
        SymbolItem("❣", SymbolCategory.AESTHETIC, "Heart Exclamation"),
        SymbolItem("ღ", SymbolCategory.AESTHETIC, "Georgian Swirl"),
        SymbolItem("❁", SymbolCategory.AESTHETIC, "Eight Petal Florette"),
        SymbolItem("✾", SymbolCategory.AESTHETIC, "Six Petal Florette"),
        SymbolItem("❃", SymbolCategory.AESTHETIC, "Heavy Florette"),
        SymbolItem("❋", SymbolCategory.AESTHETIC, "Sparkle Blossom"),
        SymbolItem("˚", SymbolCategory.AESTHETIC, "Floating Ring"),
        SymbolItem("₊", SymbolCategory.AESTHETIC, "Tiny Plus"),
        SymbolItem("‧", SymbolCategory.AESTHETIC, "Soft Dot"),
        SymbolItem("꒰", SymbolCategory.AESTHETIC, "Cute Left Wing"),
        SymbolItem("꒱", SymbolCategory.AESTHETIC, "Cute Right Wing"),
        SymbolItem("༊", SymbolCategory.AESTHETIC, "Cloud Swirl"),
        SymbolItem("࿔", SymbolCategory.AESTHETIC, "Aesthetic Scroll"),
        SymbolItem("୨", SymbolCategory.AESTHETIC, "Ribbon Left"),
        SymbolItem("୧", SymbolCategory.AESTHETIC, "Ribbon Right"),
        SymbolItem("⋆", SymbolCategory.AESTHETIC, "Star Operator"),
        SymbolItem("⊹", SymbolCategory.AESTHETIC, "Hermitian Cross"),
        SymbolItem("˖", SymbolCategory.AESTHETIC, "Modifier Plus"),
        SymbolItem("🌷", SymbolCategory.AESTHETIC, "Tulip"),
        SymbolItem("🌸", SymbolCategory.AESTHETIC, "Cherry Blossom"),
        SymbolItem("🦋", SymbolCategory.AESTHETIC, "Butterfly"),
        SymbolItem("🕊", SymbolCategory.AESTHETIC, "Dove"),
        SymbolItem("🌙", SymbolCategory.AESTHETIC, "Crescent Moon"),

        // Gaming & Pro (28)
        SymbolItem("⚡", SymbolCategory.GAMING_PRO, "Lightning Bolt"),
        SymbolItem("⚔️", SymbolCategory.GAMING_PRO, "Crossed Swords"),
        SymbolItem("☠️", SymbolCategory.GAMING_PRO, "Skull and Crossbones"),
        SymbolItem("༒", SymbolCategory.GAMING_PRO, "Warrior Mark"),
        SymbolItem("乡", SymbolCategory.GAMING_PRO, "Clan Crest"),
        SymbolItem("么", SymbolCategory.GAMING_PRO, "Pro Tag"),
        SymbolItem("➢", SymbolCategory.GAMING_PRO, "3D Right Arrowhead"),
        SymbolItem("➤", SymbolCategory.GAMING_PRO, "Black Right Arrowhead"),
        SymbolItem("➔", SymbolCategory.GAMING_PRO, "Heavy Arrow"),
        SymbolItem("➜", SymbolCategory.GAMING_PRO, "Heavy Round Arrow"),
        SymbolItem("☣", SymbolCategory.GAMING_PRO, "Biohazard"),
        SymbolItem("☢", SymbolCategory.GAMING_PRO, "Radioactive"),
        SymbolItem("乂", SymbolCategory.GAMING_PRO, "Cross Blade"),
        SymbolItem("々", SymbolCategory.GAMING_PRO, "Echo Mark"),
        SymbolItem("乄", SymbolCategory.GAMING_PRO, "Slash Sigil"),
        SymbolItem("⌘", SymbolCategory.GAMING_PRO, "Command Crest"),
        SymbolItem("⎊", SymbolCategory.GAMING_PRO, "Target Triangle"),
        SymbolItem("⟁", SymbolCategory.GAMING_PRO, "Apex Triangle"),
        SymbolItem("⌖", SymbolCategory.GAMING_PRO, "Crosshair"),
        SymbolItem("⫷", SymbolCategory.GAMING_PRO, "Triple Left Chevron"),
        SymbolItem("⫸", SymbolCategory.GAMING_PRO, "Triple Right Chevron"),
        SymbolItem("⨠", SymbolCategory.GAMING_PRO, "Double Arrow"),
        SymbolItem("⇶", SymbolCategory.GAMING_PRO, "Triple Surge"),
        SymbolItem("🔥", SymbolCategory.GAMING_PRO, "Flame"),
        SymbolItem("🎯", SymbolCategory.GAMING_PRO, "Bullseye"),
        SymbolItem("🎮", SymbolCategory.GAMING_PRO, "Controller"),
        SymbolItem("🛡", SymbolCategory.GAMING_PRO, "Shield"),
        SymbolItem("💣", SymbolCategory.GAMING_PRO, "Bomb"),

        // Stars & Sparkles (28)
        SymbolItem("★", SymbolCategory.STARS_SPARKLES, "Solid Star"),
        SymbolItem("☆", SymbolCategory.STARS_SPARKLES, "Hollow Star"),
        SymbolItem("✦", SymbolCategory.STARS_SPARKLES, "Four Point Star"),
        SymbolItem("✧", SymbolCategory.STARS_SPARKLES, "White Four Point Star"),
        SymbolItem("✩", SymbolCategory.STARS_SPARKLES, "Stress Star"),
        SymbolItem("✪", SymbolCategory.STARS_SPARKLES, "Circled Star"),
        SymbolItem("✫", SymbolCategory.STARS_SPARKLES, "Open Center Star"),
        SymbolItem("✬", SymbolCategory.STARS_SPARKLES, "Shadow Star"),
        SymbolItem("✭", SymbolCategory.STARS_SPARKLES, "Outlined Star"),
        SymbolItem("✮", SymbolCategory.STARS_SPARKLES, "Heavy Outlined Star"),
        SymbolItem("✯", SymbolCategory.STARS_SPARKLES, "Pinwheel Star"),
        SymbolItem("✰", SymbolCategory.STARS_SPARKLES, "Shadow White Star"),
        SymbolItem("✴", SymbolCategory.STARS_SPARKLES, "Eight Point Star"),
        SymbolItem("✵", SymbolCategory.STARS_SPARKLES, "Pinwheel Eight Star"),
        SymbolItem("✶", SymbolCategory.STARS_SPARKLES, "Six Point Star"),
        SymbolItem("✷", SymbolCategory.STARS_SPARKLES, "Eight Point Heavy Star"),
        SymbolItem("✸", SymbolCategory.STARS_SPARKLES, "Heavy Spoked Star"),
        SymbolItem("✹", SymbolCategory.STARS_SPARKLES, "Twelve Point Star"),
        SymbolItem("✺", SymbolCategory.STARS_SPARKLES, "Sixteen Point Star"),
        SymbolItem("✻", SymbolCategory.STARS_SPARKLES, "Teardrop Asterisk"),
        SymbolItem("✼", SymbolCategory.STARS_SPARKLES, "Open Asterisk"),
        SymbolItem("✽", SymbolCategory.STARS_SPARKLES, "Heavy Teardrop Star"),
        SymbolItem("❄", SymbolCategory.STARS_SPARKLES, "Snowflake"),
        SymbolItem("❅", SymbolCategory.STARS_SPARKLES, "Tight Trifoliate Star"),
        SymbolItem("❆", SymbolCategory.STARS_SPARKLES, "Heavy Chevron Snowflake"),
        SymbolItem("❇", SymbolCategory.STARS_SPARKLES, "Sparkle"),
        SymbolItem("❈", SymbolCategory.STARS_SPARKLES, "Heavy Sparkle"),
        SymbolItem("✨", SymbolCategory.STARS_SPARKLES, "Sparkles"),

        // Brackets & Dividers (28)
        SymbolItem("『", SymbolCategory.BRACKETS_DIVIDERS, "Corner Bracket Left"),
        SymbolItem("』", SymbolCategory.BRACKETS_DIVIDERS, "Corner Bracket Right"),
        SymbolItem("【", SymbolCategory.BRACKETS_DIVIDERS, "Lenticular Left"),
        SymbolItem("】", SymbolCategory.BRACKETS_DIVIDERS, "Lenticular Right"),
        SymbolItem("「", SymbolCategory.BRACKETS_DIVIDERS, "Single Corner Left"),
        SymbolItem("」", SymbolCategory.BRACKETS_DIVIDERS, "Single Corner Right"),
        SymbolItem("《", SymbolCategory.BRACKETS_DIVIDERS, "Double Angle Left"),
        SymbolItem("》", SymbolCategory.BRACKETS_DIVIDERS, "Double Angle Right"),
        SymbolItem("〈", SymbolCategory.BRACKETS_DIVIDERS, "Angle Left"),
        SymbolItem("〉", SymbolCategory.BRACKETS_DIVIDERS, "Angle Right"),
        SymbolItem("〔", SymbolCategory.BRACKETS_DIVIDERS, "Tortoise Left"),
        SymbolItem("〕", SymbolCategory.BRACKETS_DIVIDERS, "Tortoise Right"),
        SymbolItem("〖", SymbolCategory.BRACKETS_DIVIDERS, "Hollow Lenticular Left"),
        SymbolItem("〗", SymbolCategory.BRACKETS_DIVIDERS, "Hollow Lenticular Right"),
        SymbolItem("〘", SymbolCategory.BRACKETS_DIVIDERS, "White Tortoise Left"),
        SymbolItem("〙", SymbolCategory.BRACKETS_DIVIDERS, "White Tortoise Right"),
        SymbolItem("〚", SymbolCategory.BRACKETS_DIVIDERS, "White Square Left"),
        SymbolItem("〛", SymbolCategory.BRACKETS_DIVIDERS, "White Square Right"),
        SymbolItem("꧁", SymbolCategory.BRACKETS_DIVIDERS, "Ornate Wing Left"),
        SymbolItem("꧂", SymbolCategory.BRACKETS_DIVIDERS, "Ornate Wing Right"),
        SymbolItem("༺", SymbolCategory.BRACKETS_DIVIDERS, "Tibetan Wing Left"),
        SymbolItem("༻", SymbolCategory.BRACKETS_DIVIDERS, "Tibetan Wing Right"),
        SymbolItem("╰", SymbolCategory.BRACKETS_DIVIDERS, "Rounded Curve Left"),
        SymbolItem("╯", SymbolCategory.BRACKETS_DIVIDERS, "Rounded Curve Right"),
        SymbolItem("•", SymbolCategory.BRACKETS_DIVIDERS, "Bullet Divider"),
        SymbolItem("·", SymbolCategory.BRACKETS_DIVIDERS, "Middle Dot"),
        SymbolItem("━", SymbolCategory.BRACKETS_DIVIDERS, "Heavy Line"),
        SymbolItem("═", SymbolCategory.BRACKETS_DIVIDERS, "Double Line"),

        // Faces & Kanji (30)
        SymbolItem("ツ", SymbolCategory.FACES_KANJI, "Tsu Smile"),
        SymbolItem("×͜×", SymbolCategory.FACES_KANJI, "Cross Smile"),
        SymbolItem("⸙", SymbolCategory.FACES_KANJI, "Palm Ornament"),
        SymbolItem("亗", SymbolCategory.FACES_KANJI, "Royal Badge"),
        SymbolItem("〆", SymbolCategory.FACES_KANJI, "Shime Emblem"),
        SymbolItem("ッ", SymbolCategory.FACES_KANJI, "Small Tsu Smile"),
        SymbolItem("シ", SymbolCategory.FACES_KANJI, "Shi Smile"),
        SymbolItem("ジ", SymbolCategory.FACES_KANJI, "Ji Smile"),
        SymbolItem("ヅ", SymbolCategory.FACES_KANJI, "Dzu Smile"),
        SymbolItem("ゾ", SymbolCategory.FACES_KANJI, "Zo Mark"),
        SymbolItem("ミ", SymbolCategory.FACES_KANJI, "Mi Waves"),
        SymbolItem("愛", SymbolCategory.FACES_KANJI, "Love Kanji"),
        SymbolItem("神", SymbolCategory.FACES_KANJI, "God Kanji"),
        SymbolItem("鬼", SymbolCategory.FACES_KANJI, "Oni Spirit"),
        SymbolItem("龍", SymbolCategory.FACES_KANJI, "Dragon Kanji"),
        SymbolItem("影", SymbolCategory.FACES_KANJI, "Shadow Kanji"),
        SymbolItem("王", SymbolCategory.FACES_KANJI, "King Kanji"),
        SymbolItem("星", SymbolCategory.FACES_KANJI, "Star Kanji"),
        SymbolItem("月", SymbolCategory.FACES_KANJI, "Moon Kanji"),
        SymbolItem("雪", SymbolCategory.FACES_KANJI, "Snow Kanji"),
        SymbolItem("夢", SymbolCategory.FACES_KANJI, "Dream Kanji"),
        SymbolItem("風", SymbolCategory.FACES_KANJI, "Wind Kanji"),
        SymbolItem("火", SymbolCategory.FACES_KANJI, "Fire Kanji"),
        SymbolItem("雷", SymbolCategory.FACES_KANJI, "Thunder Kanji"),
        SymbolItem("魂", SymbolCategory.FACES_KANJI, "Soul Kanji"),
        SymbolItem("無", SymbolCategory.FACES_KANJI, "Void Kanji"),
        SymbolItem("空", SymbolCategory.FACES_KANJI, "Sky Kanji"),
        SymbolItem("零", SymbolCategory.FACES_KANJI, "Zero Kanji"),
        SymbolItem("夜", SymbolCategory.FACES_KANJI, "Night Kanji"),
        SymbolItem("炎", SymbolCategory.FACES_KANJI, "Blaze Kanji")
    )

    private val symbolsByCategory: Map<SymbolCategory, List<SymbolItem>> =
        SymbolCategory.entries.associateWith { category ->
            if (category == SymbolCategory.ALL) symbols
            else symbols.filter { it.category == category }
        }

    fun symbolsFor(category: SymbolCategory): List<SymbolItem> =
        symbolsByCategory[category].orEmpty()

    /**
     * Inserts [symbol] into [styledText] at [position] without breaking multi-byte
     * Unicode surrogate pairs (Mathematical Bold/Fraktur/Script characters).
     */
    fun applySymbolToText(
        styledText: String,
        symbol: String,
        position: SymbolPosition
    ): String {
        if (symbol.isEmpty() || styledText.isEmpty()) return styledText

        return when (position) {
            SymbolPosition.FIRST -> "$symbol $styledText"
            SymbolPosition.LAST -> "$styledText $symbol"
            SymbolPosition.MIDDLE -> insertAtMiddle(styledText, symbol)
        }
    }

    private fun insertAtMiddle(styledText: String, symbol: String): String {
        val internalSpaceIndices = ArrayList<Int>()
        for (i in 1 until styledText.lastIndex) {
            if (styledText[i] == ' ' && !styledText[i - 1].isWhitespace() && !styledText[i + 1].isWhitespace()) {
                internalSpaceIndices.add(i)
            }
        }

        if (internalSpaceIndices.isNotEmpty()) {
            val midIndex = styledText.length / 2
            val bestSpaceIdx = internalSpaceIndices.minByOrNull { kotlin.math.abs(it - midIndex) }
                ?: internalSpaceIndices[internalSpaceIndices.size / 2]
            return buildString(styledText.length + symbol.length + 2) {
                append(styledText, 0, bestSpaceIdx)
                append(' ')
                append(symbol)
                append(' ')
                append(styledText, bestSpaceIdx + 1, styledText.length)
            }
        }

        val codePointCount = styledText.codePointCount(0, styledText.length)
        if (codePointCount <= 1) {
            return "$styledText $symbol"
        }

        val midCodePoint = codePointCount / 2
        val midCharOffset = styledText.offsetByCodePoints(0, midCodePoint)
        return buildString(styledText.length + symbol.length + 2) {
            append(styledText, 0, midCharOffset)
            append(' ')
            append(symbol)
            append(' ')
            append(styledText, midCharOffset, styledText.length)
        }
    }

    /**
     * Computes decorated stylish variants for every font style in [FontStyles.styles].
     * Pure CPU work — call from Dispatchers.Default for smooth 60/120 FPS typing.
     */
    fun generateAllVariants(
        inputText: String,
        selectedSymbol: String,
        position: SymbolPosition,
        prioritizedStyle: String? = null
    ): List<StylishTextVariant> {
        if (inputText.isBlank()) return emptyList()

        val orderedStyles = if (prioritizedStyle != null && prioritizedStyle in FontStyles.styles) {
            listOf(prioritizedStyle) + FontStyles.styles.filter { it != prioritizedStyle }
        } else {
            FontStyles.styles
        }

        return orderedStyles.map { styleName ->
            val rawStyled = FontStyles.apply(inputText, styleName)
            val decorated = applySymbolToText(rawStyled, selectedSymbol, position)
            StylishTextVariant(
                styleName = styleName,
                rawStyledText = rawStyled,
                decoratedText = decorated
            )
        }
    }
}
