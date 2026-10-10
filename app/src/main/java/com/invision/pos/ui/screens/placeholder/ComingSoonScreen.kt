package com.invision.pos.ui.screens.placeholder

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.tooling.preview.Preview
import com.invision.pos.R
import com.invision.pos.ui.components.EmptyState
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.navigation.TopLevelDestination
import com.invision.pos.ui.theme.InvisionTheme

/** Temporary screen for sections that are built in later steps. */
@Composable
fun ComingSoonScreen(
    destination: TopLevelDestination,
    modifier: Modifier = Modifier,
) {
    val sectionName = stringResource(destination.labelRes)
    Scaffold(
        modifier = modifier,
        topBar = { InvisionTopAppBar(title = sectionName) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        EmptyState(
            iconRes = destination.iconRes,
            title = stringResource(R.string.coming_soon_title, sectionName),
            message = stringResource(R.string.coming_soon_body),
            modifier = Modifier.padding(padding),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ComingSoonScreenPreview() {
    InvisionTheme {
        ComingSoonScreen(destination = TopLevelDestination.BILLING)
    }
}
