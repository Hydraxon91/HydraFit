package com.hydrafit.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform