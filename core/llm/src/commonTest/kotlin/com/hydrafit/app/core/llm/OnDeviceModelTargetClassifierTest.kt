package com.hydrafit.app.core.llm

import com.hydrafit.app.core.userdata.llm.OnDeviceModelTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class OnDeviceModelTargetClassifierTest {

    @Test
    fun treatsQualcommSnapdragonPacksAsNpu() {
        assertEquals(
            OnDeviceModelTarget.NPU,
            classify("Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm")
        )
        assertEquals(
            OnDeviceModelTarget.NPU,
            classify("gemma-4-E2B-it_qualcomm_sm8750.litertlm")
        )
        assertEquals(
            OnDeviceModelTarget.NPU,
            classify("Gemma3-1B-IT_q4_ekv1280_sm8850.litertlm")
        )
    }

    @Test
    fun treatsGenericMarkersAsNpu() {
        assertEquals(OnDeviceModelTarget.NPU, classify("some-npu-build.litertlm"))
        assertEquals(OnDeviceModelTarget.NPU, classify("Gemma-tensor-g4.litertlm"))
    }

    @Test
    fun treatsThePortablePackAsCpuGpu() {
        assertEquals(
            OnDeviceModelTarget.CPU_GPU,
            classify("gemma3-1b-it-int4.litertlm")
        )
        assertEquals(
            OnDeviceModelTarget.CPU_GPU,
            classify("on_device_llm.litertlm")
        )
    }

    @Test
    fun treatsAMissingOrUnknownNameAsCpuGpu() {
        assertEquals(OnDeviceModelTarget.CPU_GPU, classify(null))
        assertEquals(OnDeviceModelTarget.CPU_GPU, classify(""))
    }

    private fun classify(name: String?) = OnDeviceModelTargetClassifier.classify(name)
}
