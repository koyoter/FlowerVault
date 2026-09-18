package com.tools.flowervault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * 首次设置 / 新增主密码 复用此页。
 * 进入时自动聚焦备注名输入框；主密码输入过滤与原逻辑一致（英文字母、数字、点）。
 */
@Composable
fun SetupScreen(
    first: Boolean,
    onCreate: (name: String, pwd: String) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    val valid = name.isNotBlank() && pwd.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (first) "设置主密码" else "新增主密码",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "主密码用于派生密码，请牢记；仅支持英文、数字和点",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 20) name = it },
            label = { Text("备注名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pwd,
            onValueChange = { if (it.all { c -> c.isLetterOrDigit() || c == '.' }) pwd = it },
            label = { Text("主密码") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (valid) onCreate(name, pwd) })
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onCancel != null) {
                OutlinedButton(onClick = onCancel) { Text("取消") }
            }
            Button(onClick = { onCreate(name, pwd) }, enabled = valid) {
                Text("保存并进入")
            }
        }
    }

    LaunchedEffect(Unit) { nameFocus.requestFocus() }
}
