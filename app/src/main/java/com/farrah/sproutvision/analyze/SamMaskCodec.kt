package com.farrah.sproutvision.analyze

/**
 * Pure SAM 3.1 wire-format parsing and mask decoding — no network, no Android.
 *
 * Verified 2026-10-22 against two primary sources (nothing here is guessed):
 * - Meta Model API docs: dev.meta.ai/docs/sam/segmenting (request shape),
 *   dev.meta.ai/docs/sam/reading-segmentation (record grammar: `<Nf>` frame
 *   lines, `<|box;...|>`, `<|mask;...;data=H,W,ENCpayload|>` with H rows
 *   first, base85 payload, `~` = lossless / `!` = one_bit).
 * - Meta's own meta-sam-parser 0.1.0 (PyPI) _mask_codec.py, which this
 *   file ports line by line: the custom base85 unpack (printable 0x21-0x7E
 *   minus `" \,;< >|`, 5-char radix-85 length prefix), the range (arithmetic)
 *   decoder, and the 12-bit spatial-context bit model for one_bit.
 *
 * Rules this port follows:
 * - Both encodings: one_bit (`!`) and lossless (`~`). The request
 *   asks for lossless (the playground's mask_encoding); lossless
 *   decodes to grayscale coverage bytes that threshold to 0/1 at
 *   >= 129, exactly as Meta's decode_mask_to_raster. one_bit keeps
 *   its canonical re-encode check; lossless has none (mirroring
 *   Meta's parser, which ships no lossless encoder).
 * - Strict all-or-nothing records: any malformed record fails the whole
 *   call (the caller falls back). A partial union would understate
 *   coverage — worse than no SAM number at all.
 * - Raster placement: each instance raster is nearest-neighbor-scaled
 *   onto its box in source pixels. The docs' examples show mask H×W
 *   equal to box dims, but live 3000×4000 responses (2026-10-27) return
 *   masks downsampled to 1024 on the long edge — the mask:box aspect
 *   match on every record proves the scale-up. Pastes clip to the frame
 *   defensively.
 * - Mask dims must fit their frame, and frames are sanity-capped, so a
 *   corrupt stream can never OOM the app on allocation.
 */
class SamMaskException(message: String, cause: Throwable? = null) : Exception(message, cause)

// Custom base85 alphabet: printable 0x21..0x7E minus " \ , ; < > |.
// NB: 94 - 7 = 87 chars but the radix is 85 — values 85+ are invalid,
// exactly as in Meta's _digit (the top two chars can never appear).
private val MASK_ALPHABET: String = buildString {
    for (code in 0x21..0x7E) {
        val c = code.toChar()
        if (c != '"' && c != '\\' && c != ',' && c != ';' && c != '<' && c != '>' && c != '|') {
            append(c)
        }
    }
}
private const val MASK_RADIX = 85
private const val LENGTH_PREFIX = 5
private const val MAX_SAFE_INTEGER = (1L shl 53) - 1
private const val MASK32 = 0xFFFFFFFFL
private const val TOP = 1L shl 24
private const val BOTTOM = 1L shl 16
private const val COUNT_INCREMENT = 14
private const val COUNT_LIMIT = 4096
private const val CONTEXT_COUNT = 1 shl 12
private const val PADDING = 2
// Sanity bounds: real traffic is a ≤640×480 input, so frames are small.
// Anything beyond this is a corrupt stream, not a tray photo.
private const val MAX_FRAME_DIM = 8192
private const val MAX_FRAME_AREA = 16_777_216L

private fun digit(c: Char): Int {
    val value = MASK_ALPHABET.indexOf(c)
    if (value < 0 || value >= MASK_RADIX) {
        throw SamMaskException("Mask payload contains an invalid character.")
    }
    return value
}

/** Inverse of unpack85 — used only for the canonical-form check. */
internal fun pack85(input: ByteArray): String {
    val length = input.size
    var remaining = length
    val prefix = CharArray(LENGTH_PREFIX)
    for (index in LENGTH_PREFIX - 1 downTo 0) {
        prefix[index] = MASK_ALPHABET[remaining % MASK_RADIX]
        remaining /= MASK_RADIX
    }
    if (remaining != 0) throw SamMaskException("Mask payload is too large.")
    val output = StringBuilder().append(prefix)
    val full = length - (length % 4)
    var offset = 0
    while (offset < full) {
        var value = (
            ((input[offset].toInt() and 0xFF) shl 24) or
                ((input[offset + 1].toInt() and 0xFF) shl 16) or
                ((input[offset + 2].toInt() and 0xFF) shl 8) or
                (input[offset + 3].toInt() and 0xFF)
            ).toLong() and MASK32
        val digits = IntArray(5)
        for (index in 4 downTo 0) {
            digits[index] = (value % MASK_RADIX).toInt()
            value = (value - digits[index]) / MASK_RADIX
        }
        digits.forEach { output.append(MASK_ALPHABET[it]) }
        offset += 4
    }
    val remainder = length - offset
    if (remainder > 0) {
        var value = 0L
        for (index in 0 until 4) {
            value = ((value shl 8) or
                (if (index < remainder) input[offset + index].toInt() and 0xFF else 0).toLong()) and MASK32
        }
        val digits = IntArray(5)
        for (index in 4 downTo 0) {
            digits[index] = (value % MASK_RADIX).toInt()
            value = (value - digits[index]) / MASK_RADIX
        }
        for (index in 0 until remainder + 1) output.append(MASK_ALPHABET[digits[index]])
    }
    return output.toString()
}

internal fun unpack85(payload: String): ByteArray {
    if (payload.length < LENGTH_PREFIX) {
        throw SamMaskException("Mask payload is missing its length prefix.")
    }
    var length = 0L
    for (c in payload.take(LENGTH_PREFIX)) length = length * MASK_RADIX + digit(c)
    if (length > MAX_SAFE_INTEGER) {
        throw SamMaskException("Mask payload length is unsupported.")
    }
    val remainder = (length % 4).toInt()
    val expected = LENGTH_PREFIX + (length / 4) * 5 + (if (remainder == 0) 0 else remainder + 1)
    if (payload.length.toLong() != expected) {
        throw SamMaskException("Mask payload length does not match its prefix.")
    }
    // Safe to narrow: expected == payload.length (an Int) constrains length.
    val lengthInt = length.toInt()
    val output = ByteArray(lengthInt)
    var source = LENGTH_PREFIX
    var destination = 0
    val full = lengthInt - remainder
    while (destination < full) {
        var value = 0L
        for (index in 0 until 5) value = value * MASK_RADIX + digit(payload[source + index])
        if (value > MASK32) {
            throw SamMaskException("Mask payload contains an out-of-range group.")
        }
        source += 5
        output[destination] = ((value shr 24) and 0xFF).toByte()
        output[destination + 1] = ((value shr 16) and 0xFF).toByte()
        output[destination + 2] = ((value shr 8) and 0xFF).toByte()
        output[destination + 3] = (value and 0xFF).toByte()
        destination += 4
    }
    if (remainder > 0) {
        var value = 0L
        for (index in 0 until 5) {
            value = value * MASK_RADIX + (
                if (index < remainder + 1) digit(payload[source + index]) else MASK_RADIX - 1
                )
        }
        if (value > MASK32) {
            throw SamMaskException("Mask payload contains an out-of-range tail.")
        }
        for (index in 0 until remainder) {
            output[destination + index] = ((value shr (24 - index * 8)) and 0xFF).toByte()
        }
    }
    if (pack85(output) != payload) {
        throw SamMaskException("Mask payload is not in canonical form.")
    }
    return output
}

/** Range (arithmetic) decoder. All state kept as unsigned 32-bit in Long. */
private class RangeDecoder(private val input: ByteArray) {
    private var position = 0
    private var low = 0L
    private var range = MASK32
    private var code = 0L
    private var scaled = 0L

    init {
        if (input.size < 4) throw SamMaskException("Mask payload is missing its finalization.")
        repeat(4) { code = ((code shl 8) or read().toLong()) and MASK32 }
    }

    fun frequency(total: Int): Int {
        scaled = range / total
        if (scaled == 0L) throw SamMaskException("Mask payload has invalid coding state.")
        val value = ((code - low) and MASK32) / scaled
        return if (value >= total) total - 1 else value.toInt()
    }

    fun decode(cumulative: Int, frequency: Int) {
        low = (low + scaled * cumulative) and MASK32
        range = scaled * frequency
        while (true) {
            val high = (low + range) and MASK32
            if (((low xor high) and MASK32) < TOP) {
                // Top byte matches: shift it out below.
            } else if (range < BOTTOM) {
                range = ((-low) and MASK32) and (BOTTOM - 1)
            } else {
                break
            }
            code = ((code shl 8) or read().toLong()) and MASK32
            low = (low shl 8) and MASK32
            range = (range shl 8) and MASK32
        }
    }

    private fun read(): Int {
        if (position >= input.size) {
            throw SamMaskException("Mask payload ended before decoding completed.")
        }
        return input[position++].toInt() and 0xFF
    }
}

/** Range encoder — used only for the canonical re-encode check. */
private class RangeEncoder {
    private var low = 0L
    private var range = MASK32
    private val output = mutableListOf<Byte>()

    fun encode(cumulative: Int, frequency: Int, total: Int) {
        val scaled = range / total
        low = (low + scaled * cumulative) and MASK32
        range = scaled * frequency
        while (true) {
            val high = (low + range) and MASK32
            if (((low xor high) and MASK32) < TOP) {
                // Top byte matches: shift it out below.
            } else if (range < BOTTOM) {
                range = ((-low) and MASK32) and (BOTTOM - 1)
            } else {
                break
            }
            output.add(((low shr 24) and 0xFF).toByte())
            low = (low shl 8) and MASK32
            range = (range shl 8) and MASK32
        }
    }

    fun finish(): ByteArray {
        repeat(4) {
            output.add(((low shr 24) and 0xFF).toByte())
            low = (low shl 8) and MASK32
        }
        return output.toByteArray()
    }
}

private fun contextAt(raster: ByteArray, position: Int, prior: Int, earlier: Int): Int {
    fun bitAt(index: Int): Int = raster[index].toInt() and 0xFF
    return bitAt(position - 1) or
        (bitAt(position - 2) shl 1) or
        (bitAt(prior - 2) shl 2) or
        (bitAt(prior - 1) shl 3) or
        (bitAt(prior) shl 4) or
        (bitAt(prior + 1) shl 5) or
        (bitAt(prior + 2) shl 6) or
        (bitAt(earlier - 2) shl 7) or
        (bitAt(earlier - 1) shl 8) or
        (bitAt(earlier) shl 9) or
        (bitAt(earlier + 1) shl 10) or
        (bitAt(earlier + 2) shl 11)
}

private fun updateCounts(counts: IntArray, offset: Int, bit: Int) {
    counts[offset + bit] = counts[offset + bit] + COUNT_INCREMENT
    if (counts[offset] + counts[offset + 1] >= COUNT_LIMIT) {
        // Meta's `(x >> 1) or 1` is Python `or`: a floor at 1, NOT a
        // bitwise set-bit. Port as coerceAtLeast, never `or 1`.
        counts[offset] = (counts[offset] shr 1).coerceAtLeast(1)
        counts[offset + 1] = (counts[offset + 1] shr 1).coerceAtLeast(1)
    }
}

/** Re-encode a raster — canonical check only, mirrors Meta's encoder. */
internal fun encodeOneBitRaster(raster: ByteArray, width: Int, height: Int): String {
    val encoder = RangeEncoder()
    val counts = IntArray(CONTEXT_COUNT * 2) { 1 }
    val paddedWidth = width + 2 * PADDING
    val padded = ByteArray(paddedWidth * (height + PADDING))
    for (y in 0 until height) {
        val row = (y + PADDING) * paddedWidth + PADDING
        for (x in 0 until width) {
            val position = row + x
            val context = contextAt(
                padded, position, position - paddedWidth, position - 2 * paddedWidth
            )
            val offset = context shl 1
            val zero = counts[offset]
            val one = counts[offset + 1]
            val bit = raster[y * width + x].toInt()
            if (bit != 0 && bit != 1) throw SamMaskException("Decoded mask is not binary.")
            if (bit == 0) encoder.encode(0, zero, zero + one)
            else encoder.encode(zero, one, zero + one)
            padded[position] = bit.toByte()
            updateCounts(counts, offset, bit)
        }
    }
    return "!" + pack85(encoder.finish())
}

private fun decodeOneBitRaster(payload: String, width: Int, height: Int): ByteArray {
    val decoder = RangeDecoder(unpack85(payload.substring(1)))
    val counts = IntArray(CONTEXT_COUNT * 2) { 1 }
    val paddedWidth = width + 2 * PADDING
    val padded = ByteArray(paddedWidth * (height + PADDING))
    val output = ByteArray(width * height)
    for (y in 0 until height) {
        val row = (y + PADDING) * paddedWidth + PADDING
        for (x in 0 until width) {
            val position = row + x
            val context = contextAt(
                padded, position, position - paddedWidth, position - 2 * paddedWidth
            )
            val offset = context shl 1
            val zero = counts[offset]
            val one = counts[offset + 1]
            val value = decoder.frequency(zero + one)
            val bit = if (value < zero) 0 else 1
            if (bit == 0) decoder.decode(0, zero) else decoder.decode(zero, one)
            padded[position] = bit.toByte()
            output[y * width + x] = bit.toByte()
            updateCounts(counts, offset, bit)
        }
    }
    return output
}

private const val LOSSLESS_SYMBOLS = 256
private const val LOSSLESS_TOTAL_INDEX = 256
private const val LOSSLESS_SPATIAL_MODES = 5
private const val LOSSLESS_ZERO_INCREMENT = 32
private const val LOSSLESS_ZERO_LIMIT = 16384
private const val LOSSLESS_ORDER_ONE_INCREMENT = 56
private const val LOSSLESS_ORDER_ONE_LIMIT = 8192
private const val LOSSLESS_ORDER_TWO_STEP = 14 * 44
private const val LOSSLESS_ORDER_TWO_LIMIT = 14 * 3584
private const val LOSSLESS_ORDER_ZERO_STEP = 2 * 16
private const val LOSSLESS_ORDER_ZERO_LIMIT = 2 * 2048
private const val LOSSLESS_ORDER_ZERO_INITIAL = 2
private const val LOSSLESS_ORDER_ONE_INITIAL = 1
private const val LOSSLESS_THRESHOLD = 129

private fun losslessPaeth(left: Int, up: Int, upperLeft: Int): Int {
    val estimate = left + up - upperLeft
    val leftDistance = kotlin.math.abs(estimate - left)
    val upDistance = kotlin.math.abs(estimate - up)
    val upperLeftDistance = kotlin.math.abs(estimate - upperLeft)
    if (leftDistance <= upDistance && leftDistance <= upperLeftDistance) return left
    return if (upDistance <= upperLeftDistance) up else upperLeft
}

private fun losslessPredict(
    output: ByteArray, offset: Int, x: Int, y: Int, mode: Int, width: Int
): Int {
    if (mode < 0 || mode >= LOSSLESS_SPATIAL_MODES) {
        throw SamMaskException("Lossless mask payload uses an invalid predictor.")
    }
    val left = if (x > 0) output[offset - 1].toInt() and 0xFF else 0
    val up = if (y > 0) output[offset - width].toInt() and 0xFF else 0
    if (mode == 1) return up
    if (mode == 2) return left
    if (mode == 3) return (left + up) shr 1
    val upperLeft = if (x > 0 && y > 0) output[offset - width - 1].toInt() and 0xFF else 0
    if (mode == 0) return losslessPaeth(left, up, upperLeft)
    if (upperLeft >= maxOf(left, up)) return minOf(left, up)
    if (upperLeft <= minOf(left, up)) return maxOf(left, up)
    return left + up - upperLeft
}

private fun losslessUnzig(value: Int, prediction: Int): Int =
    (prediction + if (value and 1 == 1) -((value + 1) shr 1) else value shr 1) and 0xFF

private fun losslessZeroContext(
    residuals: ByteArray, offset: Int, x: Int, y: Int, width: Int
): Int {
    val left = if (x > 0 && residuals[offset - 1].toInt() == 0) 1 else if (x > 0) 0 else 1
    val up = if (y > 0 && residuals[offset - width].toInt() == 0) 1 else if (y > 0) 0 else 1
    val upperLeft =
        if (x > 0 && y > 0 && residuals[offset - width - 1].toInt() == 0) 1
        else if (x > 0 && y > 0) 0 else 1
    val upperRight =
        if (y > 0 && x < width - 1 && residuals[offset - width + 1].toInt() == 0) 1
        else if (y > 0 && x < width - 1) 0 else 1
    return left or (up shl 1) or (upperLeft shl 2) or (upperRight shl 3)
}

/**
 * Lossless (`~`) payload to grayscale coverage bytes (ports Meta's
 * _decode_lossless_raster line by line: zero/nonzero bit plane, then
 * blended order-two/one/zero residual models, zigzagged off a spatial
 * predictor). The caller thresholds to 0/1 at [LOSSLESS_THRESHOLD].
 */
private fun decodeLosslessRaster(payload: String, width: Int, height: Int): ByteArray {
    if (!payload.startsWith("~")) {
        throw SamMaskException("lossless mask payloads must start with ~.")
    }
    val packed = unpack85(payload.substring(1))
    if (packed.size < 5) throw SamMaskException("Lossless mask payload is truncated.")
    val selector = packed[0].toInt() and 0xFF
    val decoder = RangeDecoder(packed.copyOfRange(1, packed.size))
    val length = width * height
    val output = ByteArray(length)
    val residuals = ByteArray(length)
    val zeroCounts = IntArray(32) { 1 }
    val orderOne = IntArray(LOSSLESS_SYMBOLS * LOSSLESS_SYMBOLS) { LOSSLESS_ORDER_ONE_INITIAL }
    val orderOneTotals = IntArray(LOSSLESS_SYMBOLS) { LOSSLESS_SYMBOLS * LOSSLESS_ORDER_ONE_INITIAL }
    val orderZero = IntArray(LOSSLESS_SYMBOLS) { LOSSLESS_ORDER_ZERO_INITIAL }
    var orderZeroTotal = LOSSLESS_SYMBOLS * LOSSLESS_ORDER_ZERO_INITIAL
    val orderTwo = mutableMapOf<Int, IntArray>()
    var previous = 0

    for (offset in 0 until length) {
        val x = offset % width
        val y = offset / width
        val zeroOffset = losslessZeroContext(residuals, offset, x, y, width) shl 1
        val zero = zeroCounts[zeroOffset]
        val nonzero = zeroCounts[zeroOffset + 1]
        val bit = if (decoder.frequency(zero + nonzero) < zero) 0 else 1
        if (bit == 0) decoder.decode(0, zero) else decoder.decode(zero, nonzero)
        zeroCounts[zeroOffset + bit] = zeroCounts[zeroOffset + bit] + LOSSLESS_ZERO_INCREMENT
        if (zeroCounts[zeroOffset] + zeroCounts[zeroOffset + 1] >= LOSSLESS_ZERO_LIMIT) {
            zeroCounts[zeroOffset] = (zeroCounts[zeroOffset] shr 1).coerceAtLeast(1)
            zeroCounts[zeroOffset + 1] = (zeroCounts[zeroOffset + 1] shr 1).coerceAtLeast(1)
        }

        var symbol = 0
        if (bit == 1) {
            val above = if (offset >= width) residuals[offset - width].toInt() and 0xFF else 0
            val second = orderTwo.getOrPut((previous shl 8) or above) {
                IntArray(LOSSLESS_SYMBOLS + 1)
            }
            val firstOffset = previous shl 8
            val excludedZero = second[0] + orderOne[firstOffset] + orderZero[0]
            val total = second[LOSSLESS_TOTAL_INDEX] + orderOneTotals[previous] +
                orderZeroTotal - excludedZero
            val target = decoder.frequency(total)
            var cumulative = 0
            symbol = 1
            var frequency = second[1] + orderOne[firstOffset + 1] + orderZero[1]
            while (cumulative + frequency <= target) {
                cumulative += frequency
                symbol += 1
                if (symbol >= LOSSLESS_SYMBOLS) {
                    throw SamMaskException("Lossless mask payload is malformed.")
                }
                frequency = second[symbol] + orderOne[firstOffset + symbol] + orderZero[symbol]
            }
            decoder.decode(cumulative, frequency)
            second[symbol] = (second[symbol] + LOSSLESS_ORDER_TWO_STEP) and 0xFFFF
            second[LOSSLESS_TOTAL_INDEX] =
                (second[LOSSLESS_TOTAL_INDEX] + LOSSLESS_ORDER_TWO_STEP) and 0xFFFF
            if (second[LOSSLESS_TOTAL_INDEX] >= LOSSLESS_ORDER_TWO_LIMIT) {
                var scaledTotal = 0
                for (value in 0 until LOSSLESS_SYMBOLS) {
                    second[value] = second[value] shr 1
                    scaledTotal += second[value]
                }
                second[LOSSLESS_TOTAL_INDEX] = scaledTotal and 0xFFFF
            }
            orderOne[firstOffset + symbol] =
                (orderOne[firstOffset + symbol] + LOSSLESS_ORDER_ONE_INCREMENT) and 0xFFFF
            orderOneTotals[previous] = orderOneTotals[previous] + LOSSLESS_ORDER_ONE_INCREMENT
            if (orderOneTotals[previous] >= LOSSLESS_ORDER_ONE_LIMIT) {
                var scaledTotal = 0
                for (value in 0 until LOSSLESS_SYMBOLS) {
                    val scaled = (orderOne[firstOffset + value] shr 1).coerceAtLeast(1)
                    orderOne[firstOffset + value] = scaled
                    scaledTotal += scaled
                }
                orderOneTotals[previous] = scaledTotal
            }
            orderZero[symbol] = (orderZero[symbol] + LOSSLESS_ORDER_ZERO_STEP) and 0xFFFF
            orderZeroTotal += LOSSLESS_ORDER_ZERO_STEP
            if (orderZeroTotal >= LOSSLESS_ORDER_ZERO_LIMIT) {
                var scaledTotal = 0
                for (value in 0 until LOSSLESS_SYMBOLS) {
                    orderZero[value] = orderZero[value] shr 1
                    scaledTotal += orderZero[value]
                }
                orderZeroTotal = scaledTotal
            }
        }
        residuals[offset] = symbol.toByte()
        output[offset] = losslessUnzig(
            symbol, losslessPredict(output, offset, x, y, selector, width)
        ).toByte()
        previous = symbol
    }
    return output
}

/**
 * Decodes one mask payload (marker + base85) to a row-major 0/1 raster of
 * exactly width*height bytes. Dispatches on the marker: `!` one_bit,
 * `~` lossless (grayscale coverage thresholded at >= 129).
 */
fun decodeMaskPayload(payload: String, width: Int, height: Int): ByteArray {
    if (width <= 0 || height <= 0) {
        throw SamMaskException("Mask dimensions must be positive.")
    }
    val marker = payload.firstOrNull()
        ?: throw SamMaskException("Mask payload is empty.")
    if (marker == '~') {
        val coverage = decodeLosslessRaster(payload, width, height)
        return ByteArray(coverage.size) { i ->
            if ((coverage[i].toInt() and 0xFF) >= LOSSLESS_THRESHOLD) 1 else 0
        }
    }
    if (marker != '!') throw SamMaskException("Mask payload has an unknown marker.")
    val raster = decodeOneBitRaster(payload, width, height)
    if (encodeOneBitRaster(raster, width, height) != payload) {
        throw SamMaskException("Mask payload is not canonical or has invalid finalization.")
    }
    return raster
}

/**
 * One parsed instance record: inclusive box in source pixels plus the
 * mask raster dims (rows H first) and raw marked payload.
 */
data class SamRecord(
    val objectId: String,
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
    val frameW: Int,
    val frameH: Int,
    val maskH: Int,
    val maskW: Int,
    val marker: Char,
    val payload: String
)

private val boxTokenPattern = Regex("<\\|box;([^|]*)\\|>")
private val maskTokenPattern = Regex("<\\|mask;([^|]*)\\|>")
private val frameHeaderPattern = Regex("^<(\\d+)f>")
private val intPattern = Regex("-?[0-9]+")
private val uintPattern = Regex("[0-9]+")
private val maskDataPattern = Regex("([0-9]+),([0-9]+),([!~].+)")

private fun safeInt(text: String): Int = try {
    text.toInt()
} catch (e: NumberFormatException) {
    throw SamMaskException("Numeric field out of range: ${text.take(24)}")
}

private fun readFields(body: String, known: Set<String>): Map<String, String?>? {
    val values = mutableMapOf<String, String?>()
    for (part in body.split(";")) {
        val field = part.trim()
        if (field.isEmpty()) continue
        val key = field.substringBefore("=").trim()
        val value = if ("=" in field) field.substringAfter("=").trim() else null
        if (key !in known) continue // Unknown fields are ignored (forward-compat).
        if (key in values && key != "c") return null // Repeated key: malformed.
        values[key] = value
    }
    return values
}

/**
 * Parses one `<0f>` output line into instance records. Strict: any
 * malformed record fails the whole line (the caller falls back to
 * Spark-only rather than measuring a partial union).
 */
fun parseSegmentationLine(line: String): List<SamRecord> {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return emptyList()
    val header = frameHeaderPattern.find(trimmed)
        ?: throw SamMaskException("Segmentation line has no frame header: ${trimmed.take(40)}")
    if (header.groupValues[1] != "0") {
        throw SamMaskException("Expected image frame <0f>, got <${header.groupValues[1]}f>.")
    }
    val body = trimmed.substring(header.range.last + 1)
    if (body.isEmpty()) throw SamMaskException("Segmentation line has no records.")
    val records = mutableListOf<SamRecord>()
    // Records: optional comma, decimal object id, then box+mask tokens.
    val recordPattern = Regex(",?(\\d+)((?:<\\|[A-Za-z][A-Za-z0-9_.-]*(?:;[^|]*)?\\|>)+)")
    var pos = 0
    while (pos < body.length) {
        val match = recordPattern.matchAt(body, pos)
            ?: throw SamMaskException("Malformed SAM API object record.")
        val objectId = match.groupValues[1]
        val tokensText = match.groupValues[2]
        val boxes = boxTokenPattern.findAll(tokensText).toList()
        val masks = maskTokenPattern.findAll(tokensText).toList()
        if (boxes.size != 1 || masks.size != 1) {
            throw SamMaskException("Record $objectId needs exactly one box and one mask.")
        }
        val box = readFields(
            boxes[0].groupValues[1],
            setOf("x1", "y1", "x2", "y2", "w", "h", "c")
        ) ?: throw SamMaskException("Record $objectId has malformed box fields.")
        val mask = readFields(
            masks[0].groupValues[1], setOf("x", "y", "data", "c")
        ) ?: throw SamMaskException("Record $objectId has malformed mask fields.")
        val coords = listOf("x1", "y1", "x2", "y2").map { box[it] }
        val dims = listOf("w", "h").map { box[it] }
        if (coords.any { it == null || !intPattern.matches(it) } ||
            dims.any { it == null || !uintPattern.matches(it) } ||
            mask["x"] != "0" || mask["y"] != "0"
        ) {
            throw SamMaskException("Record $objectId has invalid coordinates.")
        }
        val (x1, y1, x2, y2) = coords.map { safeInt(it!!) }
        val frameW = safeInt(dims[0]!!)
        val frameH = safeInt(dims[1]!!)
        if (frameW <= 0 || frameH <= 0 || frameW > MAX_FRAME_DIM || frameH > MAX_FRAME_DIM ||
            frameW.toLong() * frameH > MAX_FRAME_AREA
        ) {
            throw SamMaskException("Record $objectId has implausible frame dimensions.")
        }
        // Inclusive box, must sit inside the frame (mirrors Meta's parser).
        if (x1 < 0 || y1 < 0 || x2 < x1 || y2 < y1 || x2 >= frameW || y2 >= frameH) {
            throw SamMaskException("Record $objectId box is outside its frame.")
        }
        val data = maskDataPattern.matchEntire(mask["data"] ?: "")
            ?: throw SamMaskException("Record $objectId has a malformed mask data field.")
        val maskH = safeInt(data.groupValues[1])
        val maskW = safeInt(data.groupValues[2])
        if (maskH <= 0 || maskW <= 0 || maskH > frameH || maskW > frameW) {
            throw SamMaskException("Record $objectId has implausible mask dimensions.")
        }
        records.add(
            SamRecord(
                objectId = objectId,
                x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                frameW = frameW, frameH = frameH,
                maskH = maskH, maskW = maskW,
                marker = data.groupValues[3][0],
                payload = data.groupValues[3]
            )
        )
        pos = match.range.last + 1
    }
    val frameDims = records.map { it.frameW to it.frameH }.toSet()
    if (frameDims.size != 1) {
        throw SamMaskException("Records disagree on frame dimensions.")
    }
    return records
}

/**
 * One-line diagnosis of the tray instances SAM returned: count plus
 * each instance's box, box area (the coverage denominator), mask dims,
 * and decoded pixel count (clipped as composited). Undecodable records
 * report their error instead of throwing, so one corrupt payload
 * diagnoses instead of hiding the rest. Single-line for console logs.
 */
fun describeTrayInstances(records: List<SamRecord>): String {
    if (records.isEmpty()) return "0 tray instances"
    return buildString {
        append(records.size)
        append(if (records.size == 1) " tray instance: " else " tray instances: ")
        records.forEachIndexed { i, r ->
            if (i > 0) append("; ")
            val ones = try {
                compositeFrameGrid(listOf(r)).first.count { it == 1 }.toString()
            } catch (e: Exception) {
                "undecodable (${(e.message ?: "error").replace('\n', ' ').take(60)})"
            }
            val boxArea = (r.x2 - r.x1 + 1) * (r.y2 - r.y1 + 1)
            append("#$i box=[${r.x1},${r.y1},${r.x2},${r.y2}] boxArea=$boxArea ")
            append("mask=${r.maskW}x${r.maskH} ones=$ones")
        }
    }
}

/**
 * One-line diagnosis of the plant instances SAM returned: count, unioned
 * pixel count in frame units, and the union box plus its area. Ones far
 * below the union area mean eroded/thresholded masks; a small union box
 * means missed instances (recall). Undecodable unions report their error
 * instead of throwing. Single-line for console logs.
 */
fun describePlantSummary(records: List<SamRecord>): String {
    if (records.isEmpty()) return "0 plant instances"
    val ones = try {
        compositeFrameGrid(records).first.count { it == 1 }.toString()
    } catch (e: Exception) {
        "undecodable (${(e.message ?: "error").replace('\n', ' ').take(60)})"
    }
    val x1 = records.minOf { it.x1 }
    val y1 = records.minOf { it.y1 }
    val x2 = records.maxOf { it.x2 }
    val y2 = records.maxOf { it.y2 }
    val unionArea = (x2 - x1 + 1) * (y2 - y1 + 1)
    return "${records.size} plant instances: ones=$ones frame px, " +
        "unionBox=[$x1,$y1,$x2,$y2] unionArea=$unionArea"
}

/**
 * Decodes every record and unions the rasters onto one full-frame grid
 * (each raster nearest-neighbor-scaled onto its box, clipped to the
 * frame). Returns the row-major 0/1 grid plus its width and height,
 * ready for [SamSegmenter.fromMask]. Frame-sized (not union-box) so
 * sparse trays measure near-zero instead of ~100%: the capture flow
 * frames the tray to fill the photo, so frame area approximates tray
 * area.
 */
fun compositeFrameGrid(records: List<SamRecord>): Triple<IntArray, Int, Int> {
    require(records.isNotEmpty()) { "No SAM records to composite." }
    val frameW = records[0].frameW
    val frameH = records[0].frameH
    val grid = IntArray(frameW * frameH)
    for (record in records) {
        val raster = decodeMaskPayload(record.payload, record.maskW, record.maskH)
        val boxW = record.x2 - record.x1 + 1
        val boxH = record.y2 - record.y1 + 1
        // Masks arrive downsampled (long edge 1024 on live traffic), so
        // each box pixel samples its nearest mask cell (floor) — up or
        // down. A 1:1 paste would cram the mask into the box corner and
        // undercount by the square of the scale factor.
        for (boxRow in 0 until boxH) {
            val frameY = record.y1 + boxRow
            if (frameY >= frameH) break
            val rasterRow = (boxRow * record.maskH / boxH) * record.maskW
            for (boxCol in 0 until boxW) {
                val frameX = record.x1 + boxCol
                if (frameX >= frameW) break
                if (raster[rasterRow + boxCol * record.maskW / boxW].toInt() == 1) {
                    grid[frameY * frameW + frameX] = 1
                }
            }
        }
    }
    return Triple(grid, frameW, frameH)
}
