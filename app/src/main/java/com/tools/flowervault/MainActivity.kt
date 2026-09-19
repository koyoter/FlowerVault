package com.tools.flowervault

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tools.flowervault.ui.AppViewModel
import com.tools.flowervault.ui.EntryUi
import com.tools.flowervault.ui.FlowerVaultTheme
import com.tools.flowervault.ui.MasterUi
import com.tools.flowervault.ui.SetupScreen
import com.tools.flowervault.update.UpdateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlowerVaultTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val masters by vm.mastersUi.collectAsStateWithLifecycle()
    val activeId by vm.activeMasterId.collectAsStateWithLifecycle()
    val showAdd by vm.showAdd.collectAsStateWithLifecycle()

    when {
        // 尚未加载完成：空帧避免设置页闪烁
        masters == null -> Box(Modifier.fillMaxSize())
        // 没有任何主密码：先设置才能进入单页
        masters!!.isEmpty() && !showAdd ->
            SetupScreen(first = true, onCreate = { n, p -> vm.addMaster(n, p) })
        showAdd ->
            SetupScreen(
                first = false,
                onCreate = { n, p -> vm.addMaster(n, p) },
                onCancel = { vm.cancelAdd() }
            )
        else -> MainScreen(vm, masters!!, activeId)
    }

    // 应用内更新检测（24h 节流，失败静默）
    UpdateFlow()
}

@Composable
fun MainScreen(vm: AppViewModel, masters: List<MasterUi>, activeId: Long?) {
    val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val search by vm.search.collectAsStateWithLifecycle()
    val input by vm.input.collectAsStateWithLifecycle()
    val entries by vm.visibleEntries.collectAsStateWithLifecycle()
    val activeName by vm.activeName.collectAsStateWithLifecycle()

    var masterToDelete by remember { mutableStateOf<MasterUi?>(null) }
    var entryToDelete by remember { mutableStateOf<EntryUi?>(null) }

    val inputFocus = remember { FocusRequester() }

    fun copyOut(pwd: String?) {
        if (pwd == null) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("pwd", pwd))
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        (context as? Activity)?.finish()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "主密码",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                masters.forEach { m ->
                    NavigationDrawerItem(
                        label = {
                            Column {
                                Text(m.name)
                                Text(
                                    m.maskedPwd,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        },
                        selected = m.id == activeId,
                        onClick = {
                            vm.switchMaster(m.id)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Key, contentDescription = null) },
                        badge = {
                            IconButton(onClick = { masterToDelete = m }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除主密码",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("新增主密码") },
                    selected = false,
                    onClick = {
                        vm.showAdd()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 顶部：汉堡按钮 + 常驻搜索框
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Default.Menu, contentDescription = "菜单")
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { vm.search.value = it },
                    placeholder = { Text("搜索记录…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            if (activeName.isNotBlank()) {
                Text(
                    activeName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 中部：历史记录列表（按复制次数降序）
            if (entries.isEmpty()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (search.isBlank()) "暂无记录，输入内容生成第一条密码" else "无匹配记录",
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(entries, key = { it.id }) { e ->
                        EntryRow(
                            entry = e,
                            onClick = { vm.input.value = e.text },
                            onCopy = { copyOut(vm.regenerate(e)) },
                            onDelete = { entryToDelete = e }
                        )
                    }
                }
            }

            // 底部：输入框 + 生成按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { vm.input.value = it },
                    placeholder = { Text("输入内容") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(inputFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { copyOut(vm.generate(input)) })
                )
                Button(onClick = {
                    if (input.isBlank()) {
                        Toast.makeText(context, "请输入内容", Toast.LENGTH_SHORT).show()
                    } else {
                        copyOut(vm.generate(input))
                    }
                }) {
                    Text("生成")
                }
            }
        }
    }

    // 默认聚焦输入框（切换主密码后同样聚焦）
    LaunchedEffect(activeId) { inputFocus.requestFocus() }

    masterToDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { masterToDelete = null },
            title = { Text("删除主密码") },
            text = { Text("确定删除「${m.name}」？其下所有历史记录将一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMaster(m.id)
                    masterToDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { masterToDelete = null }) { Text("取消") }
            }
        )
    }

    entryToDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("删除记录") },
            text = { Text("确定删除「${e.text}」？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteEntry(e.id)
                    entryToDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun EntryRow(entry: EntryUi, onClick: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "复制 ${entry.copyCount} 次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = "重新生成并复制")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
