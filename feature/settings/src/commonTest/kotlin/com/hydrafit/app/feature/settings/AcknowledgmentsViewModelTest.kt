package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class AcknowledgmentsViewModelTest {

    @Test
    fun surfacesTheInjectedVersionName() {
        val viewModel = AcknowledgmentsViewModel(FakeVersionProvider("0.3.0"))

        assertEquals("0.3.0", viewModel.state.value.versionName)
    }

    private class FakeVersionProvider(override val versionName: String) : AppVersionProvider
}
