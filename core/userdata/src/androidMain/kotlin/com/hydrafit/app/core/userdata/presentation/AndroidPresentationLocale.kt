package com.hydrafit.app.core.userdata.presentation

import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Reads the separator from the JVM/Android default locale.
 *
 * This is `Locale.getDefault()`, which Android keeps in step with the per-app locale, so an app
 * scoped `de-DE` selection is honoured without reading a device-wide value.
 */
actual fun currentPresentationNumberFormat(): PresentationNumberFormat = PresentationNumberFormat(
    DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
)
