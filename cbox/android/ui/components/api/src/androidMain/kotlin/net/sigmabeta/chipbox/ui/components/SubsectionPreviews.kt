package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.SquareItemListModel
import net.sigmabeta.sage.components.SubsectionHeaderListModel
import net.sigmabeta.sage.components.SubsectionListModel
import net.sigmabeta.sage.components.WideItemListModel
import net.sigmabeta.sage.ui.Icon

@Preview
@Composable
private fun LightWide() {
    ChipboxPreview {
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.background
                )
                .padding(16.dp)
        ) {
            SampleWide()
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DarkWide() {
    ChipboxPreview {
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.background
                )
                .padding(16.dp)
        ) {
            SampleWide()
        }
    }
}

@Preview
@Composable
private fun LightSquare() {
    ChipboxPreview {
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.background
                )
                .padding(16.dp)
        ) {
            SampleSquare()
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DarkSquare() {
    ChipboxPreview {
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.background
                )
                .padding(16.dp)
        ) {
            SampleSquare()
        }
    }
}

@Composable
@Suppress("MagicNumber")
private fun SampleWide() {
    Subsection(
        SubsectionListModel(
            1234L,
            SubsectionHeaderListModel(
                "Composers for this game on VGLS",
            ),
            listOf(
                WideItemListModel(
                    2345L,
                    "Manami Kiyota",
                    null,
                    Icon.Person,
                    actionableId = null,
                    clickAction = SageAction.Noop,
                ),
                WideItemListModel(
                    3456L,
                    "Yasunori Mitsuda",
                    null,
                    Icon.Person,
                    actionableId = null,
                    clickAction = SageAction.Noop,
                ),
                WideItemListModel(
                    4567L,
                    "ACE+",
                    null,
                    Icon.Person,
                    actionableId = null,
                    clickAction = SageAction.Noop,
                ),
            ).toImmutableList()
        ),
        PreviewActionSink { },
        Modifier,
        PaddingValues(horizontal = 8.dp)
    )
}

@Composable
@Suppress("MagicNumber")
private fun SampleSquare() {
    Subsection(
        SubsectionListModel(
            1234L,
            SubsectionHeaderListModel(
                "Composers for this game on VGLS",
            ),
            listOf(
                SquareItemListModel(
                    2345L,
                    "Manami Kiyota",
                    null,
                    Icon.Person,
                    actionableId = null,
                    clickAction = SageAction.Noop,
                ),
                SquareItemListModel(
                    3456L,
                    "Yasunori Mitsuda",
                    null,
                    Icon.Person,
                    actionableId = null,
                    clickAction = SageAction.Noop,
                ),
                SquareItemListModel(
                    4567L,
                    "ACE+",
                    null,
                    Icon.Person,
                    actionableId = null,
                    clickAction = SageAction.Noop,
                ),
            ).toImmutableList()
        ),
        PreviewActionSink { },
        Modifier,
        PaddingValues(horizontal = 8.dp)
    )
}
