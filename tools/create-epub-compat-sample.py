"""Create an independently authored EPUB for manual reader comparison.

Contains intentional Duokan extension probes; not an EPUB conformance test.
No external books, artwork or fonts are copied. Uses only Python's standard library.
"""
from pathlib import Path
from html import escape
import json
import struct
import sys
import zipfile
import zlib


def image(width, height):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            border = x < 12 or y < 12 or x >= width - 12 or y >= height - 12
            rows.extend((180, 48, 48) if border else (65, 111, 145) if x < width // 2 else (225, 191, 111))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))


def xhtml(title, content, extra=''):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="zh-CN">
<head><title>{escape(title)}</title><link rel="stylesheet" href="style.css"/>{extra}</head>
<body>{content}</body></html>'''


def main():
    root = Path(__file__).resolve().parent.parent
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else root / '.local/duokan-research/sample'
    output.mkdir(parents=True, exist_ok=True)
    prose = '这是独立编写的排版对照文字，用来检查换行、图文环绕和翻页后是否丢字。'
    paragraphs = ''.join(f'<p>第{n}段。{prose * 4}</p>' for n in range(1, 13))
    chapters = [
        ('guide', '00 使用与判分说明', '''<h1>EPUB 排版对照样本</h1>
<p>本书为原创诊断样本，含标准 EPUB 写法和有意保留的多看扩展，不代表官方多看规范或合规认证。</p>
<p>请在同一设备、同字号、同字体、同方向下比较。先用原书字体/原书排版，再单独检查用户覆盖和重排模式。</p>
<p>逐项记录：正确、降级可读、错误、未验证。不要把这个合成样本的表现推算成真实书兼容率。</p>
<p>图片红色边框四边都应可见；蓝色和黄色两半用于识别裁切与跨页。第5至7节需分别检查单页及横屏双页。</p>
<p>本书没有嵌入字体，DK 字体测试专门观察阅读器能否提供角色字体回退。</p>''', ''),
        ('inline', '01 标准图片与公式', '''<h1>标准图片与公式</h1>
<p>前文<img class="formula" src="square.png" alt="公式图"/>后文：class 声明行内。</p>
<p>前文<img style="display:inline;height:1em;width:1em;vertical-align:middle" src="square.png" alt="公式图"/>后文：style 声明行内。</p>
<p>前文<img id="id-formula" src="square.png" alt="公式图"/>后文：ID 声明行内。</p>
<p>前文<img class="small-glyph" src="square.png" alt="花字"/>后文：仅声明尺寸，默认应为行内图片。</p>
<figure><img class="illustration" src="wide.png" alt="独立整行插图"/><figcaption>整行插图及题注</figcaption></figure>''', ''),
        ('notes', '02 标准与多看脚注', '''<h1>脚注入口</h1>
<p>标准链接式注释<a epub:type="noteref" href="#standard-note">[1]</a>，应显示注释或正确跳转。</p>
<p>图片携带注释<img class="duokan-footnote" src="square.png" alt="这是图片 alt 中的独立注释正文。"/>，多看扩展测试。</p>
<p>普通图片也有 alt<img class="formula" src="square.png" alt="普通图片说明，不应变成脚注"/>，应保持普通图片交互。</p>
<p>空内容脚注图<img class="duokan-footnote" src="square.png" alt=""/>，不应弹出空注释。</p>
<aside id="standard-note" epub:type="footnote"><p>这是标准链接指向的注释正文。</p></aside>''', ''),
        ('float', '03 图文环绕与题注', f'''<h1>浮图与分页</h1>
<div class="float-right"><img class="illustration" src="square.png" alt="右浮图"/></div>
<p>{prose * 8}</p><div class="clear"></div>
<p>下面增加正文，将后续图片推进到分页边界附近。设备高度变化会改变实际边界。</p>{paragraphs}
<div class="float-left"><img class="illustration" src="tall.png" alt="左浮长图"/><p class="caption">含题注的长图容器</p></div>
<p>{prose * 9}</p><div class="clear"></div>
<table class="gallery"><tbody><tr><td><img class="illustration" src="square.png" alt="并列左图"/></td><td><img class="illustration" src="square.png" alt="并列右图"/></td></tr></tbody></table>''', ''),
        ('type', '04 字体、缩进与首字', '''<h1>角色字体与专用声明</h1>
<p class="song">DK-SONGTI：正文宋体角色。</p><p class="kai">DK-KAITI：引文楷体角色。</p>
<p class="hei">DK-HEITI：标题黑体角色。</p><p class="code">DK-CODE: ABC 012345</p>
<p class="standard-indent">标准 text-indent:2em，应缩进。</p>
<p class="duokan-indent">仅 duokan-text-indent:2em，测试专有声明兼容。</p>
<p class="both-indent">标准与专有声明同为2em，不应重复缩进。</p>
<p><span class="standard-drop">首</span>字使用标准 float 和大字号；它应保留大字环绕的基本结构。</p>
<div class="clear"></div><p><span class="duokan-drop">首</span>字仅声明多看两行首字属性，用来观察是否有专用支持。</p>
<p class="features">作者特性列表：liga=0、kern=0。这里需要检查计算样式和真实字形，不应只凭肉眼判定。</p>''', ''),
        ('fullscreen', '05 多看全屏插图', '<div><img class="illustration" src="tall.png" alt="红边应四边完整可见"/></div>', 'duokan-page-fullscreen'),
        ('fitwindow', '06 多看窗口适配', '<div><img class="illustration" src="wide.png" alt="宽图与窗口适配"/></div>', 'duokan-page-fitwindow'),
        ('spread', '07 多看跨页属性', '<div><img class="illustration" src="wide.png" alt="跨页候选宽图，需记录真实阅读器行为"/></div>', 'duokan-page-fullscreen-spread-left'),
        ('vertical', '08 竖排与注音', '''<h1>竖排与注音</h1><section class="vertical">
<p>中文竖排，检查标点「括号」与数字<span class="horizontal">2026</span>混排。</p>
<p><ruby>排<rt>pái</rt></ruby><ruby>版<rt>bǎn</rt></ruby>注音是否与正文重叠。</p>
</section><p>独立横排正文，应可选择与搜索。</p>''', ''),
    ]
    css = '''body{line-height:1.8}h1{text-align:center}figure{margin:1em 0}figcaption,.caption{font-size:.8em;text-align:center}
.formula,.small-glyph{height:1em;width:1em}.formula{display:inline;vertical-align:middle}
#id-formula{display:inline;height:1em;width:1em;vertical-align:middle}.illustration{width:100%;height:auto}
.duokan-footnote{width:.8em;height:.8em}.float-right{float:right;width:35%;margin-left:.5em}
.float-left{float:left;width:30%;margin-right:.5em}.clear{clear:both}.gallery{width:100%}.gallery td{width:50%}
.song{font-family:DK-SONGTI}.kai{font-family:DK-KAITI}.hei{font-family:DK-HEITI}.code{font-family:DK-CODE}
.standard-indent{text-indent:2em}.duokan-indent{duokan-text-indent:2em}.both-indent{text-indent:2em;duokan-text-indent:2em}
.standard-drop{float:left;font-size:2em}.duokan-drop{duokan-drop-caps-style:duokan-two-line-caps}
.features{font-feature-settings:"liga" 0,"kern" 0}.vertical{writing-mode:vertical-rl;height:24em}
.horizontal{writing-mode:horizontal-tb;display:inline-block}'''
    manifest = '<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>'
    manifest += '<item id="css" href="style.css" media-type="text/css"/>'
    manifest += ''.join(f'<item id="{name}" href="{name}.png" media-type="image/png"/>' for name in ('square', 'wide', 'tall'))
    manifest += ''.join(f'<item id="{key}" href="{key}.xhtml" media-type="application/xhtml+xml"/>' for key, _, _, _ in chapters)
    spine = ''.join(f'<itemref idref="{key}"' + (f' properties="{flag}"' if flag else '') + '/>' for key, _, _, flag in chapters)
    opf = f'''<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="duokan-book-id">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="duokan-book-id">urn:uuid:69fb16fc-7f61-4d24-a9c1-af3fd409aebc</dc:identifier>
<dc:title>EPUB 排版对照样本</dc:title><dc:creator>Lumi Books compatibility research</dc:creator><dc:language>zh-CN</dc:language>
<meta property="dcterms:modified">2026-11-07T00:00:00Z</meta></metadata><manifest>{manifest}</manifest><spine>{spine}</spine></package>'''
    toc = '<nav epub:type="toc"><h1>目录</h1><ol>' + ''.join(f'<li><a href="{key}.xhtml">{escape(title)}</a></li>' for key, title, _, _ in chapters) + '</ol></nav>'
    target = output / 'epub-layout-comparison.epub'
    with zipfile.ZipFile(target, 'w') as book:
        book.writestr('mimetype', 'application/epub+zip', compress_type=zipfile.ZIP_STORED)
        entries = {'META-INF/container.xml': '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>',
                   'OPS/book.opf': opf, 'OPS/style.css': css, 'OPS/nav.xhtml': xhtml('目录', toc)}
        entries.update({f'OPS/{key}.xhtml': xhtml(title, body) for key, title, body, _ in chapters})
        entries.update({f'OPS/{name}.png': image(w, h) for name, w, h in [('square', 240, 240), ('wide', 800, 320), ('tall', 400, 800)]})
        for name, data in entries.items():
            book.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(target) as book:
        assert book.namelist()[0] == 'mimetype'
        assert book.getinfo('mimetype').compress_type == zipfile.ZIP_STORED
        assert book.testzip() is None
        import xml.etree.ElementTree as ET
        for name in book.namelist():
            if name.endswith(('.xml', '.opf', '.xhtml')):
                ET.fromstring(book.read(name))
    (output / 'sample-manifest.json').write_text(json.dumps({
        'scope': 'Original synthetic manual comparison sample; intentional proprietary extension probes; not EPUBCheck or reader validation.',
        'chapters': [{'href': f'OPS/{key}.xhtml', 'title': title, 'duokanProperty': flag or None} for key, title, _, flag in chapters],
        'checks': ['ZIP CRC', 'mimetype first and stored', 'XML well formed']}, ensure_ascii=False, indent=2), encoding='utf-8')
    print(target)


if __name__ == '__main__':
    main()
