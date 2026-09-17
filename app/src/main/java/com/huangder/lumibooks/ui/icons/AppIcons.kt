package com.huangder.lumibooks.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.*
import com.adamglin.phosphoricons.regular.*

/** A semantic pair used when an icon has a selected and an unselected state. */
data class IconPair(
    val regular: ImageVector,
    val filled: ImageVector
) {
    fun resolve(selected: Boolean): ImageVector = if (selected) filled else regular
}

/** Application-level icon vocabulary. Keep third-party icon names out of screens. */
object AppIcons {
    val Plus = PhosphorIcons.Regular.Plus
    val PlusFilled = PhosphorIcons.Fill.Plus
    val ArrowLeft = PhosphorIcons.Regular.ArrowLeft
    val ArrowRight = PhosphorIcons.Regular.ArrowRight
    val CaretLeft = PhosphorIcons.Regular.CaretLeft
    val CaretRight = PhosphorIcons.Regular.CaretRight
    val CaretUp = PhosphorIcons.Regular.CaretUp
    val CaretDown = PhosphorIcons.Regular.CaretDown
    val AlignTop = PhosphorIcons.Regular.AlignTop
    val AlignBottom = PhosphorIcons.Regular.AlignBottom

    val House = IconPair(PhosphorIcons.Regular.House, PhosphorIcons.Fill.House)
    val BooksPair = IconPair(PhosphorIcons.Regular.Books, PhosphorIcons.Fill.Books)
    val ChartBarPair = IconPair(PhosphorIcons.Regular.ChartBar, PhosphorIcons.Fill.ChartBar)
    // Bottom navigation keeps a solid, rounded silhouette in both states.
    val HomeTab = IconPair(PhosphorIcons.Fill.House, PhosphorIcons.Fill.House)
    val BookshelfTab = IconPair(PhosphorIcons.Fill.Books, PhosphorIcons.Fill.Books)
    val StatisticsTab = IconPair(PhosphorIcons.Fill.ChartBar, PhosphorIcons.Fill.ChartBar)
    val UserCircle = PhosphorIcons.Regular.UserCircle
    val Book = PhosphorIcons.Regular.Book
    val BookOpen = PhosphorIcons.Regular.BookOpen
    val BookOpenPair = IconPair(PhosphorIcons.Regular.BookOpen, PhosphorIcons.Fill.BookOpen)
    val BookOpenText = PhosphorIcons.Regular.BookOpenText
    val Books = PhosphorIcons.Regular.Books
    val File = PhosphorIcons.Regular.File
    val FileText = PhosphorIcons.Regular.FileText
    val Folder = PhosphorIcons.Regular.Folder
    val FolderOpen = PhosphorIcons.Regular.FolderOpen
    val FolderPlus = PhosphorIcons.Regular.FolderPlus
    val FolderSimple = PhosphorIcons.Regular.FolderSimple
    val Image = PhosphorIcons.Regular.Image
    val ImageSquare = PhosphorIcons.Regular.ImageSquare
    val ImageBroken = PhosphorIcons.Regular.ImageBroken
    val Tag = PhosphorIcons.Regular.Tag
    val Link = PhosphorIcons.Regular.Link

    val X = PhosphorIcons.Regular.X
    val XFilled = PhosphorIcons.Fill.X
    val Check = PhosphorIcons.Regular.Check
    val CheckFilled = PhosphorIcons.Fill.Check
    val CheckCircle = PhosphorIcons.Regular.CheckCircle
    val PencilSimple = PhosphorIcons.Regular.PencilSimple
    val Trash = PhosphorIcons.Regular.Trash
    val TrashSimple = PhosphorIcons.Regular.TrashSimple
    val Heart = IconPair(PhosphorIcons.Regular.Heart, PhosphorIcons.Fill.Heart)
    val Bookmark = IconPair(PhosphorIcons.Regular.Bookmark, PhosphorIcons.Fill.Bookmark)
    val MagnifyingGlass = PhosphorIcons.Regular.MagnifyingGlass
    val Gear = PhosphorIcons.Regular.Gear
    val Info = PhosphorIcons.Regular.Info
    val ArrowCounterClockwise = PhosphorIcons.Regular.ArrowCounterClockwise
    val ArrowClockwise = PhosphorIcons.Regular.ArrowClockwise
    val SortAscending = PhosphorIcons.Regular.SortAscending
    val DotsThreeVertical = PhosphorIcons.Regular.DotsThreeVertical
    val DotsSixVertical = PhosphorIcons.Regular.DotsSixVertical
    val ArrowSquareOut = PhosphorIcons.Regular.ArrowSquareOut
    val ShareNetwork = PhosphorIcons.Regular.ShareNetwork
    val ArrowsClockwise = PhosphorIcons.Regular.ArrowsClockwise
    val ArrowsLeftRight = PhosphorIcons.Regular.ArrowsLeftRight
    val ArrowsLeftRightPair = IconPair(
        PhosphorIcons.Regular.ArrowsLeftRight,
        PhosphorIcons.Fill.ArrowsLeftRight
    )
    val ArrowsDownUp = PhosphorIcons.Regular.ArrowsDownUp
    val ArrowsDownUpPair = IconPair(PhosphorIcons.Regular.ArrowsDownUp, PhosphorIcons.Fill.ArrowsDownUp)

    val DownloadSimple = PhosphorIcons.Regular.DownloadSimple
    val UploadSimple = PhosphorIcons.Regular.UploadSimple
    val Cloud = PhosphorIcons.Regular.Cloud
    val CloudFilled = PhosphorIcons.Fill.Cloud
    val CloudArrowDown = PhosphorIcons.Regular.CloudArrowDown
    val CloudArrowUp = PhosphorIcons.Regular.CloudArrowUp
    val CloudSlash = PhosphorIcons.Regular.CloudSlash
    val ArrowCircleDown = PhosphorIcons.Regular.ArrowCircleDown

    val List = PhosphorIcons.Regular.List
    val ListBullets = PhosphorIcons.Regular.ListBullets
    val SquaresFour = PhosphorIcons.Regular.SquaresFour
    val Rows = PhosphorIcons.Regular.Rows
    val RowsPair = IconPair(PhosphorIcons.Regular.Rows, PhosphorIcons.Fill.Rows)
    val Cards = PhosphorIcons.Regular.Cards
    val ChartBar = PhosphorIcons.Regular.ChartBar
    val Play = PhosphorIcons.Regular.Play
    val Pause = PhosphorIcons.Regular.Pause
    val Stop = PhosphorIcons.Regular.Stop
    val SkipBack = PhosphorIcons.Regular.SkipBack
    val SkipForward = PhosphorIcons.Regular.SkipForward
    // 听书面板使用实心圆润的播放控制图标
    val PlayFilled = PhosphorIcons.Fill.Play
    val PauseFilled = PhosphorIcons.Fill.Pause
    val SkipBackFilled = PhosphorIcons.Fill.SkipBack
    val SkipForwardFilled = PhosphorIcons.Fill.SkipForward
    val Headphones = PhosphorIcons.Regular.Headphones
    val PaintBrush = PhosphorIcons.Regular.PaintBrush
    val TextAa = PhosphorIcons.Regular.TextAa
    val TextB = PhosphorIcons.Regular.TextB
    val TextT = PhosphorIcons.Regular.TextT
    val LineSegments = PhosphorIcons.Regular.LineSegments
    val FrameCorners = PhosphorIcons.Regular.FrameCorners
    val Ruler = PhosphorIcons.Regular.Ruler
    val CornersOut = PhosphorIcons.Regular.CornersOut
    val Drop = PhosphorIcons.Regular.Drop
    val Sun = PhosphorIcons.Regular.Sun
    val SunPair = IconPair(PhosphorIcons.Regular.Sun, PhosphorIcons.Fill.Sun)
    val SunDim = PhosphorIcons.Regular.SunDim
    val Moon = PhosphorIcons.Regular.Moon
    val MoonPair = IconPair(PhosphorIcons.Regular.Moon, PhosphorIcons.Fill.Moon)
    val MoonStars = PhosphorIcons.Regular.MoonStars
    val FilmStrip = PhosphorIcons.Regular.FilmStrip
    val FilmStripPair = IconPair(PhosphorIcons.Regular.FilmStrip, PhosphorIcons.Fill.FilmStrip)
    val Lightning = PhosphorIcons.Regular.Lightning
    val LightningPair = IconPair(PhosphorIcons.Regular.Lightning, PhosphorIcons.Fill.Lightning)
    val HandSwipeRight = PhosphorIcons.Regular.HandSwipeRight
    val SpeakerHigh = PhosphorIcons.Regular.SpeakerHigh
    val DeviceMobile = PhosphorIcons.Regular.DeviceMobile

    val Pulse = PhosphorIcons.Regular.Pulse
    val Speedometer = PhosphorIcons.Regular.Speedometer
    val Microphone = PhosphorIcons.Regular.Microphone
    val MicrophonePair = IconPair(PhosphorIcons.Regular.Microphone, PhosphorIcons.Fill.Microphone)
    val MicrophoneStage = PhosphorIcons.Regular.MicrophoneStage
    val Subtitles = PhosphorIcons.Regular.Subtitles
    val Shield = PhosphorIcons.Regular.Shield
    val ShieldCheck = PhosphorIcons.Regular.ShieldCheck
    val Scroll = PhosphorIcons.Regular.Scroll
    val Globe = PhosphorIcons.Regular.Globe
    val Code = PhosphorIcons.Regular.Code
    val UsersThree = PhosphorIcons.Regular.UsersThree
    val ChatCircle = PhosphorIcons.Regular.ChatCircle
    val Warning = PhosphorIcons.Regular.Warning
    val Bug = PhosphorIcons.Regular.Bug
    val Archive = PhosphorIcons.Regular.Archive
    val Key = PhosphorIcons.Regular.Key
    val KeyPair = IconPair(PhosphorIcons.Regular.Key, PhosphorIcons.Fill.Key)
    val Palette = PhosphorIcons.Regular.Palette
    val PalettePair = IconPair(PhosphorIcons.Regular.Palette, PhosphorIcons.Fill.Palette)
    val TextAaPair = IconPair(PhosphorIcons.Regular.TextAa, PhosphorIcons.Fill.TextAa)
    val Timer = PhosphorIcons.Regular.Timer
    val Translate = PhosphorIcons.Regular.Translate
    val Eye = PhosphorIcons.Regular.Eye
    val EyeSlash = PhosphorIcons.Regular.EyeSlash
}

/** Resolve a logical start/end icon without relying on Material's auto-mirror metadata. */
@Composable
fun directionalIcon(ltr: ImageVector, rtl: ImageVector): ImageVector =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) rtl else ltr
