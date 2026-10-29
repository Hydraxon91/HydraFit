package com.hydrafit.app.core.userdata.settings

import android.content.Context

class AndroidGroqApiKeyStore(context: Context) :
    GroqApiKeyStore,
    ApiKeyStore by AndroidKeystoreApiKeyStore(
        context = context,
        keyAlias = "hydrafit_groq_api_key",
        preferencesName = "hydrafit_groq_secure"
    )
