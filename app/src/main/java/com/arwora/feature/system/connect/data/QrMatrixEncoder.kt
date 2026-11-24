package com.arwora.feature.system.connect.data

/**
 * Pure-Kotlin ISO/IEC 18004 QR Code matrix encoder (Byte mode, Error Correction Level L, Versions 1..12).
 * Produces standard QR bit matrices scannable by ML Kit / CameraX offline without external dependencies.
 */
object QrMatrixEncoder {

    data class QrMatrix(
        val size: Int,
        val modules: Array<BooleanArray>
    ) {
        fun get(x: Int, y: Int): Boolean = modules[y][x]
    }

    // Version 1..12 capacities (Total codewords, EC codewords per block, Number of blocks) for ECC Level L
    private data class VersionSpec(
        val version: Int,
        val totalCodewords: Int,
        val ecPerBlock: Int,
        val blocksGroup1: Int,
        val dataPerBlockG1: Int,
        val blocksGroup2: Int = 0,
        val dataPerBlockG2: Int = 0
    ) {
        val totalDataCodewords: Int
            get() = blocksGroup1 * dataPerBlockG1 + blocksGroup2 * dataPerBlockG2
    }

    private val VERSION_SPECS_ECC_L = listOf(
        VersionSpec(1, 26, 7, 1, 19),
        VersionSpec(2, 44, 10, 1, 34),
        VersionSpec(3, 70, 15, 1, 55),
        VersionSpec(4, 100, 20, 1, 80),
        VersionSpec(5, 134, 26, 1, 108),
        VersionSpec(6, 172, 18, 2, 68),
        VersionSpec(7, 196, 20, 2, 78),
        VersionSpec(8, 242, 24, 2, 97),
        VersionSpec(9, 292, 30, 2, 116),
        VersionSpec(10, 346, 18, 2, 68, 2, 69),
        VersionSpec(11, 404, 20, 4, 81),
        VersionSpec(12, 466, 24, 2, 92, 2, 93),
        VersionSpec(13, 532, 22, 4, 107),
        VersionSpec(14, 581, 24, 3, 115, 1, 116)
    )

    private val ALIGNMENT_POSITIONS = mapOf(
        1 to emptyList(),
        2 to listOf(6, 18),
        3 to listOf(6, 22),
        4 to listOf(6, 26),
        5 to listOf(6, 30),
        6 to listOf(6, 34),
        7 to listOf(6, 22, 38),
        8 to listOf(6, 24, 42),
        9 to listOf(6, 26, 46),
        10 to listOf(6, 28, 50),
        11 to listOf(6, 30, 54),
        12 to listOf(6, 32, 58),
        13 to listOf(6, 34, 62),
        14 to listOf(6, 26, 46, 66)
    )

    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var v = 1
        for (i in 0 until 255) {
            GF_EXP[i] = v
            GF_LOG[v] = i
            v = v shl 1
            if (v and 0x100 != 0) {
                v = v xor 0x11D
            }
        }
        for (i in 255 until 512) {
            GF_EXP[i] = GF_EXP[i - 255]
        }
    }

    fun encode(text: String): QrMatrix {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val spec = selectVersion(bytes.size)
            ?: throw IllegalArgumentException("Payload too large (${bytes.size} bytes) for QR Version 14")

        val size = spec.version * 4 + 17
        val dataCodewords = buildDataCodewords(bytes, spec)
        val finalCodewords = interleaveWithEc(dataCodewords, spec)

        // Build base function-pattern mask and data matrix
        val bestMatrix = (0..7).map { mask ->
            val modules = Array(size) { BooleanArray(size) }
            val isFunction = Array(size) { BooleanArray(size) }
            drawFunctionPatterns(spec.version, size, modules, isFunction)
            placeCodewords(finalCodewords, size, modules, isFunction)
            applyMask(mask, size, modules, isFunction)
            drawFormatBits(mask, size, modules)
            if (spec.version >= 7) {
                drawVersionBits(spec.version, size, modules)
            }
            mask to modules
        }.minByOrNull { (_, modules) -> evaluatePenalty(size, modules) }!!.second

        return QrMatrix(size = size, modules = bestMatrix)
    }

    private fun selectVersion(byteCount: Int): VersionSpec? {
        for (spec in VERSION_SPECS_ECC_L) {
            val charCountBits = if (spec.version <= 9) 8 else 16
            val neededBits = 4 + charCountBits + byteCount * 8
            if (neededBits <= spec.totalDataCodewords * 8) {
                return spec
            }
        }
        return null
    }

    private fun buildDataCodewords(data: ByteArray, spec: VersionSpec): IntArray {
        val bitBuf = ArrayList<Int>(spec.totalDataCodewords * 8)

        fun appendBits(value: Int, count: Int) {
            for (i in (count - 1) downTo 0) {
                bitBuf.add((value ushr i) and 1)
            }
        }

        // Byte mode indicator = 0100
        appendBits(0b0100, 4)
        val countBits = if (spec.version <= 9) 8 else 16
        appendBits(data.size, countBits)
        for (b in data) {
            appendBits(b.toInt() and 0xFF, 8)
        }

        val totalBits = spec.totalDataCodewords * 8
        val terminator = minOf(4, totalBits - bitBuf.size)
        repeat(terminator) { bitBuf.add(0) }
        while (bitBuf.size % 8 != 0) {
            bitBuf.add(0)
        }

        val result = IntArray(spec.totalDataCodewords)
        val byteCount = bitBuf.size / 8
        for (i in 0 until byteCount) {
            var byteVal = 0
            for (b in 0 until 8) {
                byteVal = (byteVal shl 1) or bitBuf[i * 8 + b]
            }
            result[i] = byteVal
        }

        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        for (i in byteCount until spec.totalDataCodewords) {
            result[i] = padBytes[padIdx % 2]
            padIdx++
        }
        return result
    }

    private fun interleaveWithEc(data: IntArray, spec: VersionSpec): IntArray {
        val gen = rsGeneratorPoly(spec.ecPerBlock)
        val totalBlocks = spec.blocksGroup1 + spec.blocksGroup2
        val dataBlocks = Array(totalBlocks) { IntArray(0) }
        val ecBlocks = Array(totalBlocks) { IntArray(spec.ecPerBlock) }

        var offset = 0
        for (i in 0 until totalBlocks) {
            val blockLen = if (i < spec.blocksGroup1) spec.dataPerBlockG1 else spec.dataPerBlockG2
            val slice = data.copyOfRange(offset, offset + blockLen)
            offset += blockLen
            dataBlocks[i] = slice
            ecBlocks[i] = rsComputeRemainder(slice, gen)
        }

        val out = IntArray(spec.totalCodewords)
        var outIdx = 0
        val maxDataLen = maxOf(spec.dataPerBlockG1, spec.dataPerBlockG2)
        for (col in 0 until maxDataLen) {
            for (b in 0 until totalBlocks) {
                if (col < dataBlocks[b].size) {
                    out[outIdx++] = dataBlocks[b][col]
                }
            }
        }
        for (col in 0 until spec.ecPerBlock) {
            for (b in 0 until totalBlocks) {
                out[outIdx++] = ecBlocks[b][col]
            }
        }
        return out
    }

    private fun gfMul(x: Int, y: Int): Int {
        if (x == 0 || y == 0) return 0
        return GF_EXP[GF_LOG[x] + GF_LOG[y]]
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var result = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(result.size + 1)
            val root = GF_EXP[i]
            for (j in result.indices) {
                next[j] = next[j] xor result[j]
                next[j + 1] = next[j + 1] xor gfMul(result[j], root)
            }
            result = next
        }
        return result
    }

    private fun rsComputeRemainder(data: IntArray, gen: IntArray): IntArray {
        val ecLen = gen.size - 1
        val rem = IntArray(ecLen)
        for (b in data) {
            val factor = b xor rem[0]
            System.arraycopy(rem, 1, rem, 0, ecLen - 1)
            rem[ecLen - 1] = 0
            for (j in 0 until ecLen) {
                rem[j] = rem[j] xor gfMul(gen[j + 1], factor)
            }
        }
        return rem
    }

    private fun drawFunctionPatterns(
        version: Int,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        fun setFn(x: Int, y: Int, dark: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                modules[y][x] = dark
                isFunction[y][x] = true
            }
        }

        fun drawFinder(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    val dark = dist != 2 && dist != 4
                    setFn(cx + dx, cy + dy, dark)
                }
            }
        }

        drawFinder(3, 3)
        drawFinder(size - 4, 3)
        drawFinder(3, size - 4)

        // Timing patterns
        for (i in 8 until size - 8) {
            val dark = i % 2 == 0
            setFn(i, 6, dark)
            setFn(6, i, dark)
        }

        // Alignment patterns
        val align = ALIGNMENT_POSITIONS[version].orEmpty()
        for (cy in align) {
            for (cx in align) {
                if ((cx == 6 && cy == 6) ||
                    (cx == 6 && cy == size - 7) ||
                    (cx == size - 7 && cy == 6)
                ) continue
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                        setFn(cx + dx, cy + dy, dist != 1)
                    }
                }
            }
        }

        // Format info reservation + dark module
        for (i in 0..8) {
            if (i != 6) {
                setFn(8, i, false)
                setFn(i, 8, false)
            }
        }
        for (i in 0..7) {
            setFn(size - 1 - i, 8, false)
            if (i < 7) {
                setFn(8, size - 1 - i, false)
            }
        }
        setFn(8, size - 8, true)

        // Version info reservation for versions >= 7
        if (version >= 7) {
            for (i in 0 until 6) {
                for (j in 0 until 3) {
                    setFn(size - 11 + j, i, false)
                    setFn(i, size - 11 + j, false)
                }
            }
        }
    }

    private fun placeCodewords(
        codewords: IntArray,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        var bitIdx = 0
        val totalBits = codewords.size * 8
        var right = size - 1
        var upward = true

        while (right >= 1) {
            if (right == 6) right--
            for (step in 0 until size) {
                val y = if (upward) size - 1 - step else step
                for (dx in 0..1) {
                    val x = right - dx
                    if (!isFunction[y][x]) {
                        val dark = if (bitIdx < totalBits) {
                            ((codewords[bitIdx ushr 3] ushr (7 - (bitIdx and 7))) and 1) != 0
                        } else {
                            false
                        }
                        modules[y][x] = dark
                        bitIdx++
                    }
                }
            }
            right -= 2
            upward = !upward
        }
    }

    private fun applyMask(
        mask: Int,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!isFunction[y][x]) {
                    val invert = when (mask) {
                        0 -> (y + x) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (y + x) % 3 == 0
                        4 -> (y / 2 + x / 3) % 2 == 0
                        5 -> (y * x) % 2 + (y * x) % 3 == 0
                        6 -> ((y * x) % 2 + (y * x) % 3) % 2 == 0
                        7 -> ((y + x) % 2 + (y * x) % 3) % 2 == 0
                        else -> false
                    }
                    if (invert) {
                        modules[y][x] = !modules[y][x]
                    }
                }
            }
        }
    }

    private fun drawFormatBits(mask: Int, size: Int, modules: Array<BooleanArray>) {
        // ECC Level L = 01
        val data = (0b01 shl 3) or mask
        var rem = data
        repeat(10) {
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412

        for (i in 0..5) modules[i][8] = ((bits ushr i) and 1) != 0
        modules[7][8] = ((bits ushr 6) and 1) != 0
        modules[8][8] = ((bits ushr 7) and 1) != 0
        modules[8][7] = ((bits ushr 8) and 1) != 0
        for (i in 9..14) modules[8][14 - i] = ((bits ushr i) and 1) != 0

        for (i in 0..7) modules[8][size - 1 - i] = ((bits ushr i) and 1) != 0
        for (i in 8..14) modules[size - 15 + i][8] = ((bits ushr i) and 1) != 0
        modules[size - 8][8] = true
    }

    private fun drawVersionBits(version: Int, size: Int, modules: Array<BooleanArray>) {
        var rem = version
        repeat(12) {
            rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
        }
        val bits = (version shl 12) or rem
        for (i in 0 until 18) {
            val bit = ((bits ushr i) and 1) != 0
            val a = size - 11 + (i % 3)
            val b = i / 3
            modules[b][a] = bit
            modules[a][b] = bit
        }
    }

    private fun evaluatePenalty(size: Int, modules: Array<BooleanArray>): Int {
        var penalty = 0
        // Rule 1: 5+ consecutive modules in row/col
        for (y in 0 until size) {
            var runColor = modules[y][0]
            var runLen = 1
            for (x in 1 until size) {
                if (modules[y][x] == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    runColor = modules[y][x]
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }
        for (x in 0 until size) {
            var runColor = modules[0][x]
            var runLen = 1
            for (y in 1 until size) {
                if (modules[y][x] == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    runColor = modules[y][x]
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }
        // Rule 2: 2x2 blocks
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) {
                    penalty += 3
                }
            }
        }
        return penalty
    }
}
