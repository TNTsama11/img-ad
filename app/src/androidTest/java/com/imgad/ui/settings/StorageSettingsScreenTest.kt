package com.imgad.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.imgad.domain.port.ArchiveImportPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StorageSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun archiveActionsForwardOptionsAndRequireImportConfirmation() {
        var exportOptions: Pair<Boolean, String?>? = null
        var importPassword: String? = null
        var confirmed = 0
        val preview = mutableStateOf<ArchiveImportPreview?>(ArchiveImportPreview(1, 2, 3))
        composeRule.setContent {
            StorageSettingsScreen(
                usage = StorageUsage(12L, 2),
                actions = StorageSettingsActions(
                    onExport = { includeSecrets, password -> exportOptions = includeSecrets to password },
                    onImport = { importPassword = it },
                    onConfirmImport = {
                        confirmed++
                        preview.value = null
                    },
                ),
                importPreview = preview.value,
            )
        }

        composeRule.onNodeWithText("供应方 1，会话 2，资源 3").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-import").performClick()
        assertEquals(1, confirmed)

        composeRule.onNodeWithText("导出").performClick()
        composeRule.onNodeWithText("选择导出位置").performClick()
        assertEquals(false to null, exportOptions)

        composeRule.onNodeWithText("导入").performClick()
        composeRule.onNodeWithText("密码（如需要）").performTextInput("secret")
        composeRule.onNodeWithText("选择归档").performClick()
        assertEquals("secret", importPassword)
    }

    @Test
    fun includingSecretsRequiresPasswordAndPreviewCanBeCancelled() {
        var cancelled = 0
        val preview = mutableStateOf<ArchiveImportPreview?>(ArchiveImportPreview(1, 0, 0))
        composeRule.setContent {
            StorageSettingsScreen(
                usage = StorageUsage(0L, 0),
                actions = StorageSettingsActions(
                    onCancelImport = {
                        cancelled++
                        preview.value = null
                    },
                ),
                importPreview = preview.value,
            )
        }

        composeRule.onNodeWithTag("cancel-import").performClick()
        assertEquals(1, cancelled)
        composeRule.onNodeWithText("导出").performClick()
        composeRule.onNodeWithTag("include-secrets").performClick()
        composeRule.onNodeWithText("选择导出位置").assertIsNotEnabled()
    }
}
