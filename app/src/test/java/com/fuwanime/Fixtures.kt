package com.fuwanime

import com.fuwanime.data.remote.ApiResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/** Real API responses (trimmed) under src/test/resources/fixtures, parsed the way Retrofit does. */
object Fixtures {
    val moshi: Moshi = Moshi.Builder().build()

    fun text(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "missing fixture $name" }.readText()

    inline fun <reified T> response(name: String): ApiResponse<T> =
        checkNotNull(
            moshi.adapter<ApiResponse<T>>(Types.newParameterizedType(ApiResponse::class.java, T::class.java))
                .fromJson(text(name)),
        )
}
