package com.huangder.lumibooks.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import fluent.ui.system.icons.FluentIcons
import fluent.ui.system.icons.filled.*
import fluent.ui.system.icons.regular.*

/** A semantic pair used when an icon has a selected and an unselected state. */
data class IconPair(
    val regular: ImageVector,
    val filled: ImageVector
) {
    fun resolve(selected: Boolean): ImageVector = if (selected) filled else regular
}

/** Application-level icon vocabulary. Keep third-party icon names out of screens. */
object AppIcons {
    val Plus = FluentIcons.Regular.Add
    val PlusFilled = FluentIcons.Filled.Add
    val ArrowLeft = FluentIcons.Regular.ArrowLeft
    val ArrowRight = FluentIcons.Regular.ArrowRight
    val CaretLeft = FluentIcons.Regular.ChevronLeft
    val CaretRight = FluentIcons.Regular.ChevronRight
    val CaretUp = FluentIcons.Regular.ChevronUp
    val CaretDown = FluentIcons.Regular.ChevronDown
    val AlignTop = FluentIcons.Regular.AlignTop
    val AlignBottom = FluentIcons.Regular.AlignBottom

    val House = IconPair(FluentIcons.Regular.Home, FluentIcons.Filled.Home)
    val BooksPair = IconPair(FluentIcons.Regular.Library, FluentIcons.Filled.Library)
    val ChartBarPair = IconPair(FluentIcons.Regular.DataBarVertical, FluentIcons.Filled.DataBarVertical)
    // Bottom tabs intentionally keep the filled Fluent silhouette in both states.
    val HomeTab = IconPair(FluentIcons.Filled.Home, FluentIcons.Filled.Home)
    val BookshelfTab = IconPair(FluentIcons.Filled.Library, FluentIcons.Filled.Library)
    val StatisticsTab = IconPair(FluentIcons.Filled.DataBarVertical, FluentIcons.Filled.DataBarVertical)
    val UserCircle = FluentIcons.Regular.PersonCircle
    val Book = FluentIcons.Regular.Book
    val BookOpen = FluentIcons.Regular.BookOpen
    val BookOpenPair = IconPair(FluentIcons.Regular.BookOpen, FluentIcons.Filled.BookOpen)
    val BookOpenText = FluentIcons.Regular.BookOpen
    val Books = FluentIcons.Regular.Library
    val File = FluentIcons.Regular.Document
    val FileText = FluentIcons.Regular.DocumentText
    val Folder = FluentIcons.Regular.Folder
    val FolderOpen = FluentIcons.Regular.FolderOpen
    val FolderPlus = FluentIcons.Regular.FolderAdd
    val FolderSimple = FluentIcons.Regular.Folder
    val Image = FluentIcons.Regular.Image
    val ImageSquare = FluentIcons.Regular.Image
    val ImageBroken = FluentIcons.Regular.ImageOff
    val Tag = FluentIcons.Regular.Tag
    val Link = FluentIcons.Regular.Link
    val Lock = FluentIcons.Regular.LockClosed
    val LockOpen = FluentIcons.Regular.LockOpen

    val X = FluentIcons.Regular.Dismiss
    val XFilled = FluentIcons.Filled.Dismiss
    val Check = FluentIcons.Regular.Checkmark
    val CheckFilled = FluentIcons.Filled.Checkmark
    val CheckCircle = FluentIcons.Filled.CheckmarkCircle
    val PencilSimple = FluentIcons.Regular.Edit
    val Trash = FluentIcons.Regular.Delete
    val TrashSimple = FluentIcons.Regular.Delete
    val Heart = IconPair(FluentIcons.Regular.Heart, FluentIcons.Filled.Heart)
    val Bookmark = IconPair(FluentIcons.Regular.Bookmark, FluentIcons.Filled.Bookmark)
    val PushPin = FluentIcons.Regular.Pin
    val MagnifyingGlass = FluentIcons.Regular.Search
    val Gear = FluentIcons.Regular.Settings
    val Info = FluentIcons.Regular.Info
    val ArrowCounterClockwise = FluentIcons.Regular.ArrowCounterclockwise
    val ArrowClockwise = FluentIcons.Regular.ArrowClockwise
    val SortAscending = FluentIcons.Regular.TextSortAscending
    val DotsThreeVertical = FluentIcons.Regular.MoreVertical
    val DotsSixVertical = FluentIcons.Regular.ReOrderDotsVertical
    val ArrowSquareOut = FluentIcons.Regular.Open
    val ShareNetwork = FluentIcons.Regular.Share
    val ArrowsClockwise = FluentIcons.Regular.ArrowSync
    val ArrowsLeftRight = FluentIcons.Regular.ArrowBidirectionalLeftRight
    val ArrowsLeftRightPair = IconPair(
        FluentIcons.Regular.ArrowBidirectionalLeftRight,
        FluentIcons.Filled.ArrowBidirectionalLeftRight
    )
    val ArrowsDownUp = FluentIcons.Regular.ArrowBidirectionalUpDown
    val ArrowsDownUpPair = IconPair(FluentIcons.Regular.ArrowBidirectionalUpDown, FluentIcons.Filled.ArrowBidirectionalUpDown)

    val DownloadSimple = FluentIcons.Regular.ArrowDownload
    val UploadSimple = FluentIcons.Regular.ArrowUpload
    val Cloud = FluentIcons.Regular.Cloud
    val CloudFilled = FluentIcons.Filled.Cloud
    val CloudArrowDown = FluentIcons.Regular.CloudArrowDown
    val CloudArrowUp = FluentIcons.Regular.CloudArrowUp
    val CloudSlash = FluentIcons.Regular.CloudOff
    val ArrowCircleDown = FluentIcons.Regular.ArrowCircleDown

    val List = FluentIcons.Regular.List
    val ListBullets = FluentIcons.Regular.TextBulletList
    val SquaresFour = FluentIcons.Regular.Grid
    val Rows = FluentIcons.Regular.LayoutRowThree
    val RowsPair = IconPair(FluentIcons.Regular.LayoutRowThree, FluentIcons.Filled.LayoutRowThree)
    val Cards = FluentIcons.Regular.CardUi
    val ChartBar = FluentIcons.Regular.DataBarVertical
    val Play = FluentIcons.Filled.Play
    val Pause = FluentIcons.Filled.Pause
    val Stop = FluentIcons.Filled.Stop
    val SkipBack = FluentIcons.Filled.Previous
    val SkipForward = FluentIcons.Filled.Next
    val PlayFilled = FluentIcons.Filled.Play
    val PauseFilled = FluentIcons.Filled.Pause
    val SkipBackFilled = FluentIcons.Filled.Previous
    val SkipForwardFilled = FluentIcons.Filled.Next
    val Headphones = FluentIcons.Regular.Headphones
    val PaintBrush = FluentIcons.Regular.PaintBrush
    val TextAa = FluentIcons.Regular.TextFont
    val TextB = FluentIcons.Regular.TextBold
    val TextT = FluentIcons.Regular.TextT
    val LineSegments = FluentIcons.Regular.TextLineSpacing
    val FrameCorners = FluentIcons.Regular.BorderOutside
    val Ruler = FluentIcons.Regular.Ruler
    val CornersOut = FluentIcons.Regular.ArrowMaximize
    val Drop = FluentIcons.Regular.Drop
    val Sun = FluentIcons.Regular.WeatherSunny
    val SunPair = IconPair(FluentIcons.Regular.WeatherSunny, FluentIcons.Filled.WeatherSunny)
    val SunDim = FluentIcons.Regular.WeatherSunnyLow
    val Moon = FluentIcons.Regular.WeatherMoon
    val MoonPair = IconPair(FluentIcons.Regular.WeatherMoon, FluentIcons.Filled.WeatherMoon)
    val MoonStars = FluentIcons.Regular.WeatherMoon
    val FilmStrip = FluentIcons.Regular.Filmstrip
    val FilmStripPair = IconPair(FluentIcons.Regular.Filmstrip, FluentIcons.Filled.Filmstrip)
    val Lightning = FluentIcons.Regular.Flash
    val LightningPair = IconPair(FluentIcons.Regular.Flash, FluentIcons.Filled.Flash)
    val HandSwipeRight = FluentIcons.Regular.SwipeRight
    val SpeakerHigh = FluentIcons.Regular.Speaker2
    val DeviceMobile = FluentIcons.Regular.Phone

    val Pulse = FluentIcons.Regular.Pulse
    val Speedometer = FluentIcons.Regular.Gauge
    val Microphone = FluentIcons.Regular.Mic
    val MicrophonePair = IconPair(FluentIcons.Regular.Mic, FluentIcons.Filled.Mic)
    val MicrophoneStage = FluentIcons.Regular.MicSparkle
    val Subtitles = FluentIcons.Regular.Subtitles
    val Shield = FluentIcons.Regular.Shield
    val ShieldCheck = FluentIcons.Filled.ShieldCheckmark
    val Scroll = FluentIcons.Regular.DocumentText
    val Globe = FluentIcons.Regular.Globe
    val Code = FluentIcons.Regular.Code
    val UsersThree = FluentIcons.Regular.People
    val ChatCircle = FluentIcons.Regular.Chat
    val Warning = FluentIcons.Filled.Warning
    val Bug = FluentIcons.Regular.Bug
    val Archive = FluentIcons.Regular.Archive
    val Key = FluentIcons.Regular.Key
    val KeyPair = IconPair(FluentIcons.Regular.Key, FluentIcons.Filled.Key)
    val Palette = FluentIcons.Regular.Color
    val PalettePair = IconPair(FluentIcons.Regular.Color, FluentIcons.Filled.Color)
    val TextAaPair = IconPair(FluentIcons.Regular.TextFont, FluentIcons.Filled.TextFont)
    val Timer = FluentIcons.Regular.Timer
    val Translate = FluentIcons.Regular.Translate
    val Eye = FluentIcons.Regular.Eye
    val EyeSlash = FluentIcons.Regular.EyeOff
}

/** Resolve a logical start/end icon without relying on Material's auto-mirror metadata. */
@Composable
fun directionalIcon(ltr: ImageVector, rtl: ImageVector): ImageVector =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) rtl else ltr
