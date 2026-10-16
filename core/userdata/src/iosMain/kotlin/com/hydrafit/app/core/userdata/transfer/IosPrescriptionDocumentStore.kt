package com.hydrafit.app.core.userdata.transfer

/**
 * iOS prescription document surface. HydraFit has no iOS document writer in 0.9.x, so this delegates
 * to the unsupported fallback and always reports [isSupported] false.
 */
class IosPrescriptionDocumentStore :
    PrescriptionDocumentStore by UnsupportedPrescriptionDocumentStore()
