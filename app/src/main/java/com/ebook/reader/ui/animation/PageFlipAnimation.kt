package com.ebook.reader.ui.animation

interface PageFlipAnimation {
    val name: String
    fun buildAnimationJs(bgColor: String = "#ffffff"): String
}

/**
 * EPUB 滑动覆盖动画（双层视差）
 */
object SlideCoverAnimation : PageFlipAnimation {
    override val name = "滑动覆盖"

    override fun buildAnimationJs(bgColor: String): String = """
    var topLayer = null, botLayer = null, edgeShadow = null, flipping = false;
    var BG = '$bgColor';

    function resetLayer(el) {
        el.style.transform = 'none';
        el.style.boxShadow = 'none';
        el.style.borderRadius = '0';
        el.style.zIndex = '';
    }
    function cleanupLayers() {
        resetLayer(topLayer); resetLayer(botLayer);
        topLayer.innerHTML = ''; botLayer.innerHTML = '';
        botLayer.style.display = 'none';
        topLayer.style.display = 'none';
        edgeShadow.style.opacity = '0';
        if (outer) outer.style.visibility = 'visible';
    }
    function initLayers(b) {
        topLayer = document.createElement('div');
        topLayer.setAttribute('data-pg', '1');
        topLayer.style.cssText = 'position:absolute;top:0;left:0;width:' + vw + 'px;height:' + vh + 'px;overflow:hidden;background:' + BG + ';transition:none;z-index:10;display:none;';
        botLayer = document.createElement('div');
        botLayer.setAttribute('data-pg', '1');
        botLayer.style.cssText = 'position:absolute;top:0;left:0;width:' + vw + 'px;height:' + vh + 'px;overflow:hidden;background:' + BG + ';transition:none;display:none;z-index:9;';
        edgeShadow = document.createElement('div');
        edgeShadow.setAttribute('data-pg', '1');
        edgeShadow.style.cssText = 'position:absolute;top:0;width:80px;height:100%;pointer-events:none;z-index:11;opacity:0;';
        b.appendChild(topLayer);
        b.appendChild(botLayer);
        b.appendChild(edgeShadow);
    }

    function flipTo(p, dur) {
        if (p < 0 || p >= total || p === cur) return;
        if (animId) { cancelAnimationFrame(animId); animId = null; }
        flipping = true;
        var dir = p > cur ? 1 : -1;
        if (outer) outer.style.visibility = 'hidden';
        if (!topLayer.firstChild) {
            var cw = wrap.cloneNode(true);
            cw.style.transform = 'translateX(' + (-cur * vw) + 'px)';
            topLayer.appendChild(cw);
        }
        topLayer.style.display = '';
        topLayer.style.backgroundColor = BG;
        var startTop = gx(topLayer);
        if (!botLayer.firstChild) {
            var bw = wrap.cloneNode(true);
            bw.style.transform = 'translateX(' + (-p * vw) + 'px)';
            botLayer.innerHTML = '';
            botLayer.appendChild(bw);
        }
        botLayer.style.display = '';
        topLayer.style.zIndex = dir > 0 ? '10' : '9';
        botLayer.style.zIndex = dir > 0 ? '9' : '10';
        var upper = dir > 0 ? topLayer : botLayer;
        upper.style.boxShadow = '0 0 48px rgba(0,0,0,0.15), 4px 0 16px rgba(0,0,0,0.08)';
        upper.style.borderRadius = '0 36px 36px 0';
        upper.style.backgroundColor = BG;
        var startBot = gx(botLayer);
        var endTop, endBot;
        if (dir > 0) { endTop = -vw; endBot = 0; if (startBot === 0) startBot = vw * 0.7; }
        else { endBot = 0; endTop = startTop + vw * 0.2; if (startBot === 0) startBot = -vw * 0.5; }
        botLayer.style.transform = 'translateX(' + startBot + 'px)';
        var d = dur || 450, t0 = performance.now();
        (function go(now) {
            var el = now - t0, pg = Math.min(el / d, 1), t = eo(pg);
            var topX = startTop + (endTop - startTop) * t;
            var botX = dir > 0 ? startBot + (endBot - startBot) * (t * 0.3 + 0.7) : startBot + (endBot - startBot) * t;
            topLayer.style.transform = 'translateX(' + Math.round(topX) + 'px)';
            botLayer.style.transform = 'translateX(' + Math.round(botX) + 'px)';
            if (pg < 1) { animId = requestAnimationFrame(go); return; }
            animId = null; flipping = false;
            cleanupLayers();
            wrap.style.transition = 'none';
            wrap.style.transform = 'translateX(' + (-p * vw) + 'px)';
            cur = p;
            try { AndroidBridge.onPageChanged(cur, total); } catch(e) {}
        })(performance.now());
    }

    function bounceBack(dur) {
        if (flipping) return;
        var x0 = gx(topLayer), d = dur || 350, t0 = performance.now();
        var botX0 = gx(botLayer);
        var botTarget = (botX0 > 0) ? vw : -vw;
        (function go(now) {
            var el = now - t0, pg = Math.min(el / d, 1), t = eb(pg);
            topLayer.style.transform = 'translateX(' + Math.round(x0 * (1 - t)) + 'px)';
            botLayer.style.transform = 'translateX(' + Math.round(botX0 + (botTarget - botX0) * t) + 'px)';
            if (pg < 1) { animId = requestAnimationFrame(go); return; }
            cleanupLayers();
        })(performance.now());
    }

    function aDrag(dx) {
        var c = dx;
        var isF = c < 0;
        var target = isF ? cur + 1 : cur - 1;
        var atBoundary = (isF && cur >= total - 1) || (!isF && cur <= 0);
        if (atBoundary) c = dx * 0.25;
        if (!topLayer.firstChild) {
            var cw = wrap.cloneNode(true);
            cw.style.transform = 'translateX(' + (-cur * vw) + 'px)';
            topLayer.appendChild(cw);
        }
        topLayer.style.display = '';
        topLayer.style.backgroundColor = BG;
        if (atBoundary) {
            topLayer.style.zIndex = '10';
            topLayer.style.boxShadow = isF ? '0 0 48px rgba(0,0,0,0.15), 4px 0 16px rgba(0,0,0,0.08)' : '0 0 48px rgba(0,0,0,0.15), -4px 0 16px rgba(0,0,0,0.08)';
            topLayer.style.borderRadius = isF ? '0 36px 36px 0' : '36px 0 0 36px';
            topLayer.style.transform = 'translateX(' + c + 'px)';
            botLayer.style.display = 'none';
            if (outer) outer.style.visibility = 'hidden';
        } else {
            if (outer) outer.style.visibility = 'hidden';
            if (!botLayer.firstChild || botLayer.firstChild._t !== target) {
                var bw = wrap.cloneNode(true);
                bw._t = target;
                bw.style.transform = 'translateX(' + (-target * vw) + 'px)';
                botLayer.innerHTML = '';
                botLayer.appendChild(bw);
            }
            botLayer.style.display = '';
            if (isF) {
                topLayer.style.zIndex = '10'; botLayer.style.zIndex = '9';
                topLayer.style.boxShadow = '0 0 48px rgba(0,0,0,0.15), 4px 0 16px rgba(0,0,0,0.08)';
                topLayer.style.borderRadius = '0 36px 36px 0';
                botLayer.style.boxShadow = 'none'; botLayer.style.borderRadius = '0';
                topLayer.style.transform = 'translateX(' + c + 'px)';
                botLayer.style.transform = 'translateX(' + Math.round(vw * 0.7 + c * 0.3) + 'px)';
            } else {
                botLayer.style.zIndex = '10'; topLayer.style.zIndex = '9';
                botLayer.style.boxShadow = '0 0 48px rgba(0,0,0,0.15), 4px 0 16px rgba(0,0,0,0.08)';
                botLayer.style.borderRadius = '0 36px 36px 0';
                topLayer.style.boxShadow = 'none'; topLayer.style.borderRadius = '0';
                botLayer.style.transform = 'translateX(' + Math.round(-vw + c) + 'px)';
                topLayer.style.transform = 'translateX(' + Math.round(c * 0.2) + 'px)';
            }
        }
    }

    function dEnd(dx, vel) {
        if (Math.abs(dx) / vw > 0.25 || Math.abs(vel) > 350) {
            var dir = dx < 0 ? 1 : -1, np = cur + dir;
            if (np >= 0 && np < total) { try { AndroidBridge.onPageFlip(dir); } catch(e) {} flipTo(np, 450); return; }
            try { AndroidBridge.onChapterFlipReady(dir); } catch(e) {}
        }
        bounceBack(350);
    }

    function tapFlip(dir) {
        var np = cur + dir;
        if (np < 0 || np >= total) return;
        try { AndroidBridge.onPageFlip(dir); } catch(e) {}
        flipTo(np, 450);
    }
    """.trimIndent()
}

/**
 * PDF 简洁滑动动画（纯跟手，非线性缓动，无视差无阴影）
 */
object SimpleSlideAnimation : PageFlipAnimation {
    override val name = "简洁滑动"

    override fun buildAnimationJs(bgColor: String): String = """
    var flipping = false;

    function initLayers(b) {
        // PDF 不需要额外的动画层，直接用 wrapper
    }

    function flipTo(p, dur) {
        if (p < 0 || p >= total || p === cur) return;
        if (animId) { cancelAnimationFrame(animId); animId = null; }
        flipping = true;

        var start = gx(wrap);
        var end = -p * vw;
        var d = dur || 380;
        var t0 = performance.now();

        (function go(now) {
            var el = now - t0, pg = Math.min(el / d, 1);
            // easeOutCubic: 非线性缓动
            var t = 1 - Math.pow(1 - pg, 3);
            wrap.style.transform = 'translateX(' + Math.round(start + (end - start) * t) + 'px)';
            if (pg < 1) { animId = requestAnimationFrame(go); return; }
            animId = null; flipping = false;
            cur = p;
            try { AndroidBridge.onPageChanged(cur, total); } catch(e) {}
        })(performance.now());
    }

    function bounceBack() {
        if (flipping) return;
        var start = gx(wrap);
        var end = -cur * vw;
        var d = 280;
        var t0 = performance.now();
        (function go(now) {
            var el = now - t0, pg = Math.min(el / d, 1);
            var t = 1 - Math.pow(1 - pg, 4);
            wrap.style.transform = 'translateX(' + Math.round(start + (end - start) * t) + 'px)';
            if (pg < 1) { animId = requestAnimationFrame(go); return; }
        })(performance.now());
    }

    function aDrag(dx) {
        var c = dx;
        if (dx > 0 && cur === 0) c = dx * 0.25;
        else if (dx < 0 && cur === total - 1) c = dx * 0.25;
        wrap.style.transition = 'none';
        wrap.style.transform = 'translateX(' + Math.round(-cur * vw + c) + 'px)';
    }

    function dEnd(dx, vel) {
        if (Math.abs(dx) / vw > 0.25 || Math.abs(vel) > 350) {
            var dir = dx < 0 ? 1 : -1, np = cur + dir;
            if (np >= 0 && np < total) { try { AndroidBridge.onPageFlip(dir); } catch(e) {} flipTo(np); return; }
            try { AndroidBridge.onChapterFlipReady(dir); } catch(e) {}
        }
        bounceBack();
    }

    function tapFlip(dir) {
        var np = cur + dir;
        if (np < 0 || np >= total) return;
        try { AndroidBridge.onPageFlip(dir); } catch(e) {}
        flipTo(np);
    }
    """.trimIndent()
}

val pageFlipAnimations = listOf(SlideCoverAnimation, SimpleSlideAnimation)
