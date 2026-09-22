"""Update bundled Lumi guides with current feature notes and cover metadata."""

from __future__ import annotations

import shutil
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GUIDE_DIR = ROOT / "app" / "src" / "main" / "assets" / "builtin" / "lumi"
COVER_DIR = GUIDE_DIR / "covers"
SOURCE_COVER_DIR = Path(r"E:\Desktop\jc")


CONTENT = {
    "zh-CN": {
        "cover": "SC.webp",
        "updates": """
<section id="updates-2-1-9"><h2>2.1.9 更新内容</h2><p>本版本重点提升了阅读器、书库和听书体验。EPUB、PDF 和漫画重新整理了缓存、预加载和解码清晰度；PDF 与漫画可以在阅读器顶部切换正常、高清和原图三档（墨水屏会隐藏此选项）。高清内容会先显示正常清晰度，再在后台替换高清结果，减少等待。</p><p>书籍原排版新增“原排版”主题套装，默认保留书籍自己的颜色、背景和装饰；原排版与阅读器排版分别保存设置。整页图片、SVG、横向插图和固定版式页面会按原始比例显示，也可以选择整页图裁切填满。连续滚动、卷曲翻页、章节定位和阅读进度恢复也得到修正。</p><p>听书支持从连续滚动的当前位置开始、双击句子从该句开始，以及耳机和蓝牙媒体按键控制。书库新增 Cover Flow、可折叠子文件夹和授权文件夹浏览；从系统分享导入时可以选择复制到应用文件夹或移动到已授权文件夹。WebDAV 地址处理、同步目录创建和异常提示也更稳定。</p><p>TXT 目录规则新增兼容常见阅读器规则的导入，并扩展符号开头和层级化章节标题识别。规则只解析你有权访问的本地 TXT，兼容规则中的脚本不会执行。</p></section>""",
        "toc": """
<h3>7. 符号、卷章层级和兼容规则</h3><p>“符号开头章节”适合“☆、第一章”“★、新的开始”“◆：章节标题”等格式。若标题由卷和章组成，可以填写卷正则和章节正则；Lumi 会先匹配卷，再匹配章节，卷会成为目录中的分组标题。重新扫描后请检查卷标题是否没有被重复当成普通章节。</p><p>在设置中的 TXT 目录规则页面可以导入常见阅读器的兼容规则文件，也可以导入内置兼容预设。兼容规则只使用正则表达式，并按整文件语义检查当前行及相邻行，因此部分环视写法可以工作；反向引用不支持。规则中的脚本或替换代码只作为数据保留，绝不会执行。导入完成后，在本书的目录规则弹窗选择兼容规则，再查看命中数和目录边界。</p><p>兼容规则不能代替书源，也不会下载书籍。命中数过多时收紧规则，命中数为零时先检查编码、空白和标题是否独占一行；保存并应用只重建目录，不会改写原 TXT。</p>""",
        "nav_updates": "2.1.9 更新内容",
        "nav_toc": "TXT 目录规则",
    },
    "zh-TW": {
        "cover": "TC.webp",
        "updates": """
<section id="updates-2-1-9"><h2>2.1.9 更新內容</h2><p>本版本重點提升閱讀器、書庫及聽書體驗。EPUB、PDF 及漫畫重新整理快取、預載入和解碼清晰度；PDF 與漫畫可在閱讀器頂部切換正常、高清及原圖三檔（墨水屏會隱藏此選項）。高清內容會先顯示正常清晰度，再於背景替換高清結果，減少等待。</p><p>書籍原排版新增「原排版」主題套裝，預設保留書籍自己的顏色、背景和裝飾；原排版與閱讀器排版分開儲存設定。整頁圖片、SVG、橫向插圖及固定版式頁面會按原始比例顯示，也可選擇整頁圖裁切填滿。連續滾動、卷曲翻頁、章節定位及閱讀進度恢復亦已修正。</p><p>聽書支援從連續滾動的目前位置開始、雙擊句子從該句開始，以及耳機和藍牙媒體按鍵控制。書庫新增 Cover Flow、可摺疊子資料夾及授權資料夾瀏覽；從系統分享匯入時可選擇複製到應用程式資料夾或移動到已授權資料夾。WebDAV 地址處理、同步資料夾建立和錯誤提示亦更穩定。</p><p>TXT 目錄規則新增兼容常見閱讀器規則的匯入，並擴展符號開頭和層級化章節標題識別。規則只解析你有權存取的本機 TXT，兼容規則中的腳本不會執行。</p></section>""",
        "toc": """
<h3>七、符號、卷章層級及兼容規則</h3><p>「符號開頭章節」適合「☆、第一章」「★、新的開始」「◆：章節標題」等格式。若標題由卷和章組成，可分別填寫卷正規表達式及章節正規表達式；Lumi 會先匹配卷，再匹配章節，卷會成為目錄中的分組標題。重新掃描後請確認卷標題沒有重複成為普通章節。</p><p>在設定的 TXT 目錄規則頁面可以匯入常見閱讀器的兼容規則檔案，也可以匯入內置兼容預設。兼容規則只使用正規表達式，並按整份檔案語義檢查目前行及相鄰行，因此部分環視寫法可以運作；反向引用不支援。規則中的腳本或替換程式碼只作資料保存，絕不會執行。匯入後，在本書的目錄規則視窗選擇兼容規則，再檢查命中數及目錄界線。</p><p>兼容規則不是書源，也不會下載書籍。命中太多時收緊規則，命中為零時先檢查編碼、空白和標題是否獨佔一行；儲存並套用只會重建目錄，不會改寫原 TXT。</p>""",
        "nav_updates": "2.1.9 更新內容",
        "nav_toc": "TXT 目錄規則",
    },
    "zh-HK": {
        "cover": "TC.webp",
        "updates": """
<section id="updates-2-1-9"><h2>2.1.9 更新內容</h2><p>本版本重點改善閱讀器、書庫和聽書體驗。EPUB、PDF 和漫畫重新整理快取、預載入及解碼清晰度；PDF 和漫畫可在閱讀器頂部切換正常、高清和原圖三檔（墨水屏會隱藏此選項）。高清內容會先顯示正常清晰度，再在背景替換高清結果，減少等待。</p><p>書籍原排版新增「原排版」主題套裝，預設保留書籍自己的顏色、背景和裝飾；原排版和閱讀器排版分開儲存設定。整頁圖片、SVG、橫向插圖和固定版式頁面會按原始比例顯示，也可以選擇整頁圖裁切填滿。連續滾動、卷曲翻頁、章節定位和閱讀進度恢復亦已修正。</p><p>聽書支援從連續滾動的目前位置開始、雙擊句子從該句開始，以及耳機和藍牙媒體按鍵控制。書庫新增 Cover Flow、可摺疊子資料夾和授權資料夾瀏覽；從系統分享匯入時可以選擇複製到應用程式資料夾或移動到已授權資料夾。WebDAV 地址處理、同步資料夾建立和錯誤提示亦更穩定。</p><p>TXT 目錄規則新增兼容常見閱讀器規則的匯入，並擴展符號開頭和層級化章節標題識別。規則只解析你有權存取的本機 TXT，兼容規則中的腳本不會執行。</p></section>""",
        "toc": """
<h3>七、符號、卷章層級和兼容規則</h3><p>「符號開頭章節」適合「☆、第一章」「★、新的開始」「◆：章節標題」等格式。若標題由卷和章組成，可分別填寫卷正規表達式和章節正規表達式；Lumi 會先匹配卷，再匹配章節，卷會成為目錄中的分組標題。重新掃描後請檢查卷標題沒有重複成為普通章節。</p><p>在設定的 TXT 目錄規則頁面可以匯入常見閱讀器的兼容規則檔案，也可以匯入內置兼容預設。兼容規則只使用正規表達式，並按整份檔案語義檢查目前行及相鄰行，因此部分環視寫法可以運作；反向引用不支援。規則中的腳本或替換程式碼只作資料保留，絕不會執行。匯入後，在本書的目錄規則視窗選擇兼容規則，再檢查命中數和目錄界線。</p><p>兼容規則不是書源，也不會下載書籍。命中太多時收緊規則，命中為零時先檢查編碼、空白和標題是否獨佔一行；儲存並套用只會重建目錄，不會改寫原 TXT。</p>""",
        "nav_updates": "2.1.9 更新內容",
        "nav_toc": "TXT 目錄規則",
    },
    "zh-MO": {
        "cover": "TC.webp",
        "updates": """
<section id="updates-2-1-9"><h2>2.1.9 更新內容</h2><p>本版本重點改善閱讀器、書庫與聽書體驗。EPUB、PDF 與漫畫重新整理快取、預載入及解碼清晰度；PDF 與漫畫可在閱讀器頂部切換正常、高清與原圖三檔（墨水屏會隱藏此選項）。高清內容會先顯示正常清晰度，再在背景替換高清結果，減少等待。</p><p>書籍原排版新增「原排版」主題套裝，預設保留書籍自己的顏色、背景與裝飾；原排版與閱讀器排版分開儲存設定。整頁圖片、SVG、橫向插圖與固定版式頁面會按原始比例顯示，也可以選擇整頁圖裁切填滿。連續滾動、卷曲翻頁、章節定位與閱讀進度恢復亦已修正。</p><p>聽書支援從連續滾動的目前位置開始、雙擊句子從該句開始，以及耳機和藍牙媒體按鍵控制。書庫新增 Cover Flow、可摺疊子資料夾與授權資料夾瀏覽；從系統分享匯入時可以選擇複製到應用程式資料夾或移動到已授權資料夾。WebDAV 地址處理、同步資料夾建立和錯誤提示亦更穩定。</p><p>TXT 目錄規則新增兼容常見閱讀器規則的匯入，並擴展符號開頭和層級化章節標題識別。規則只解析你有權存取的本機 TXT，兼容規則中的腳本不會執行。</p></section>""",
        "toc": """
<h3>七、符號、卷章層級及兼容規則</h3><p>「符號開頭章節」適合「☆、第一章」「★、新的開始」「◆：章節標題」等格式。若標題由卷和章組成，可分別填寫卷正規表達式和章節正規表達式；Lumi 會先匹配卷，再匹配章節，卷會成為目錄中的分組標題。重新掃描後請檢查卷標題沒有重複成為普通章節。</p><p>在設定的 TXT 目錄規則頁面可以匯入常見閱讀器的兼容規則檔案，也可以匯入內置兼容預設。兼容規則只使用正規表達式，並按整份檔案語義檢查目前行及相鄰行，因此部分環視寫法可以運作；反向引用不支援。規則中的腳本或替換程式碼只作資料保留，絕不會執行。匯入後，在本書的目錄規則視窗選擇兼容規則，再檢查命中數和目錄界線。</p><p>兼容規則不是書源，也不會下載書籍。命中太多時收緊規則，命中為零時先檢查編碼、空白和標題是否獨佔一行；儲存並套用只會重建目錄，不會改寫原 TXT。</p>""",
        "nav_updates": "2.1.9 更新內容",
        "nav_toc": "TXT 目錄規則",
    },
    "en": {
        "cover": "EN.webp",
        "updates": """
<section id="updates-2-1-9"><h2>What's new in 2.1.9</h2><p>This release improves reading, the library, and listening. EPUB, PDF, and comic readers now coordinate caching, preloading, and decode quality more carefully. PDF and comic readers offer Normal, High, and Original clarity from the top bar (the control is hidden in e-ink mode). High-quality pages show the normal result first and replace it in the background.</p><p>Book-layout EPUBs now have an Original layout theme that preserves the publisher's colors, backgrounds, and decorations by default. Book layout and reader layout keep separate settings. Full-page images, SVG, landscape illustrations, and fixed-layout pages keep their aspect ratio; an optional crop-to-fill setting is available. Continuous scrolling, curl turns, chapter navigation, and position recovery are more reliable.</p><p>Listening can start at the current position in continuous scrolling, start from a sentence after a double tap, and respond to wired or Bluetooth headset media buttons. The library adds Cover Flow, collapsible subfolders, and an authorized-folder browser. Sharing a book into Lumi can now copy it into the app folder or move it into an authorized folder. WebDAV URL handling, sync-directory creation, and diagnostics are more robust.</p><p>TXT table-of-contents rules now import compatible rules from common reader apps and recognize symbol-prefixed and hierarchical headings. Rules parse only local TXT files you are allowed to access; scripts inside compatible rules are never executed.</p></section>""",
        "toc": """
<h3>7. Symbols, volume hierarchy, and compatible rules</h3><p>Use “Symbol-prefixed chapters” for lines such as “☆, Chapter 1”, “★, New beginning”, or “◆: Epilogue”. For books with volumes and chapters, fill in both fields: Lumi checks the volume regex first and shows matching lines as grouping headings before matching chapter lines. After rescanning, confirm that volume headings have not also become ordinary chapters.</p><p>In Settings, TXT table of contents rules can import compatible rule files from common reader apps or a built-in compatible preset. Lumi uses only each rule's regular expression and evaluates it with whole-file semantics, so some look-around expressions work; back-references are unsupported. Script or replacement code is retained only as data and is never executed. After importing, select a compatible rule in the book's rule dialog and inspect its match count and chapter boundaries.</p><p>Compatible rules are not book sources and do not download books. If there are too many matches, make the pattern stricter. If there are none, check encoding, whitespace, and whether the heading occupies its own line. Save and apply rebuilds the index without changing the original TXT.</p>""",
        "nav_updates": "What's new in 2.1.9",
        "nav_toc": "TXT table of contents rules",
    },
    "ja": {
        "cover": "JP.webp",
        "updates": """
<section id="updates-2-1-9"><h2>2.1.9 の更新内容</h2><p>今回の更新では、読書画面、本棚、読み上げを改善しました。EPUB、PDF、漫画のキャッシュ、先読み、解像度を整理し、PDF と漫画では上部から通常・高画質・原画の3段階を選べます（電子ペーパーでは非表示）。高画質は通常結果を先に表示してからバックグラウンドで置き換えます。</p><p>書籍本来のレイアウトに「原レイアウト」テーマを追加し、出版社の色、背景、装飾を初期状態で保ちます。本来のレイアウトとリーダーレイアウトの設定は別々に保存されます。全面画像、SVG、横長画像、固定レイアウトは縦横比を維持し、必要なら全面画像を切り抜いて画面いっぱいにできます。連続スクロール、カールめくり、章移動、位置復元も安定しました。</p><p>読み上げは連続スクロールの現在位置やダブルタップした文から開始でき、イヤホンや Bluetooth のメディアボタンにも対応します。本棚には Cover Flow、折りたたみ可能なサブフォルダー、許可フォルダーの閲覧画面を追加しました。共有から取り込むときは、アプリ内へコピーするか許可済みフォルダーへ移動するかを選べます。WebDAV の URL 処理と同期診断も改善しました。</p><p>TXT の目次ルールは一般的なリーダーの互換ルールを読み込めるようになり、記号で始まる見出しと巻・章の階層も認識します。ルールは権限のあるローカル TXT だけを解析し、互換ルール内のスクリプトは実行しません。</p></section>""",
        "toc": """
<h3>7. 記号、巻と章の階層、互換ルール</h3><p>「記号で始まる章」は「☆、第1章」「★、新しい始まり」「◆：終章」のような行に使います。巻と章がある本では巻正規表現と章正規表現を両方入力してください。Lumi は巻を先に判定し、巻を目次のグループ見出しとして表示してから章を判定します。再スキャン後、巻が通常の章として重複していないか確認します。</p><p>設定の TXT 目次ルールから、一般的なリーダーの互換ルールファイルまたは内蔵互換プリセットを読み込めます。使用するのは正規表現だけで、ファイル全体の意味で現在行と前後の行を評価します。そのため一部の先読み・後読みは使えますが、後方参照は使えません。ルールのスクリプトや置換コードはデータとして保持するだけで、実行しません。読み込み後、本のルール画面で互換ルールを選び、命中数と章の境界を確認してください。</p><p>互換ルールは書籍ソースではなく、書籍をダウンロードしません。命中が多すぎるときは正規表現を厳しくし、0件のときは文字コード、空白、見出しが独立行かを確認します。「保存して適用」は本文を変更せず目次だけを作り直します。</p>""",
        "nav_updates": "2.1.9 の更新内容",
        "nav_toc": "TXT の目次ルール",
    },
    "ko": {
        "cover": "KR.webp",
        "updates": """
<section id="updates-2-1-9"><h2>2.1.9 업데이트 내용</h2><p>이번 버전은 읽기 화면, 서재, 듣기 기능을 개선했습니다. EPUB, PDF, 만화의 캐시와 미리 읽기, 디코딩 품질을 정리했으며 PDF와 만화에서 일반·고화질·원본 3단계를 선택할 수 있습니다(전자잉크 모드에서는 숨겨짐). 고화질 결과는 일반 결과를 먼저 보여 준 뒤 백그라운드에서 교체합니다.</p><p>원본 레이아웃 EPUB에는 출판사의 색상, 배경, 장식을 기본으로 유지하는 “원본 레이아웃” 테마가 추가되었습니다. 원본 레이아웃과 리더 레이아웃의 설정은 따로 저장됩니다. 전체 페이지 이미지, SVG, 가로 이미지, 고정 레이아웃은 비율을 유지하며 전체 페이지를 잘라 화면에 맞추는 옵션도 있습니다. 연속 스크롤, 컬 넘김, 장 이동, 위치 복원이 안정되었습니다.</p><p>듣기는 연속 스크롤의 현재 위치나 두 번 탭한 문장부터 시작할 수 있고 유선·Bluetooth 헤드셋 미디어 버튼을 지원합니다. 서재에는 Cover Flow, 접을 수 있는 하위 폴더, 권한 폴더 탐색이 추가되었습니다. 공유로 가져올 때 앱 폴더에 복사하거나 권한 폴더로 이동할 수 있습니다. WebDAV 주소 처리와 동기화 진단도 개선되었습니다.</p><p>TXT 목차 규칙은 일반 리더 앱의 호환 규칙을 가져올 수 있고 기호로 시작하는 제목과 권·장 계층을 인식합니다. 규칙은 접근 권한이 있는 로컬 TXT만 분석하며 호환 규칙의 스크립트는 실행하지 않습니다.</p></section>""",
        "toc": """
<h3>7. 기호, 권·장 계층, 호환 규칙</h3><p>“기호로 시작하는 장”은 “☆, 제1장”, “★, 새로운 시작”, “◆: 끝장”과 같은 줄에 사용합니다. 권과 장이 함께 있는 책은 권 정규식과 장 정규식을 모두 입력하세요. Lumi는 권을 먼저 판정해 목차의 그룹 제목으로 표시한 다음 장을 판정합니다. 다시 스캔한 뒤 권 제목이 일반 장으로 중복되지 않았는지 확인하세요.</p><p>설정의 TXT 목차 규칙에서 일반 리더 앱의 호환 규칙 파일이나 내장 호환 프리셋을 가져올 수 있습니다. 각 규칙의 정규식만 사용하고 파일 전체 의미로 현재 줄과 앞뒤 줄을 평가하므로 일부 전후방 탐색은 사용할 수 있지만 역참조는 지원하지 않습니다. 규칙의 스크립트나 치환 코드는 데이터로만 보관하며 실행하지 않습니다. 가져온 뒤 책의 규칙 창에서 호환 규칙을 선택하고 일치 수와 장 경계를 확인하세요.</p><p>호환 규칙은 책 소스가 아니며 책을 내려받지 않습니다. 일치가 너무 많으면 정규식을 더 엄격하게 하고, 0개면 인코딩과 공백, 제목이 한 줄로 분리되어 있는지 확인하세요. 저장 후 적용은 원본 TXT를 바꾸지 않고 목차만 다시 만듭니다.</p>""",
        "nav_updates": "2.1.9 업데이트 내용",
        "nav_toc": "TXT 목차 규칙",
    },
}


GUIDES = {
    "guide_zh-CN.epub": "zh-CN",
    "guide_zh-TW.epub": "zh-TW",
    "guide_zh-HK.epub": "zh-HK",
    "guide_zh-MO.epub": "zh-MO",
    "guide_en.epub": "en",
    "guide_ja.epub": "ja",
    "guide_ko.epub": "ko",
}


def update_guide(path: Path, locale: str) -> None:
    spec = CONTENT[locale]
    source_cover = SOURCE_COVER_DIR / spec["cover"]
    if not source_cover.is_file():
        raise FileNotFoundError(source_cover)

    with zipfile.ZipFile(path, "r") as source:
        entries = {info.filename: source.read(info.filename) for info in source.infolist()}

    content_name = "OEBPS/content.xhtml"
    nav_name = "OEBPS/nav.xhtml"
    opf_name = "OEBPS/package.opf"
    content = entries[content_name].decode("utf-8")
    nav = entries[nav_name].decode("utf-8")
    opf = entries[opf_name].decode("utf-8")

    if 'id="updates-2-1-9"' not in content:
        marker = '<section id="s3">'
        if marker not in content:
            raise ValueError(f"Cannot find insertion point in {path}")
        content = content.replace(marker, spec["updates"] + "\n" + marker, 1)

    advanced_markers = (
        "Symbols, volume hierarchy",
        "符号、卷章层级",
        "符號、卷章層級",
        "記号、巻と章",
        "기호, 권·장",
    )
    if not any(marker in content for marker in advanced_markers):
        marker = "</section>\n<section id=\"s3\">"
        if marker not in content:
            marker = "</section>\r\n<section id=\"s3\">"
        if marker not in content:
            raise ValueError(f"Cannot find TOC insertion point in {path}")
        content = content.replace(marker, spec["toc"] + "\n" + marker, 1)

    nav = "\n".join(
        line for line in nav.splitlines()
        if "content.xhtml#updates-2-1-9" not in line
    )
    marker = '<li><a href="content.xhtml#s3">'
    if marker not in nav:
        raise ValueError(f"Cannot find nav insertion point in {path}")
    nav = nav.replace(
        marker,
        f'<li><a href="content.xhtml#updates-2-1-9">{spec["nav_updates"]}</a></li>\n'
        + marker,
        1,
    )

    if 'id="cover-image"' not in opf:
        manifest_marker = "<manifest>"
        cover_item = '<item id="cover-image" href="images/cover.webp" media-type="image/webp" properties="cover-image"/>'
        opf = opf.replace(manifest_marker, manifest_marker + cover_item, 1)
    if 'name="cover"' not in opf:
        metadata_marker = "</metadata>"
        opf = opf.replace(metadata_marker, '<meta name="cover" content="cover-image"/>' + metadata_marker, 1)

    entries[content_name] = content.encode("utf-8")
    entries[nav_name] = nav.encode("utf-8")
    entries[opf_name] = opf.encode("utf-8")
    entries["OEBPS/images/cover.webp"] = source_cover.read_bytes()

    temporary = path.with_suffix(path.suffix + ".tmp")
    with zipfile.ZipFile(temporary, "w") as target:
        for name, data in entries.items():
            info = zipfile.ZipInfo(name)
            info.compress_type = zipfile.ZIP_STORED if name == "mimetype" else zipfile.ZIP_DEFLATED
            target.writestr(info, data)
    temporary.replace(path)


def main() -> None:
    COVER_DIR.mkdir(parents=True, exist_ok=True)
    for filename, locale in GUIDES.items():
        spec = CONTENT[locale]
        shutil.copy2(SOURCE_COVER_DIR / spec["cover"], COVER_DIR / spec["cover"])
        update_guide(GUIDE_DIR / filename, locale)
        print(f"updated {filename} with {spec['cover']}")


if __name__ == "__main__":
    main()
