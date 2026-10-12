package com.hydrafit.app.core.domain.workout

/**
 * Unicode 17.0.0 default full case folding, generated from the Unicode Character Database
 * `CaseFolding.txt` (statuses `C` and `F`, `F` winning where both apply).
 *
 * Folding removes case differences only, so it is accent-sensitive: `Épaules` and `ÉPAULES` fold
 * together while `Epaules` does not. It is applied both to stored search projections and to query
 * input, so history search is Unicode-insensitive without relying on a platform Unicode version or
 * SQLite's ASCII-only `NOCASE` collation.
 *
 * Data files are copyright (c) Unicode, Inc. and distributed under the Unicode License v3; the
 * required notice is retained in `core/domain/src/commonMain/kotlin/.../UnicodeCaseFold.kt`'s
 * generated tables below.
 *
 * `scripts/check-case-fold-table.py <CaseFolding.txt>` re-verifies every mapping in this file against
 * the published data, so the table can be re-checked after a Unicode release or a hand edit.
 */
fun unicodeCaseFold(value: String): String = buildString(value.length) {
    var index = 0
    while (index < value.length) {
        val leading = value[index].code
        val codePoint = if (
            leading in HIGH_SURROGATE_START..HIGH_SURROGATE_END &&
            index + 1 < value.length &&
            value[index + 1].code in LOW_SURROGATE_START..LOW_SURROGATE_END
        ) {
            val trailing = value[++index].code
            ((leading - HIGH_SURROGATE_START) shl 10) + (trailing - LOW_SURROGATE_START) + 0x10000
        } else {
            leading
        }
        index++
        val exception = FOLD_EXCEPTIONS[codePoint]
        if (exception != null) {
            exception.forEach { appendCodePoint(it) }
        } else {
            appendCodePoint(codePoint + foldDelta(codePoint))
        }
    }
}

private fun StringBuilder.appendCodePoint(codePoint: Int) {
    if (codePoint <= 0xFFFF) {
        append(codePoint.toChar())
    } else {
        val scalar = codePoint - 0x10000
        append((HIGH_SURROGATE_START + (scalar shr 10)).toChar())
        append((LOW_SURROGATE_START + (scalar and 0x3FF)).toChar())
    }
}

/**
 * Delta applied by the single-code-point folding ranges. [FOLD_RANGES] is sorted by `start`, so a
 * code point before the current range's start cannot appear in any later range.
 */
private fun foldDelta(codePoint: Int): Int {
    for (range in FOLD_RANGES) {
        if (codePoint < range.start) return 0
        if (codePoint <= range.end && (codePoint - range.start) % range.step == 0) {
            return range.delta
        }
    }
    return 0
}

/** `start-end:delta` or `start-end/step:delta`, both in hexadecimal; deltas may be negative. */
internal val FOLD_RANGES: List<FoldRange> = foldRangeData().map { entry ->
    val codePoints = entry.substringBefore(':')
    FoldRange(
        start = codePoints.substringBefore('-').toInt(16),
        end = codePoints.substringAfter('-').substringBefore('/').toInt(16),
        step = if ('/' in codePoints) codePoints.substringAfter('/').toInt() else 1,
        delta = entry.substringAfter(':').toInt(16)
    )
}

/** `source:target[,target]`, all hexadecimal; targets may be multiple code points. Internal so a test
 *  can assert the table's invariants against the generation contract. */
internal val FOLD_EXCEPTIONS: Map<Int, List<Int>> = foldExceptionData().associate { entry ->
    entry.substringBefore(':').toInt(16) to
        entry.substringAfter(':').split(',').map { it.toInt(16) }
}

internal class FoldRange(val start: Int, val end: Int, val step: Int, val delta: Int)

private fun foldRangeData(): List<String> = """
    41-5a:20
    b5-b5:307
    c0-d6:20
    d8-de:20
    100-12e/2:1
    132-136/2:1
    139-147/2:1
    14a-176/2:1
    178-178:-79
    179-17d/2:1
    17f-17f:-10c
    181-181:d2
    182-184/2:1
    186-186:ce
    187-187:1
    189-18a:cd
    18b-18b:1
    18e-18e:4f
    18f-18f:ca
    190-190:cb
    191-191:1
    193-193:cd
    194-194:cf
    196-196:d3
    197-197:d1
    198-198:1
    19c-19c:d3
    19d-19d:d5
    19f-19f:d6
    1a0-1a4/2:1
    1a6-1a6:da
    1a7-1a7:1
    1a9-1a9:da
    1ac-1ac:1
    1ae-1ae:da
    1af-1af:1
    1b1-1b2:d9
    1b3-1b5/2:1
    1b7-1b7:db
    1b8-1b8:1
    1bc-1bc:1
    1c4-1c4:2
    1c5-1c5:1
    1c7-1c7:2
    1c8-1c8:1
    1ca-1ca:2
    1cb-1db/2:1
    1de-1ee/2:1
    1f1-1f1:2
    1f2-1f4/2:1
    1f6-1f6:-61
    1f7-1f7:-38
    1f8-21e/2:1
    220-220:-82
    222-232/2:1
    23a-23a:2a2b
    23b-23b:1
    23d-23d:-a3
    23e-23e:2a28
    241-241:1
    243-243:-c3
    244-244:45
    245-245:47
    246-24e/2:1
    345-345:74
    370-372/2:1
    376-376:1
    37f-37f:74
    386-386:26
    388-38a:25
    38c-38c:40
    38e-38f:3f
    391-3a1:20
    3a3-3ab:20
    3c2-3c2:1
    3cf-3cf:8
    3d0-3d0:-1e
    3d1-3d1:-19
    3d5-3d5:-f
    3d6-3d6:-16
    3d8-3ee/2:1
    3f0-3f0:-36
    3f1-3f1:-30
    3f4-3f4:-3c
    3f5-3f5:-40
    3f7-3f7:1
    3f9-3f9:-7
    3fa-3fa:1
    3fd-3ff:-82
    400-40f:50
    410-42f:20
    460-480/2:1
    48a-4be/2:1
    4c0-4c0:f
    4c1-4cd/2:1
    4d0-52e/2:1
    531-556:30
    10a0-10c5:1c60
    10c7-10c7:1c60
    10cd-10cd:1c60
    13f8-13fd:-8
    1c80-1c80:-184e
    1c81-1c81:-184d
    1c82-1c82:-1844
    1c83-1c84:-1842
    1c85-1c85:-1843
    1c86-1c86:-183c
    1c87-1c87:-1824
    1c88-1c88:89c3
    1c89-1c89:1
    1c90-1cba:-bc0
    1cbd-1cbf:-bc0
    1e00-1e94/2:1
    1e9b-1e9b:-3a
    1ea0-1efe/2:1
    1f08-1f0f:-8
    1f18-1f1d:-8
    1f28-1f2f:-8
    1f38-1f3f:-8
    1f48-1f4d:-8
    1f59-1f5f/2:-8
    1f68-1f6f:-8
    1fb8-1fb9:-8
    1fba-1fbb:-4a
    1fbe-1fbe:-1c05
    1fc8-1fcb:-56
    1fd8-1fd9:-8
    1fda-1fdb:-64
    1fe8-1fe9:-8
    1fea-1feb:-70
    1fec-1fec:-7
    1ff8-1ff9:-80
    1ffa-1ffb:-7e
    2126-2126:-1d5d
    212a-212a:-20bf
    212b-212b:-2046
    2132-2132:1c
    2160-216f:10
    2183-2183:1
    24b6-24cf:1a
    2c00-2c2f:30
    2c60-2c60:1
    2c62-2c62:-29f7
    2c63-2c63:-ee6
    2c64-2c64:-29e7
    2c67-2c6b/2:1
    2c6d-2c6d:-2a1c
    2c6e-2c6e:-29fd
    2c6f-2c6f:-2a1f
    2c70-2c70:-2a1e
    2c72-2c72:1
    2c75-2c75:1
    2c7e-2c7f:-2a3f
    2c80-2ce2/2:1
    2ceb-2ced/2:1
    2cf2-2cf2:1
    a640-a66c/2:1
    a680-a69a/2:1
    a722-a72e/2:1
    a732-a76e/2:1
    a779-a77b/2:1
    a77d-a77d:-8a04
    a77e-a786/2:1
    a78b-a78b:1
    a78d-a78d:-a528
    a790-a792/2:1
    a796-a7a8/2:1
    a7aa-a7aa:-a544
    a7ab-a7ab:-a54f
    a7ac-a7ac:-a54b
    a7ad-a7ad:-a541
    a7ae-a7ae:-a544
    a7b0-a7b0:-a512
    a7b1-a7b1:-a52a
    a7b2-a7b2:-a515
    a7b3-a7b3:3a0
    a7b4-a7c2/2:1
    a7c4-a7c4:-30
    a7c5-a7c5:-a543
    a7c6-a7c6:-8a38
    a7c7-a7c9/2:1
    a7cb-a7cb:-a567
    a7cc-a7da/2:1
    a7dc-a7dc:-a641
    a7f5-a7f5:1
    ab70-abbf:-97d0
    ff21-ff3a:20
    10400-10427:28
    104b0-104d3:28
    10570-1057a:27
    1057c-1058a:27
    1058c-10592:27
    10594-10595:27
    10c80-10cb2:40
    10d50-10d65:20
    118a0-118bf:20
    16e40-16e5f:20
    16ea0-16eb8:1b
    1e900-1e921:22
""".trimIndent().lines().filter(String::isNotBlank)

private fun foldExceptionData(): List<String> = """
    df:73,73
    130:69,307
    149:2bc,6e
    1f0:6a,30c
    390:3b9,308,301
    3b0:3c5,308,301
    587:565,582
    1e96:68,331
    1e97:74,308
    1e98:77,30a
    1e99:79,30a
    1e9a:61,2be
    1e9e:73,73
    1f50:3c5,313
    1f52:3c5,313,300
    1f54:3c5,313,301
    1f56:3c5,313,342
    1f80:1f00,3b9
    1f81:1f01,3b9
    1f82:1f02,3b9
    1f83:1f03,3b9
    1f84:1f04,3b9
    1f85:1f05,3b9
    1f86:1f06,3b9
    1f87:1f07,3b9
    1f88:1f00,3b9
    1f89:1f01,3b9
    1f8a:1f02,3b9
    1f8b:1f03,3b9
    1f8c:1f04,3b9
    1f8d:1f05,3b9
    1f8e:1f06,3b9
    1f8f:1f07,3b9
    1f90:1f20,3b9
    1f91:1f21,3b9
    1f92:1f22,3b9
    1f93:1f23,3b9
    1f94:1f24,3b9
    1f95:1f25,3b9
    1f96:1f26,3b9
    1f97:1f27,3b9
    1f98:1f20,3b9
    1f99:1f21,3b9
    1f9a:1f22,3b9
    1f9b:1f23,3b9
    1f9c:1f24,3b9
    1f9d:1f25,3b9
    1f9e:1f26,3b9
    1f9f:1f27,3b9
    1fa0:1f60,3b9
    1fa1:1f61,3b9
    1fa2:1f62,3b9
    1fa3:1f63,3b9
    1fa4:1f64,3b9
    1fa5:1f65,3b9
    1fa6:1f66,3b9
    1fa7:1f67,3b9
    1fa8:1f60,3b9
    1fa9:1f61,3b9
    1faa:1f62,3b9
    1fab:1f63,3b9
    1fac:1f64,3b9
    1fad:1f65,3b9
    1fae:1f66,3b9
    1faf:1f67,3b9
    1fb2:1f70,3b9
    1fb3:3b1,3b9
    1fb4:3ac,3b9
    1fb6:3b1,342
    1fb7:3b1,342,3b9
    1fbc:3b1,3b9
    1fc2:1f74,3b9
    1fc3:3b7,3b9
    1fc4:3ae,3b9
    1fc6:3b7,342
    1fc7:3b7,342,3b9
    1fcc:3b7,3b9
    1fd2:3b9,308,300
    1fd3:3b9,308,301
    1fd6:3b9,342
    1fd7:3b9,308,342
    1fe2:3c5,308,300
    1fe3:3c5,308,301
    1fe4:3c1,313
    1fe6:3c5,342
    1fe7:3c5,308,342
    1ff2:1f7c,3b9
    1ff3:3c9,3b9
    1ff4:3ce,3b9
    1ff6:3c9,342
    1ff7:3c9,342,3b9
    1ffc:3c9,3b9
    fb00:66,66
    fb01:66,69
    fb02:66,6c
    fb03:66,66,69
    fb04:66,66,6c
    fb05:73,74
    fb06:73,74
    fb13:574,576
    fb14:574,565
    fb15:574,56b
    fb16:57e,576
    fb17:574,56d
""".trimIndent().lines().filter(String::isNotBlank)

private const val HIGH_SURROGATE_START = 0xD800
private const val HIGH_SURROGATE_END = 0xDBFF
private const val LOW_SURROGATE_START = 0xDC00
private const val LOW_SURROGATE_END = 0xDFFF
