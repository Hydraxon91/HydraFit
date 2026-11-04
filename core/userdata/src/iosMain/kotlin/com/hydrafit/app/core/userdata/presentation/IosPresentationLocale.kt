package com.hydrafit.app.core.userdata.presentation

import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle

/**
 * Reads the separator from a decimal-style `NSNumberFormatter`.
 *
 * An instance API is used deliberately: the `NSLocale` class properties are not exposed to
 * Kotlin/Native (the same reason `offsetFromZoneName` builds an `NSDateFormatter`). An unsupported
 * or missing value falls back to the ASCII dot rather than failing a render.
 */
actual fun currentPresentationNumberFormat(): PresentationNumberFormat {
    val formatter = NSNumberFormatter()
    formatter.numberStyle = NSNumberFormatterDecimalStyle
    val separator = formatter.decimalSeparator?.firstOrNull()
    return PresentationNumberFormat(separator?.takeIf { !it.isDigit() } ?: '.')
}
