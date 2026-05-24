package dev.tireless.abun

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform