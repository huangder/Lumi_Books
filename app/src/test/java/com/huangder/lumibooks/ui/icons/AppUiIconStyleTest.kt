package com.huangder.lumibooks.ui.icons

import com.huangder.lumibooks.domain.model.AppUiIconStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppUiIconStyleTest {
    @Test
    fun unknownStoredValuesFallBackToFilledColorful() {
        assertEquals(AppUiIconStyle.DEFAULT, AppUiIconStyle.fromStoredValue(null))
        assertEquals(AppUiIconStyle.DEFAULT, AppUiIconStyle.fromStoredValue("unknown"))
        assertEquals(AppUiIconStyle.DEFAULT.storedValue, AppUiIconStyle.normalize("unknown"))
    }

    @Test
    fun settingsAndStatisticsIconsResolveTheirFilledAndOutlineVariants() {
        assertEquals(
            AppIcons.FolderOpen,
            AppIcons.styled(AppIcons.Tile.Folders, AppUiIconStyle.OUTLINE_COLORFUL)
        )
        assertEquals(
            AppIcons.Tile.Folders,
            AppIcons.styled(AppIcons.FolderOpen, AppUiIconStyle.FILLED_MONOCHROME)
        )
        assertEquals(
            AppIcons.ChartBar,
            AppIcons.styled(AppIcons.ChartBar, AppUiIconStyle.OUTLINE_MONOCHROME)
        )
        assertNotEquals(
            AppIcons.styled(AppIcons.Timer, AppUiIconStyle.FILLED_MONOCHROME),
            AppIcons.styled(AppIcons.Timer, AppUiIconStyle.OUTLINE_COLORFUL)
        )
    }
}
