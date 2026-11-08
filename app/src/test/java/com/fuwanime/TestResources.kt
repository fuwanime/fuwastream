package com.fuwanime

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider

/** App resources under the current Robolectric `qualifiers` (tests set "id" or "en" explicitly). */
fun testResources(): Resources = ApplicationProvider.getApplicationContext<Context>().resources
