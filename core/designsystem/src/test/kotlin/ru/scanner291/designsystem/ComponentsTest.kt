package ru.scanner291.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun bottomNavHasFourLabelledTabsAndSelects() {
        var selected by mutableStateOf(Destination.Home)
        rule.setContent { ScannerTheme { ScanBottomNav(selected, { selected = it }) } }
        listOf("Главная", "Документы", "Поиск", "Настройки").forEach { rule.onNodeWithText(it).assertExists() }
        rule.onNodeWithText("Поиск").performClick()
        assertEquals(Destination.Search, selected)
    }

    @Test fun fioToggleIsCheckboxAndWholeRowToggles() {
        var checked by mutableStateOf(true)
        rule.setContent { ScannerTheme { FioToggle(checked, { checked = it }) } }
        val row = rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        row.assertIsOn()
        row.assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Назвать файл по ФИО").performClick()
        row.assertIsOff()
        assertEquals(false, checked)
    }

    @Test fun touchTargetsAreAtLeast48dp() {
        rule.setContent {
            ScannerTheme {
                androidx.compose.foundation.layout.Column {
                    ScanButton("Все ›", {}, variant = ButtonVariant.Ghost)
                    ScanChip("Паспорт", selected = false, onClick = {})
                    ScanButton("Сохранить", {})
                }
            }
        }
        rule.onNodeWithText("Все ›").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Паспорт").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Сохранить").assertHeightIsAtLeast(56.dp)
    }

    @Test fun folderRowDeclinesCount() {
        rule.setContent { ScannerTheme { FolderRow("Договоры", 12, {}, {}) } }
        rule.onNodeWithText("12 документов").assertExists()
    }

    @Test fun segmentedSelects() {
        var sel by mutableStateOf("PDF")
        rule.setContent { ScannerTheme { ScanSegmented(listOf("PDF", "JPG", "PNG"), sel, { it }, { sel = it }) } }
        rule.onNodeWithText("JPG").performClick()
        assertEquals("JPG", sel)
    }

    @Test fun fioCardShowsStatusAndKeepsFileNameEditable() {
        rule.setContent {
            ScannerTheme {
                FioCard(
                    enabled = true, useFio = true, fio = FioUi.NotFound, fileName = "Скан 2026-10-23", extension = "pdf",
                    onToggle = {}, onUseFioChange = {}, onPickCandidate = {}, onFileNameChange = {},
                )
            }
        }
        rule.onNodeWithText("ФИО не найдено").assertExists()
        rule.onNodeWithText(".pdf").assertExists()
        rule.onNodeWithText("Выбрать другое ФИО").assertDoesNotExist()
    }

    @Test fun galleryRendersInBothThemes() {
        var mode by mutableStateOf(ThemeMode.DARK)
        rule.setContent { ScannerTheme(mode) { ComponentGallery() } }
        rule.onNodeWithText("291 Сканер").assertExists()
        mode = ThemeMode.LIGHT
        rule.waitForIdle()
        rule.onNodeWithText("Сохранить все (5)").assertExists()
    }
}
