package com.xiaoxiong.weather.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoxiong.weather.R

@Composable
fun PrivacyGateDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onReadPrivacy: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(stringResource(R.string.privacy_gate_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.privacy_gate_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.privacy_gate_bullet_location))
                Text(stringResource(R.string.privacy_gate_bullet_notify))
                Text(stringResource(R.string.privacy_gate_bullet_network))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.privacy_gate_footer),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.privacy_gate_read),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onReadPrivacy),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.privacy_gate_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.privacy_gate_decline))
            }
        },
    )
}
