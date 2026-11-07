package com.huangder.lumibooks.ui.icons

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.huangder.lumibooks.domain.model.AppUiIconStyle
import fluent.ui.system.icons.FluentIcons
import fluent.ui.system.icons.filled.*
import fluent.ui.system.icons.regular.*

val LocalAppUiIconStyle = staticCompositionLocalOf { AppUiIconStyle.DEFAULT }

/** A semantic pair used when an icon has a selected and an unselected state. */
data class IconPair(
    val regular: ImageVector,
    val filled: ImageVector
) {
    fun resolve(selected: Boolean): ImageVector = if (selected) filled else regular
}

/** Application-level icon vocabulary. Keep third-party icon names out of screens. */
object AppIcons {
    // Identity registration keeps filled glyphs and unrelated vectors out of control styling.
    private val outlineIcons = java.util.Collections.newSetFromMap(
        java.util.IdentityHashMap<ImageVector, Boolean>()
    )

    private fun outline(icon: ImageVector): ImageVector = icon.also { outlineIcons.add(it) }

    internal fun isOutline(icon: ImageVector): Boolean = outlineIcons.contains(icon)

    private val styleVariants: List<IconPair> = listOf(
        IconPair(outline(FluentIcons.Regular.FolderOpen), FluentIcons.Filled.FolderOpen),
        IconPair(outline(FluentIcons.Regular.Tag), FluentIcons.Filled.Tag),
        IconPair(outline(FluentIcons.Regular.TextFont), FluentIcons.Filled.TextFont),
        IconPair(outline(FluentIcons.Regular.WeatherSunny), FluentIcons.Filled.WeatherSunny),
        IconPair(outline(FluentIcons.Regular.Translate), FluentIcons.Filled.Translate),
        IconPair(outline(FluentIcons.Regular.Shield), FluentIcons.Filled.ShieldLock),
        IconPair(outline(FluentIcons.Regular.Storage), FluentIcons.Filled.Storage),
        IconPair(outline(FluentIcons.Regular.Archive), FluentIcons.Filled.Archive),
        IconPair(outline(FluentIcons.Regular.Cloud), FluentIcons.Filled.Cloud),
        IconPair(outline(FluentIcons.Regular.Info), FluentIcons.Filled.Info),
        IconPair(outline(FluentIcons.Regular.Heart), FluentIcons.Filled.Heart),
        IconPair(outline(FluentIcons.Regular.Bug), FluentIcons.Filled.Bug),
        IconPair(outline(FluentIcons.Regular.Timer), FluentIcons.Filled.Timer),
        IconPair(outline(FluentIcons.Regular.DataBarVertical), FluentIcons.Filled.DataBarVertical),
        IconPair(outline(FluentIcons.Regular.Library), FluentIcons.Filled.Library),
        IconPair(outline(FluentIcons.Regular.Flash), FluentIcons.Filled.Flash),
        IconPair(outline(FluentIcons.Regular.CheckmarkCircle), FluentIcons.Filled.CheckmarkCircle),
        IconPair(outline(FluentIcons.Regular.Headphones), FluentIcons.Filled.Headphones),
        IconPair(outline(FluentIcons.Regular.Edit), FluentIcons.Filled.Edit),
        IconPair(outline(FluentIcons.Regular.Pulse), FluentIcons.Filled.Pulse),
        IconPair(outline(FluentIcons.Regular.BookOpen), FluentIcons.Filled.BookOpen)
    )
    private val styleVariantsByIcon: Map<ImageVector, IconPair> = buildMap {
        styleVariants.forEach { pair ->
            put(pair.regular, pair)
            put(pair.filled, pair)
        }
    }

    fun styled(icon: ImageVector, style: AppUiIconStyle): ImageVector {
        if (style == AppUiIconStyle.FILLED_COLORFUL) return icon
        return styleVariantsByIcon[icon]?.resolve(style.usesFilledGlyph) ?: icon
    }

    val Plus = outline(FluentIcons.Regular.Add)
    val PlusFilled = FluentIcons.Filled.Add
    val ArrowLeft = outline(FluentIcons.Regular.ArrowLeft)
    val ArrowRight = outline(FluentIcons.Regular.ArrowRight)
    val CaretLeft = outline(FluentIcons.Regular.ChevronLeft)
    val CaretRight = outline(FluentIcons.Regular.ChevronRight)
    val CaretUp = outline(FluentIcons.Regular.ChevronUp)
    val CaretDown = outline(FluentIcons.Regular.ChevronDown)
    val AlignTop = outline(FluentIcons.Regular.AlignTop)
    val AlignBottom = outline(FluentIcons.Regular.AlignBottom)

    val House = IconPair(outline(FluentIcons.Regular.Home), FluentIcons.Filled.Home)
    val BooksPair = IconPair(outline(FluentIcons.Regular.Library), FluentIcons.Filled.Library)
    val ChartBarPair = IconPair(outline(FluentIcons.Regular.DataBarVertical), FluentIcons.Filled.DataBarVertical)
    // Bottom tabs intentionally keep the filled Fluent silhouette in both states.
    val HomeTab = IconPair(FluentIcons.Filled.Home, FluentIcons.Filled.Home)
    val BookshelfTab = IconPair(FluentIcons.Filled.Library, FluentIcons.Filled.Library)
    val StatisticsTab = IconPair(FluentIcons.Filled.DataBarVertical, FluentIcons.Filled.DataBarVertical)
    val UserCircle = outline(FluentIcons.Regular.PersonCircle)
    val Book = outline(FluentIcons.Regular.Book)
    val BookOpen = outline(FluentIcons.Regular.BookOpen)
    val BookOpenPair = IconPair(outline(FluentIcons.Regular.BookOpen), FluentIcons.Filled.BookOpen)
    val BookOpenText = outline(FluentIcons.Regular.BookOpen)
    val Books = outline(FluentIcons.Regular.Library)
    val File = outline(FluentIcons.Regular.Document)
    val FileText = outline(FluentIcons.Regular.DocumentText)
    val Folder = outline(FluentIcons.Regular.Folder)
    val FolderOpen = outline(FluentIcons.Regular.FolderOpen)
    val FolderPlus = outline(FluentIcons.Regular.FolderAdd)
    val FolderSimple = outline(FluentIcons.Regular.Folder)
    val Image = outline(FluentIcons.Regular.Image)
    val ImageSquare = outline(FluentIcons.Regular.Image)
    val ImageBroken = outline(FluentIcons.Regular.ImageOff)
    val Tag = outline(FluentIcons.Regular.Tag)
    val Link = outline(FluentIcons.Regular.Link)
    val Lock = outline(FluentIcons.Regular.LockClosed)
    val LockOpen = outline(FluentIcons.Regular.LockOpen)

    val X = outline(FluentIcons.Regular.Dismiss)
    val XFilled = FluentIcons.Filled.Dismiss
    val Check = outline(FluentIcons.Regular.Checkmark)
    val CheckFilled = FluentIcons.Filled.Checkmark
    val CheckCircle = FluentIcons.Filled.CheckmarkCircle
    val PencilSimple = outline(FluentIcons.Regular.Edit)
    val Trash = outline(FluentIcons.Regular.Delete)
    val TrashSimple = outline(FluentIcons.Regular.Delete)
    val Heart = IconPair(outline(FluentIcons.Regular.Heart), FluentIcons.Filled.Heart)
    val Bookmark = IconPair(outline(FluentIcons.Regular.Bookmark), FluentIcons.Filled.Bookmark)
    val PushPin = outline(FluentIcons.Regular.Pin)
    val MagnifyingGlass = outline(FluentIcons.Regular.Search)
    val Gear = outline(FluentIcons.Regular.Settings)
    val Info = outline(FluentIcons.Regular.Info)
    val ArrowCounterClockwise = outline(FluentIcons.Regular.ArrowCounterclockwise)
    val ArrowClockwise = outline(FluentIcons.Regular.ArrowClockwise)
    val SortAscending = outline(FluentIcons.Regular.TextSortAscending)
    val DotsThreeVertical = outline(FluentIcons.Regular.MoreVertical)
    val DotsSixVertical = outline(FluentIcons.Regular.ReOrderDotsVertical)
    val ArrowSquareOut = outline(FluentIcons.Regular.Open)
    val ShareNetwork = outline(FluentIcons.Regular.Share)
    val ArrowsClockwise = outline(FluentIcons.Regular.ArrowSync)
    val ArrowsLeftRight = outline(FluentIcons.Regular.ArrowBidirectionalLeftRight)
    val ArrowsLeftRightPair = IconPair(
        outline(FluentIcons.Regular.ArrowBidirectionalLeftRight),
        FluentIcons.Filled.ArrowBidirectionalLeftRight
    )
    val ArrowsDownUp = outline(FluentIcons.Regular.ArrowBidirectionalUpDown)
    val ArrowsDownUpPair = IconPair(outline(FluentIcons.Regular.ArrowBidirectionalUpDown), FluentIcons.Filled.ArrowBidirectionalUpDown)

    val DownloadSimple = outline(FluentIcons.Regular.ArrowDownload)
    val UploadSimple = outline(FluentIcons.Regular.ArrowUpload)
    val Cloud = outline(FluentIcons.Regular.Cloud)
    val CloudFilled = FluentIcons.Filled.Cloud
    val CloudArrowDown = outline(FluentIcons.Regular.CloudArrowDown)
    val CloudArrowUp = outline(FluentIcons.Regular.CloudArrowUp)
    val CloudSlash = outline(FluentIcons.Regular.CloudOff)
    val ArrowCircleDown = outline(FluentIcons.Regular.ArrowCircleDown)

    val List = outline(FluentIcons.Regular.List)
    val ListBullets = outline(FluentIcons.Regular.TextBulletList)
    val SquaresFour = outline(FluentIcons.Regular.Grid)
    val Rows = outline(FluentIcons.Regular.LayoutRowThree)
    val RowsPair = IconPair(outline(FluentIcons.Regular.LayoutRowThree), FluentIcons.Filled.LayoutRowThree)
    val Cards = outline(FluentIcons.Regular.CardUi)
    val ChartBar = outline(FluentIcons.Regular.DataBarVertical)
    val Play = FluentIcons.Filled.Play
    val Pause = FluentIcons.Filled.Pause
    val Stop = FluentIcons.Filled.Stop
    val SkipBack = FluentIcons.Filled.Previous
    val SkipForward = FluentIcons.Filled.Next
    val PlayFilled = FluentIcons.Filled.Play
    val PauseFilled = FluentIcons.Filled.Pause
    val SkipBackFilled = FluentIcons.Filled.Previous
    val SkipForwardFilled = FluentIcons.Filled.Next
    val Headphones = outline(FluentIcons.Regular.Headphones)
    val PaintBrush = outline(FluentIcons.Regular.PaintBrush)
    val TextAa = outline(FluentIcons.Regular.TextFont)
    val TextB = outline(FluentIcons.Regular.TextBold)
    val TextT = outline(FluentIcons.Regular.TextT)
    val LineSegments = outline(FluentIcons.Regular.TextLineSpacing)
    val FrameCorners = outline(FluentIcons.Regular.BorderOutside)
    val Ruler = outline(FluentIcons.Regular.Ruler)
    val CornersOut = outline(FluentIcons.Regular.ArrowMaximize)
    val Drop = outline(FluentIcons.Regular.Drop)
    val Sun = outline(FluentIcons.Regular.WeatherSunny)
    val SunPair = IconPair(outline(FluentIcons.Regular.WeatherSunny), FluentIcons.Filled.WeatherSunny)
    val SunDim = outline(FluentIcons.Regular.WeatherSunnyLow)
    val Moon = outline(FluentIcons.Regular.WeatherMoon)
    val MoonPair = IconPair(outline(FluentIcons.Regular.WeatherMoon), FluentIcons.Filled.WeatherMoon)
    val MoonStars = outline(FluentIcons.Regular.WeatherMoon)
    val FilmStrip = outline(FluentIcons.Regular.Filmstrip)
    val FilmStripPair = IconPair(outline(FluentIcons.Regular.Filmstrip), FluentIcons.Filled.Filmstrip)
    val Lightning = outline(FluentIcons.Regular.Flash)
    val LightningPair = IconPair(outline(FluentIcons.Regular.Flash), FluentIcons.Filled.Flash)
    val HandSwipeRight = outline(FluentIcons.Regular.SwipeRight)
    val SpeakerHigh = outline(FluentIcons.Regular.Speaker2)
    val DeviceMobile = outline(FluentIcons.Regular.Phone)

    val Pulse = outline(FluentIcons.Regular.Pulse)
    val Speedometer = outline(FluentIcons.Regular.Gauge)
    val Microphone = outline(FluentIcons.Regular.Mic)
    val MicrophonePair = IconPair(outline(FluentIcons.Regular.Mic), FluentIcons.Filled.Mic)
    val MicrophoneStage = outline(FluentIcons.Regular.MicSparkle)
    val Subtitles = outline(FluentIcons.Regular.Subtitles)
    val Shield = outline(FluentIcons.Regular.Shield)
    val ShieldCheck = FluentIcons.Filled.ShieldCheckmark
    val Scroll = outline(FluentIcons.Regular.DocumentText)
    val Globe = outline(FluentIcons.Regular.Globe)
    val Code = outline(FluentIcons.Regular.Code)
    val UsersThree = outline(FluentIcons.Regular.People)
    val ChatCircle = outline(FluentIcons.Regular.Chat)
    val Warning = FluentIcons.Filled.Warning
    val Bug = outline(FluentIcons.Regular.Bug)
    val Archive = outline(FluentIcons.Regular.Archive)
    val Key = outline(FluentIcons.Regular.Key)
    val KeyPair = IconPair(outline(FluentIcons.Regular.Key), FluentIcons.Filled.Key)
    val Palette = outline(FluentIcons.Regular.Color)
    val PalettePair = IconPair(outline(FluentIcons.Regular.Color), FluentIcons.Filled.Color)
    val TextAaPair = IconPair(outline(FluentIcons.Regular.TextFont), FluentIcons.Filled.TextFont)
    val Timer = outline(FluentIcons.Regular.Timer)
    val Translate = outline(FluentIcons.Regular.Translate)
    val Eye = outline(FluentIcons.Regular.Eye)
    val EyeSlash = outline(FluentIcons.Regular.EyeOff)

    /** Filled glyphs drawn in white on the colored tiles of the settings home page. */
    object Tile {
        val Folders = FluentIcons.Filled.FolderOpen
        val Tags = FluentIcons.Filled.Tag
        val Reading = FluentIcons.Filled.TextFont
        val Display = FluentIcons.Filled.WeatherSunny
        val Language = FluentIcons.Filled.Translate
        val Privacy = FluentIcons.Filled.ShieldLock
        val Storage = FluentIcons.Filled.Storage
        val Backup = FluentIcons.Filled.Archive
        val Services = FluentIcons.Filled.Cloud
        val About = FluentIcons.Filled.Info
        val Sponsor = FluentIcons.Filled.Heart
        val Feedback = FluentIcons.Filled.Bug
    }
}

/** Resolve a logical start/end icon without relying on Material's auto-mirror metadata. */
@Composable
fun directionalIcon(ltr: ImageVector, rtl: ImageVector): ImageVector =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) rtl else ltr
