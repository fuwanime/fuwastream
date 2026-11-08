package com.fuwanime.ui.legal

import androidx.compose.foundation.layout.fillMaxHeight
import com.fuwanime.ui.components.readableWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fuwanime.BuildConfig
import com.fuwanime.R
import com.fuwanime.util.legalLocale

/**
 * Legal copy shown inside the app. Mirrors docs/legal/privacy.md and docs/legal/terms.md in the
 * repository, so the published policy and the in-app one cannot drift apart unnoticed.
 * The text only exists in Indonesian and English (`legal_*`); see [legalResources].
 */
object LegalText {

    const val DEVELOPER_NAME = "FuwaStream Community (YuzakiNetwork)"
    const val DEVELOPER_EMAIL = "fuwanimereal@gmail.com"
    const val DEVELOPER_WEB = "https://github.com/fuwanime/fuwastream"

    private val PRIVACY = intArrayOf(
        R.string.legal_privacy_updated, R.string.legal_privacy_summary, R.string.legal_privacy_data_stored,
        R.string.legal_privacy_data_not_collected, R.string.legal_privacy_third_party, R.string.legal_privacy_permissions,
        R.string.legal_privacy_backup, R.string.legal_privacy_children, R.string.legal_privacy_security,
        R.string.legal_privacy_rights, R.string.legal_privacy_changes, R.string.legal_privacy_contact,
    )
    private val TERMS = intArrayOf(
        R.string.legal_terms_updated, R.string.legal_terms_acceptance, R.string.legal_terms_license,
        R.string.legal_terms_third_party, R.string.legal_terms_prohibited, R.string.legal_terms_fees,
        R.string.legal_terms_warranty, R.string.legal_terms_liability, R.string.legal_terms_changes,
        R.string.legal_terms_contact,
    )

    /** Sections are separated by a blank line; each is "HEADING\ntext". */
    fun privacy(res: Resources): String = PRIVACY.joinToString("\n\n") { res.getString(it) }

    fun terms(res: Resources): String = TERMS.joinToString("\n\n") { res.getString(it) }

    fun about(res: Resources): String = listOf(
        res.getString(R.string.legal_about_version, BuildConfig.VERSION_NAME),
        res.getString(R.string.legal_about_developer, DEVELOPER_NAME, DEVELOPER_EMAIL, DEVELOPER_WEB),
        res.getString(R.string.legal_about_license),
        res.getString(R.string.legal_about_content),
        res.getString(R.string.legal_about_affiliation),
    ).joinToString("\n\n")

    /** Title (in the app language, from [appRes]) and body (from [legalRes]) for a Legal route key. */
    fun forKey(key: String, appRes: Resources, legalRes: Resources): Pair<String, String> = when (key) {
        "privacy" -> appRes.getString(R.string.mine_privacy_policy) to privacy(legalRes)
        "terms" -> appRes.getString(R.string.mine_terms) to terms(legalRes)
        else -> appRes.getString(R.string.mine_about_title) to about(legalRes)
    }
}

/** Resources in the legal-text language: Indonesian for Indonesian users, English for everyone else. */
@Composable
fun legalResources(): Resources {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    return remember(config) {
        context.createConfigurationContext(Configuration(config).apply { setLocale(legalLocale(config.locales[0])) }).resources
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalRoute(type: String, onBack: () -> Unit) {
    val appRes = LocalContext.current.resources
    val legalRes = legalResources()
    val (title, body) = remember(type, appRes, legalRes) { LegalText.forKey(type, appRes, legalRes) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.mine_back)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxHeight().readableWidth(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                start = 24.dp,
                end = 24.dp,
                bottom = 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
