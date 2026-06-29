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
        // 如果 botLayer 持有预渲染 wrapper，先移回 body 再清理
        if (botLayer && botLayer._preIdx !== undefined) {
            var pr = preRendered[botLayer._preIdx];
            if (pr && pr.wrapper && pr.wrapper.parentNode === botLayer) {
                pr.wrapper.style.visibility = 'hidden';
                try { document.body.appendChild(pr.wrapper); } catch(e5) {}
            }
            delete botLayer._preIdx;
        }
        if (topLayer) { resetLayer(topLayer); topLayer.innerHTML = ''; topLayer.style.display = 'none'; }
        if (botLayer) { resetLayer(botLayer); botLayer.innerHTML = ''; botLayer.style.display = 'none'; }
        if (edgeShadow) edgeShadow.style.opacity = '0';
        if (outer) outer.style.visibility = 'visible';
    }
    function initLayers(b) {
        topLayer = document.createElement('div');
        topLayer.setAttribute('data-pg', '1');
        topLayer.style.cssText = 'position:absolute;top:0;left:0;width:' + vw + 'px;height:' + vh + 'px;overflow:hidden;background:' + BG + ';transition:none;z-index:10;display:none;';
        botLayer = document.createElement('div');
        botLayer.setAttribute('data-pg', '1');
        botLayer.style.cssText = 'position:absolute;top:0;left:0;width:' + vw + 'px;height:' + vh + 'px;overflow:hidden;background:' + BG + ';transition:none;display:none;z-index:9;';
        botLayer._preIdx = undefined;
        edgeShadow = document.createElement('div');
        edgeShadow.setAttribute('data-pg', '1');
        edgeShadow.style.cssText = 'position:absolute;top:0;width:80px;height:100%;pointer-events:none;z-index:11;opacity:0;';
        b.appendChild(topLayer);
        b.appendChild(botLayer);
        b.appendChild(edgeShadow);
    }

    // 完成章节切换：将预渲染 wrapper 设为当前 wrap，通知 Kotlin
    function completeChapterSwap(dir, adjIdx) {
        var pr = preRendered[adjIdx];
        if (!pr) return false;
        if (wrap) wrap.remove();
        wrap = pr.wrapper;
        wrap.style.visibility = 'visible';
        wrap.style.position = 'absolute';
        wrap.style.top = '0';
        wrap.style.left = '0';
        // 重置 transform 以显示正确页
        if (dir < 0) wrap.style.transform = 'translateX(' + (-(pr.total - 1) * vw) + 'px)';
        else wrap.style.transform = 'none';
        total = pr.total;
        cur = dir < 0 ? total - 1 : 0;
        outer.appendChild(wrap);
        window.__currentChapterIdx = adjIdx;
        delete preRendered[adjIdx];
        // 清理其他预渲染
        for (var k in preRendered) {
            if (preRendered[k] && preRendered[k].wrapper) {
                try { preRendered[k].wrapper.remove(); } catch(e2) {}
                delete preRendered[k];
            }
        }
        delete botLayer._preIdx;
        resetLayer(topLayer); resetLayer(botLayer);
        topLayer.innerHTML = ''; botLayer.innerHTML = '';
        botLayer.style.display = 'none'; topLayer.style.display = 'none';
        edgeShadow.style.opacity = '0';
        if (outer) outer.style.visibility = 'visible';
        try { AndroidBridge.onPageChanged(cur, total); } catch(e) {}
        try { AndroidBridge.onChapterSwapped(dir); } catch(e) {}
        return true;
    }

    // 拖拽过阈值时动画切换到相邻章节（从当前拖拽位置开始动画）
    function animateChapterSwap(dir, adjIdx) {
        if (flipping) return;
        flipping = true;
        var d = 280, t0 = performance.now();
        var startTop = gx(topLayer), startBot = gx(botLayer);
        var endTop = dir > 0 ? -vw : 0, endBot = 0;
        // 边缘阴影随动画渐显/渐隐
        if (edgeShadow) {
            edgeShadow.style.left = (dir > 0) ? '' : '0';
            edgeShadow.style.right = (dir > 0) ? '0' : '';
            edgeShadow.style.background = 'linear-gradient(' + (dir > 0 ? 'to right' : 'to left') + ', rgba(0,0,0,0.18), transparent)';
        }
        (function go(now) {
            var el = now - t0, pg = Math.min(el / d, 1), t = eo(pg);
            topLayer.style.transform = 'translateX(' + Math.round(startTop + (endTop - startTop) * t) + 'px)';
            botLayer.style.transform = 'translateX(' + Math.round(startBot + (endBot - startBot) * t) + 'px)';
            if (edgeShadow && pg > 0.1) edgeShadow.style.opacity = Math.min((pg - 0.1) / 0.9, 0.5);
            if (pg < 1) { animId = requestAnimationFrame(go); return; }
            animId = null; flipping = false;
            completeChapterSwap(dir, adjIdx);
        })(performance.now());
    }

    // 章节边界动画翻页（tap 触发）
    function chapterFlipTo(dir) {
        if (flipping) return;
        var adjIdx = dir > 0 ? window.__currentChapterIdx + 1 : window.__currentChapterIdx - 1;
        var pr = preRendered[adjIdx];
        if (!pr) { try { AndroidBridge.onChapterFlipReady(dir); } catch(e) {} return; }
        if (animId) { cancelAnimationFrame(animId); animId = null; }
        flipping = true;
        if (outer) outer.style.visibility = 'hidden';

        // topLayer: 当前页克隆
        if (!topLayer.firstChild) {
            var cw = wrap.cloneNode(true);
            cw.style.transform = 'translateX(' + (-cur * vw) + 'px)';
            topLayer.appendChild(cw);
        }
        topLayer.style.display = '';
        topLayer.style.backgroundColor = BG;

        // botLayer: 预渲染相邻章节（真实内容）
        while (botLayer.firstChild) botLayer.firstChild.remove();
        botLayer.appendChild(pr.wrapper);
        pr.wrapper.style.visibility = 'visible';
        pr.wrapper.style.position = 'absolute';
        pr.wrapper.style.top = '0';
        pr.wrapper.style.left = '0';
        // 前进→显示第一列，后退→显示最后一列
        pr.wrapper.style.transform = dir < 0 ? 'translateX(' + (-(pr.total - 1) * vw) + 'px)' : 'none';
        botLayer._preIdx = adjIdx;
        botLayer.style.display = '';
        botLayer.style.backgroundColor = BG;

        if (dir > 0) {
            topLayer.style.zIndex = '10'; botLayer.style.zIndex = '9';
            topLayer.style.boxShadow = '0 0 48px rgba(0,0,0,0.15), 4px 0 16px rgba(0,0,0,0.08)';
            topLayer.style.borderRadius = '0 36px 36px 0';
            botLayer.style.boxShadow = 'none'; botLayer.style.borderRadius = '0';
            topLayer.style.transform = 'translateX(0px)';
            botLayer.style.transform = 'translateX(' + Math.round(vw * 0.7) + 'px)';
        } else {
            botLayer.style.zIndex = '10'; topLayer.style.zIndex = '9';
            botLayer.style.boxShadow = '0 0 48px rgba(0,0,0,0.15), 4px 0 16px rgba(0,0,0,0.08)';
            botLayer.style.borderRadius = '0 36px 36px 0';
            topLayer.style.boxShadow = 'none'; topLayer.style.borderRadius = '0';
            botLayer.style.transform = 'translateX(' + Math.round(-vw) + 'px)';
            topLayer.style.transform = 'translateX(0px)';
        }

        var d = 380, t0 = performance.now();
        var startTop = gx(topLayer), startBot = gx(botLayer);
        var endTop = dir > 0 ? -vw : 0, endBot = 0;
        (function go(now) {
            var el = now - t0, pg = Math.min(el / d, 1), t = eo(pg);
            topLayer.style.transform = 'translateX(' + Math.round(startTop + (endTop - startTop) * t) + 'px)';
            botLayer.style.transform = 'translateX(' + Math.round(startBot + (endBot - startBot) * t) + 'px)';
            if (pg < 1) { animId = requestAnimationFrame(go); return; }
            animId = null; flipping = false;
            completeChapterSwap(dir, adjIdx);
        })(performance.now());
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
        flipping = true;
        var x0 = gx(topLayer), d = dur || 350, t0 = performance.now();
        var botX0 = gx(botLayer);
        var botTarget = (botX0 > 0) ? vw : -vw;
        (function go(now) {
            var el = now - t0, pg = Math.min(el / d, 1), t = eb(pg);
            topLayer.style.transform = 'translateX(' + Math.round(x0 * (1 - t)) + 'px)';
            botLayer.style.transform = 'translateX(' + Math.round(botX0 + (botTarget - botX0) * t) + 'px)';
            if (edgeShadow && pg > 0.5) edgeShadow.style.opacity = Math.max(0, (1 - pg) / 0.5 * 0.5);
            if (pg < 1) { animId = requestAnimationFrame(go); return; }
            animId = null; flipping = false;
            cleanupLayers();
        })(performance.now());
    }

    function aDrag(dx) {
        var c = dx;
        var isF = c < 0;
        var target = isF ? cur + 1 : cur - 1;
        var atBoundary = (isF && cur >= total - 1) || (!isF && cur <= 0);

        if (atBoundary) {
            // 检查是否有预渲染的相邻章节
            var adjIdx = isF ? window.__currentChapterIdx + 1 : window.__currentChapterIdx - 1;
            var pr = preRendered[adjIdx];
            if (pr) {
                // ✅ 预渲染命中：展示真实下一章内容，跟手拖拽（无阻力）
                if (outer) outer.style.visibility = 'hidden';
                if (!topLayer.firstChild) {
                    var cw = wrap.cloneNode(true);
                    cw.style.transform = 'translateX(' + (-cur * vw) + 'px)';
                    topLayer.appendChild(cw);
                }
                topLayer.style.display = '';
                topLayer.style.backgroundColor = BG;

                // botLayer 加载预渲染 wrapper（非克隆！真实内容）
                if (!botLayer.firstChild || botLayer._preIdx !== adjIdx) {
                    while (botLayer.firstChild) botLayer.firstChild.remove();
                    botLayer.appendChild(pr.wrapper);
                    pr.wrapper.style.visibility = 'visible';
                    pr.wrapper.style.position = 'absolute';
                    pr.wrapper.style.top = '0';
                    pr.wrapper.style.left = '0';
                    // 前进→显示第一列，后退→显示最后一列
                    pr.wrapper.style.transform = isF ? 'none' : 'translateX(' + (-(pr.total - 1) * vw) + 'px)';
                    botLayer._preIdx = adjIdx;
                }
                botLayer.style.display = '';
                botLayer.style.backgroundColor = BG;

                // 正常跟手拖拽（无阻力！因为下一章内容真实存在）
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
                return;
            }
            // 无预渲染：回退到渐进式橡皮筋阻力（跟手→逐渐变硬）
            var absDx = Math.abs(dx);
            var maxSlide = vw * 0.3;
            c = (dx > 0 ? 1 : -1) * maxSlide * (1 - Math.exp(-absDx / (vw * 0.1)));
        }

        if (atBoundary) {
            // 无预渲染的边界：只显示当前页克隆 + 阻力
            if (!topLayer.firstChild) {
                var cw2 = wrap.cloneNode(true);
                cw2.style.transform = 'translateX(' + (-cur * vw) + 'px)';
                topLayer.appendChild(cw2);
            }
            topLayer.style.display = '';
            topLayer.style.backgroundColor = BG;
            topLayer.style.zIndex = '10';
            topLayer.style.boxShadow = isF ? '0 0 48px rgba(0,0,0,0.15), 4px 0 16px rgba(0,0,0,0.08)' : '0 0 48px rgba(0,0,0,0.15), -4px 0 16px rgba(0,0,0,0.08)';
            topLayer.style.borderRadius = isF ? '0 36px 36px 0' : '36px 0 0 36px';
            topLayer.style.transform = 'translateX(' + c + 'px)';
            botLayer.style.display = 'none';
            if (outer) outer.style.visibility = 'hidden';
        } else {
            // 正常章节内拖拽（不变）
            if (outer) outer.style.visibility = 'hidden';
            if (!topLayer.firstChild) {
                var cw3 = wrap.cloneNode(true);
                cw3.style.transform = 'translateX(' + (-cur * vw) + 'px)';
                topLayer.appendChild(cw3);
            }
            topLayer.style.display = '';
            topLayer.style.backgroundColor = BG;
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
            if (np >= 0 && np < total) {
                // 章节内翻页
                try { AndroidBridge.onPageFlip(dir); } catch(e) {}
                flipTo(np, 450); return;
            }
            // 章节边界：尝试在 JS 内完成切换
            var adjIdx = dir > 0 ? window.__currentChapterIdx + 1 : window.__currentChapterIdx - 1;
            if (preRendered[adjIdx]) {
                animateChapterSwap(dir, adjIdx);
                return;
            }
            // 无预渲染：回退到 Kotlin 层（Miss 路径）
            try { AndroidBridge.onChapterFlipReady(dir); } catch(e) {}
        }
        bounceBack(350);
    }

    function tapFlip(dir) {
        var np = cur + dir;
        if (np < 0 || np >= total) {
            // 章节边界：尝试 JS 层预渲染命中
            var adjIdx = dir > 0 ? window.__currentChapterIdx + 1 : window.__currentChapterIdx - 1;
            if (preRendered[adjIdx]) { chapterFlipTo(dir); return; }
            // 无预渲染：回退到 Kotlin
            try { AndroidBridge.onChapterFlipReady(dir); } catch(e) {}
            return;
        }
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
        flipping = true;
        var start = gx(wrap);
        var end = -cur * vw;
        var d = 280;
        var t0 = performance.now();
        (function go(now) {
            var el = now - t0, pg = Math.min(el / d, 1);
            var t = 1 - Math.pow(1 - pg, 4);
            wrap.style.transform = 'translateX(' + Math.round(start + (end - start) * t) + 'px)';
            if (pg < 1) { animId = requestAnimationFrame(go); return; }
            animId = null; flipping = false;
            // 如果 bounceBack 时 outer 中有预渲染 wrapper，移回 body
            if (outer._prWrapper) {
                outer._prWrapper.style.visibility = 'hidden';
                try { document.body.appendChild(outer._prWrapper); } catch(e) {}
                outer._prWrapper = null;
                outer._prIdx = undefined;
            }
        })(performance.now());
    }

    function aDrag(dx) {
        var c = dx;
        var isF = c < 0;
        var atBoundary = (isF && cur >= total - 1) || (!isF && cur <= 0);

        if (atBoundary) {
            var adjIdx = isF ? window.__currentChapterIdx + 1 : window.__currentChapterIdx - 1;
            var pr = preRendered[adjIdx];
            if (pr) {
                // 预渲染命中：将相邻章节 wrapper 放入 outer，与当前 wrap 一起拖拽
                wrap.style.transition = 'none';
                if (outer._prIdx !== adjIdx) {
                    // 移除旧的预渲染 wrapper
                    if (outer._prWrapper) {
                        outer._prWrapper.style.visibility = 'hidden';
                        try { document.body.appendChild(outer._prWrapper); } catch(e) {}
                    }
                    outer.appendChild(pr.wrapper);
                    pr.wrapper.style.visibility = 'visible';
                    pr.wrapper.style.position = 'absolute';
                    pr.wrapper.style.top = '0';
                    pr.wrapper.style.left = '0';
                    outer._prWrapper = pr.wrapper;
                    outer._prIdx = adjIdx;
                }
                // 根据方向设置预渲染 wrapper 的初始位置
                if (isF) {
                    // 前进：预渲染在右侧
                    pr.wrapper.style.transform = 'none';
                    wrap.style.transform = 'translateX(' + Math.round(-cur * vw + c) + 'px)';
                } else {
                    // 后退：预渲染在左侧，显示最后一页
                    pr.wrapper.style.transform = 'translateX(' + (-(pr.total - 1) * vw) + 'px)';
                    wrap.style.transform = 'translateX(' + Math.round(-cur * vw + c) + 'px)';
                }
                return;
            }
            // 无预渲染：渐进式橡皮筋阻力
            var absDx = Math.abs(dx);
            var maxSlide = vw * 0.3;
            c = (dx > 0 ? 1 : -1) * maxSlide * (1 - Math.exp(-absDx / (vw * 0.1)));
        }
        wrap.style.transition = 'none';
        wrap.style.transform = 'translateX(' + Math.round(-cur * vw + c) + 'px)';
    }

    function dEnd(dx, vel) {
        if (Math.abs(dx) / vw > 0.25 || Math.abs(vel) > 350) {
            var dir = dx < 0 ? 1 : -1, np = cur + dir;
            if (np >= 0 && np < total) { try { AndroidBridge.onPageFlip(dir); } catch(e) {} flipTo(np); return; }
            // 章节边界：尝试 JS 内完成
            var adjIdx = dir > 0 ? window.__currentChapterIdx + 1 : window.__currentChapterIdx - 1;
            var pr = preRendered[adjIdx];
            if (pr) {
                // 动画交换：当前 wrap 滑出 + 预渲染 wrapper 滑入
                flipping = true;
                var oldWrap = wrap, newWrap = pr.wrapper;
                if (animId) { cancelAnimationFrame(animId); animId = null; }
                var oldStart = gx(oldWrap);
                var targetCur = dir < 0 ? pr.total - 1 : 0;
                var newTarget = -targetCur * vw;
                var newStart = gx(newWrap);
                var d = 280, t0 = performance.now();
                total = pr.total;
                cur = targetCur;
                window.__currentChapterIdx = adjIdx;
                delete preRendered[adjIdx];
                for (var k in preRendered) {
                    if (preRendered[k] && preRendered[k].wrapper) {
                        try { preRendered[k].wrapper.remove(); } catch(e2) {}
                        delete preRendered[k];
                    }
                }
                outer._prWrapper = null; outer._prIdx = undefined;
                (function go(now) {
                    var el = now - t0, pg = Math.min(el / d, 1);
                    var t = 1 - Math.pow(1 - pg, 3);
                    oldWrap.style.transform = 'translateX(' + Math.round(oldStart * (1 - t)) + 'px)';
                    newWrap.style.transform = 'translateX(' + Math.round(newStart + (newTarget - newStart) * t) + 'px)';
                    if (pg < 1) { animId = requestAnimationFrame(go); return; }
                    animId = null; flipping = false;
                    oldWrap.remove();
                    wrap = newWrap;
                    wrap.style.visibility = 'visible';
                    wrap.style.transform = 'translateX(' + newTarget + 'px)';
                    outer.appendChild(wrap);
                    try { AndroidBridge.onPageChanged(cur, total); } catch(e) {}
                    try { AndroidBridge.onChapterSwapped(dir); } catch(e) {}
                })(performance.now());
                return;
            }
            try { AndroidBridge.onChapterFlipReady(dir); } catch(e) {}
        }
        bounceBack();
    }

    function tapFlip(dir) {
        var np = cur + dir;
        if (np < 0 || np >= total) {
            // 章节边界：尝试 JS 层预渲染命中
            var adjIdx = dir > 0 ? window.__currentChapterIdx + 1 : window.__currentChapterIdx - 1;
            var pr = preRendered[adjIdx];
            if (pr) {
                // 简单动画翻页
                if (flipping) return;
                flipping = true;
                if (wrap) wrap.remove();
                wrap = pr.wrapper;
                wrap.style.visibility = 'visible';
                total = pr.total;
                cur = dir < 0 ? total - 1 : 0;
                outer.appendChild(wrap);
                window.__currentChapterIdx = adjIdx;
                delete preRendered[adjIdx];
                for (var k in preRendered) {
                    if (preRendered[k] && preRendered[k].wrapper) {
                        try { preRendered[k].wrapper.remove(); } catch(e2) {}
                        delete preRendered[k];
                    }
                }
                outer._prWrapper = null; outer._prIdx = undefined;
                var start, end, d;
                if (dir > 0) { start = vw; end = 0; }
                else { start = -vw; end = 0; }
                wrap.style.transform = 'translateX(' + start + 'px)';
                d = 380; var t0 = performance.now();
                (function go(now) {
                    var el = now - t0, pg = Math.min(el / d, 1);
                    var t = 1 - Math.pow(1 - pg, 3);
                    wrap.style.transform = 'translateX(' + Math.round(start + (end - start) * t) + 'px)';
                    if (pg < 1) { animId = requestAnimationFrame(go); return; }
                    animId = null; flipping = false;
                    wrap.style.transform = 'translateX(' + (-cur * vw) + 'px)';
                    try { AndroidBridge.onPageChanged(cur, total); } catch(e) {}
                    try { AndroidBridge.onChapterSwapped(dir); } catch(e) {}
                })(performance.now());
                return;
            }
            try { AndroidBridge.onChapterFlipReady(dir); } catch(e) {}
            return;
        }
        try { AndroidBridge.onPageFlip(dir); } catch(e) {}
        flipTo(np);
    }
    """.trimIndent()
}

val pageFlipAnimations = listOf(SlideCoverAnimation, SimpleSlideAnimation)
