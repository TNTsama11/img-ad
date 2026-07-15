package com.imgad.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imgad.domain.model.Session

data class HistoryScreenActions(
    val onOpenSession: (String) -> Unit = {},
)

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, actions: HistoryScreenActions = HistoryScreenActions()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state, actions, viewModel::updateQuery, viewModel::requestRename, viewModel::requestDelete)
    state.pendingDeleteId?.let { id ->
        DeleteSessionDialog(
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
    state.pendingRenameId?.let { id ->
        RenameSessionDialog(
            initialTitle = state.sessions.firstOrNull { it.id == id }?.title.orEmpty(),
            onConfirm = { title -> viewModel.rename(id, title) },
            onDismiss = viewModel::cancelRename,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    actions: HistoryScreenActions,
    onQueryChanged: (String) -> Unit = {},
    onRenameRequested: (String) -> Unit = {},
    onDeleteRequested: (String) -> Unit = {},
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("历史") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索标题") },
                singleLine = true,
            )
            state.errorMessage?.let { Text(it) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.sessions, key = Session::id) { session ->
                    SessionRow(session, actions, onRenameRequested, onDeleteRequested)
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: Session,
    actions: HistoryScreenActions,
    onRenameRequested: (String) -> Unit,
    onDeleteRequested: (String) -> Unit,
) {
    var expanded by remember(session.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { actions.onOpenSession(session.id) }) {
                Text(session.title.ifBlank { "未命名会话" })
            }
            Column {
                TextButton(onClick = { expanded = true }) { Text("更多") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            expanded = false
                            onRenameRequested(session.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            expanded = false
                            onDeleteRequested(session.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteSessionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除会话？") },
        text = { Text("将删除应用私有图片，但不会删除系统图库副本。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RenameSessionDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名会话") },
        text = {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }, enabled = title.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
