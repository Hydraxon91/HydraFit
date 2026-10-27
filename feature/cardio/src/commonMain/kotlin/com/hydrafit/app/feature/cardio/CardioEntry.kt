package com.hydrafit.app.feature.cardio

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hydrafit.app.core.navigation.LocalCardioNavigator
import hydrafit.feature.cardio.generated.resources.Res
import hydrafit.feature.cardio.generated.resources.cardio_entry_open
import org.jetbrains.compose.resources.stringResource

/**
 * The Logger's entry into the pushed Cardio destination. The shell provides
 * [LocalCardioNavigator], so this action needs no route, state or module from
 * `:feature:cardio`'s own navigation graph, and the Logger never imports another feature.
 */
@Composable
fun CardioEntry(modifier: Modifier = Modifier) {
    val openCardio = LocalCardioNavigator.current
    TextButton(
        onClick = openCardio,
        modifier = modifier.testTag(CardioTestTags.ENTRY)
    ) {
        Text(stringResource(Res.string.cardio_entry_open))
    }
}
