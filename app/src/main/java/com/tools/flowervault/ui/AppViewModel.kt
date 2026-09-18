package com.tools.flowervault.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tools.flowervault.PwCalc
import com.tools.flowervault.data.Entry
import com.tools.flowervault.data.MasterPassword
import com.tools.flowervault.data.VaultCipher
import com.tools.flowervault.data.VaultDao
import com.tools.flowervault.data.VaultDb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MasterUi(val id: Long, val name: String, val maskedPwd: String)
data class EntryUi(val id: Long, val text: String, val copyCount: Int, val updatedAt: Long)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val dao: VaultDao = VaultDb.get(app).dao()

    /** 同步快照：供 init 自动选主密码与 currentMaster 读取 */
    private val mastersState: StateFlow<List<MasterPassword>> =
        dao.masters().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val mastersFlow: Flow<List<MasterPassword>> = dao.masters()

    private val _activeMasterId = MutableStateFlow<Long?>(null)
    val activeMasterId: StateFlow<Long?> = _activeMasterId.asStateFlow()

    private val _showAdd = MutableStateFlow(false)
    val showAdd: StateFlow<Boolean> = _showAdd.asStateFlow()

    val search = MutableStateFlow("")
    val input = MutableStateFlow("")

    init {
        viewModelScope.launch {
            mastersState.collect { list ->
                val cur = _activeMasterId.value
                if (list.isNotEmpty() && (cur == null || list.none { it.id == cur })) {
                    // 已按 lastUsedAt 降序，选最近使用的；删除当前主密码后也会自动回退
                    val pick = list.first()
                    _activeMasterId.value = pick.id
                    dao.touchMaster(pick.id, System.currentTimeMillis())
                }
            }
        }
    }

    /** 抽屉里的主密码列表：备注名 + 掩码；null = 尚未加载完成 */
    val mastersUi: StateFlow<List<MasterUi>?> = mastersFlow.map { list ->
        list.mapNotNull { m ->
            val pwd = runCatching { VaultCipher.decrypt(m.pwdEnc) }.getOrNull()
                ?: return@mapNotNull null
            MasterUi(m.id, m.name, mask(pwd))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val rawEntries: Flow<List<Entry>> = _activeMasterId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.entries(id)
    }

    /** 当前主密码下的解密条目 */
    private val entriesUi: StateFlow<List<EntryUi>> = rawEntries.map { list ->
        list.mapNotNull { e ->
            val text = runCatching { VaultCipher.decrypt(e.textEnc) }.getOrNull()
                ?: return@mapNotNull null
            EntryUi(e.id, text, e.copyCount, e.updatedAt)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 页面展示列表：搜索过滤（大小写不敏感）+ 复制次数降序 */
    val visibleEntries: StateFlow<List<EntryUi>> =
        combine(entriesUi, search) { list, q ->
            val filtered = if (q.isBlank()) list
            else list.filter { it.text.contains(q, ignoreCase = true) }
            filtered.sortedWith(
                compareByDescending<EntryUi> { it.copyCount }.thenByDescending { it.updatedAt }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前主密码备注名 */
    val activeName: StateFlow<String> = combine(_activeMasterId, mastersState) { id, list ->
        list.firstOrNull { it.id == id }?.name ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun addMaster(name: String, pwd: String) {
        if (name.isBlank() || pwd.isBlank()) return
        viewModelScope.launch {
            val id = dao.insertMaster(
                MasterPassword(
                    name = name.trim(),
                    pwdEnc = VaultCipher.encrypt(pwd),
                    lastUsedAt = System.currentTimeMillis()
                )
            )
            _activeMasterId.value = id
            _showAdd.value = false
            search.value = ""
            input.value = ""
        }
    }

    fun showAdd() {
        _showAdd.value = true
    }

    fun cancelAdd() {
        _showAdd.value = false
    }

    fun switchMaster(id: Long) {
        if (id == _activeMasterId.value) return
        _activeMasterId.value = id
        search.value = ""
        input.value = ""
        viewModelScope.launch { dao.touchMaster(id, System.currentTimeMillis()) }
    }

    fun deleteMaster(id: Long) {
        viewModelScope.launch { dao.deleteMaster(id) }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { dao.deleteEntry(id) }
    }

    /** 主流程生成：返回密码供 UI 复制后退出，同时记录/累加该条目 */
    fun generate(rawText: String): String? {
        val text = rawText.trim()
        if (text.isEmpty()) return null
        val master = currentMaster() ?: return null
        val pwd = PwCalc.calcPwd(master.second, text)
        viewModelScope.launch { record(master.first, text) }
        return pwd
    }

    /** 列表复制：对该条目重新生成密码并累加复制次数 */
    fun regenerate(entry: EntryUi): String? {
        val master = currentMaster() ?: return null
        val pwd = PwCalc.calcPwd(master.second, entry.text)
        viewModelScope.launch { dao.bumpEntry(entry.id, System.currentTimeMillis()) }
        return pwd
    }

    private suspend fun record(masterId: Long, text: String) {
        val now = System.currentTimeMillis()
        val exist = entriesUi.value.firstOrNull { it.text == text }
        if (exist != null) {
            dao.bumpEntry(exist.id, now)
        } else {
            dao.insertEntry(
                Entry(
                    masterId = masterId,
                    textEnc = VaultCipher.encrypt(text),
                    copyCount = 1,
                    updatedAt = now
                )
            )
        }
    }

    private fun currentMaster(): Pair<Long, String>? {
        val id = _activeMasterId.value ?: return null
        val m = mastersState.value.firstOrNull { it.id == id } ?: return null
        val pwd = runCatching { VaultCipher.decrypt(m.pwdEnc) }.getOrNull() ?: return null
        return id to pwd
    }

    private fun mask(pwd: String): String =
        if (pwd.length <= 4) "****" else pwd.take(2) + "****" + pwd.takeLast(2)
}
