package com.taxiapp.driver.utils

import com.taxiapp.driver.BuildConfig

fun toFullImageUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    val base = BuildConfig.BASE_URL.trimEnd('/')
    val path = url.trimStart('/')
    return "$base/$path"
}