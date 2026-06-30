package com.huangder.lumibooks.ui.animation

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
        if (botLayer) { resetLayer(botLayer); botLayer.innerHTML = ''; botLayer.style.display = 'none'; botLayer._loadingIdx = undefined; delete botLayer._loadingIdx; }
        if (edgeShadow) edgeShadow.style.opacity = '0';
        if (outer) outer.style.visibility = 'visible';
        // 🔥 不在此清除 __pendingFlip —— bounceBack 是合法的等待状态，
        // 内容到达后 onPreRenderReady 会负责清除并自动完成章节切换
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
        window.__ccsCalled = adjIdx;  // 🔥 追踪：completeChapterSwap 被调用
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
        window.__pendingFlip = undefined;
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
        if (flipping) { window.__cftBlocked = 'flipping'; return; }
        var adjIdx = dir > 0 ? window.__currentChapterIdx + 1 : window.__currentChapterIdx - 1;
        var pr = preRendered[adjIdx];
        if (!pr) { window.__cftNoPr = adjIdx; window.__pendingFlip = adjIdx; try { AndroidBridge.onChapterFlipReady(dir); } catch(e) {} return; }
        window.__cftCalled = adjIdx;  // 🔥 追踪：chapterFlipTo 开始执行
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
            // 🔥 bounceBack 完成后检查是否有待处理的跨章节翻转
            // （onPreRenderReady 可能在动画期间到达，当时 flipping=true 无法触发 chapterFlipTo）
            window.__bbDone = 1;  // 🔥 追踪：bounceBack 完成
            if (window.__pendingFlip !== undefined) {
                var pendingIdx = window.__pendingFlip;
                var pdir = pendingIdx > window.__currentChapterIdx ? 1 : -1;
                window.__bbPendingIdx = pendingIdx;  // 🔥 追踪：bounceBack 看到的 pending
                if (preRendered[pendingIdx]) {
                    window.__pendingFlip = undefined;
                    window.__bbTriggeredFlip = pendingIdx;  // 🔥 追踪：bounceBack 触发了 chapterFlipTo
                    chapterFlipTo(pdir);
                } else {
                    window.__bbNoPr = pendingIdx;  // 🔥 追踪：pending 存在但 preRendered 不存在
                }
            }
        })(performance.now());
    }

    // 🔥 Kotlin 异步加载完成后调用：处理预渲染内容
    // 三种场景：
    // 1. 用户仍在拖拽 → 无缝替换 loading 为真实内容
    // 2. 用户已松手但有 __pendingFlip → 自动完成章节切换动画
    // 3. 用户已松手无 pending → 清除 loading 标记，preRendered 已就绪，下次拖拽走 Hit Path
    window.onPreRenderReady = function(chapterIndex) {
        var cci = window.__currentChapterIdx;
        if (cci !== undefined) {
            var dir = chapterIndex > cci ? 1 : -1;
            if (chapterIndex !== (dir > 0 ? cci + 1 : cci - 1)) return 'guard_adj';
        }
        if (!preRendered[chapterIndex]) return 'guard_nopr';

        // ── 场景 1：用户仍在拖拽中 ──
        if (typeof dg !== 'undefined' && dg.on) {
            if (typeof botLayer !== 'undefined' && botLayer && botLayer._loadingIdx === chapterIndex) {
                var pr = preRendered[chapterIndex];
                while (botLayer.firstChild) botLayer.firstChild.remove();
                botLayer.appendChild(pr.wrapper);
                pr.wrapper.style.visibility = 'visible';
                pr.wrapper.style.position = 'absolute';
                pr.wrapper.style.top = '0';
                pr.wrapper.style.left = '0';
                pr.wrapper.style.transform = (dir > 0) ? 'none' : 'translateX(' + (-(pr.total - 1) * vw) + 'px)';
                botLayer._preIdx = chapterIndex;
                botLayer._loadingIdx = undefined;
                delete botLayer._loadingIdx;
                window.__pendingFlip = undefined;
            }
            return 'scene1';
        }

        // ── 清理 loading 标记 ──
        if (typeof botLayer !== 'undefined' && botLayer) {
            botLayer._loadingIdx = undefined;
            delete botLayer._loadingIdx;
        }

        // ── 场景 2：有 pending 标记 → 自动完成章节切换动画 ──
        if (window.__pendingFlip === chapterIndex) {
            if (!flipping) {
                window.__pendingFlip = undefined;
                chapterFlipTo(dir);
                return 'scene2_flip';
            }
            return 'scene2_defer';
        }

        // ── 场景 3：无 pending → preRendered 已缓存，下次拖拽/点击走 Hit Path ──
        window.__pendingFlip = undefined;
        return 'scene3_nop';
    };

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
            // 🔥 无预渲染：展示 loading 占位层 + 1:1 跟手拖拽（无阻力！）
            // 参考 HiReader：动画不等内容加载，展示 loading 状态，异步注入内容后替换
            // botLayer 显示 loading spinner，topLayer 显示当前页克隆
            // 拖拽参数与 Hit Path 完全相同（1:1跟手，无视差阻力）
            var adjIdx2 = isF ? window.__currentChapterIdx + 1 : window.__currentChapterIdx - 1;
            if (outer) outer.style.visibility = 'hidden';
            // topLayer: 当前页克隆
            if (!topLayer.firstChild) {
                var cw2 = wrap.cloneNode(true);
                cw2.style.transform = 'translateX(' + (-cur * vw) + 'px)';
                topLayer.appendChild(cw2);
            }
            topLayer.style.display = '';
            topLayer.style.backgroundColor = BG;
            // botLayer: loading 占位（如果还没创建或章节变了就重建）
            if (!botLayer.firstChild || botLayer._loadingIdx !== adjIdx2) {
                while (botLayer.firstChild) botLayer.firstChild.remove();
                var ld = document.createElement('div');
                ld.style.cssText = 'display:flex;align-items:center;justify-content:center;height:100%;font-size:14px;';
                ld.innerHTML = '<div style="text-align:center;color:' + (BG === '#1a1a1a' ? '#666' : '#999') + ';"><div style="width:24px;height:24px;border:2px solid ' + (BG === '#1a1a1a' ? '#333' : '#ddd') + ';border-top-color:' + (BG === '#1a1a1a' ? '#888' : '#999') + ';border-radius:50%;display:inline-block;animation:_spin 0.8s linear infinite;"></div><div style="margin-top:12px;">加载中...</div></div>';
                if (!document.getElementById('_spinKeyframes')) {
                    var sk = document.createElement('style'); sk.id = '_spinKeyframes';
                    sk.textContent = '@keyframes _spin{to{transform:rotate(360deg)}}';
                    document.head.appendChild(sk);
                }
                botLayer.appendChild(ld);
                botLayer._loadingIdx = adjIdx2;
            }
            botLayer.style.display = '';
            botLayer.style.backgroundColor = BG;
            // 🔥 与 Hit Path 完全相同的拖拽参数——1:1跟手，无阻力！
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
            // 🔥 设置 pending 标记，onPreRenderReady 到达后自动完成章节切换
            window.__pendingFlip = adjIdx2;
            // 通知 Kotlin 紧急预渲染此章节
            try { AndroidBridge.onChapterFlipReady(isF ? 1 : -1); } catch(e) {}
        } else {
            // 🔥 章节内拖拽：取消任何待处理的跨章节翻转标记
            window.__pendingFlip = undefined;
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
            // 🔥 无预渲染：bounceBack（loading 占位已在 aDrag 中展示，Kotlin 紧急预加载已触发）
        }
        bounceBack(350);
    }

    function tapFlip(dir) {
        var np = cur + dir;
        if (np < 0 || np >= total) {
            // 章节边界：尝试 JS 层预渲染命中
            var adjIdx = dir > 0 ? window.__currentChapterIdx + 1 : window.__currentChapterIdx - 1;
            if (preRendered[adjIdx]) { chapterFlipTo(dir); return; }
            // 🔥 无预渲染：设置 pending 标记 + 通知 Kotlin 紧急加载
            window.__pendingFlip = adjIdx;
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
            // 🔥 不在此清除 __pendingFlip —— bounceBack 是合法的等待状态
            // 但需要检查待处理的跨章节翻转（onPreRenderReady 可能在动画期间到达）
            window.__bbDone = 1;  // 🔥 追踪：bounceBack 完成
            if (window.__pendingFlip !== undefined) {
                var pendingIdx = window.__pendingFlip;
                var pdir = pendingIdx > window.__currentChapterIdx ? 1 : -1;
                window.__bbPendingIdx = pendingIdx;  // 🔥 追踪
                if (preRendered[pendingIdx]) {
                    window.__pendingFlip = undefined;
                    window.__bbTriggeredFlip = pendingIdx;  // 🔥 追踪：bounceBack 触发了章节交换
                    // PDF 简化版：直接交换 wrapper
                    var pr = preRendered[pendingIdx];
                    if (wrap) wrap.remove();
                    wrap = pr.wrapper;
                    wrap.style.visibility = 'visible';
                    total = pr.total;
                    cur = pdir < 0 ? total - 1 : 0;
                    outer.appendChild(wrap);
                    window.__currentChapterIdx = pendingIdx;
                    delete preRendered[pendingIdx];
                    for (var k in preRendered) {
                        if (preRendered[k] && preRendered[k].wrapper) {
                            try { preRendered[k].wrapper.remove(); } catch(e2) {}
                            delete preRendered[k];
                        }
                    }
                    try { AndroidBridge.onPageChanged(cur, total); } catch(e) {}
                    try { AndroidBridge.onChapterSwapped(pdir); } catch(e) {}
                } else {
                    window.__bbNoPr = pendingIdx;  // 🔥 追踪：pending 存在但 preRendered 不存在
                }
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
            // 🔥 无预渲染：1:1跟手拖拽（无阻力），设置 pending + 触发 Kotlin 紧急加载
            // PDF 页面小渲染快，不需要 loading 占位
            window.__pendingFlip = adjIdx;
            try { AndroidBridge.onChapterFlipReady(isF ? 1 : -1); } catch(e) {}
        } else {
            // 🔥 章节内拖拽：取消任何待处理的跨章节翻转标记
            window.__pendingFlip = undefined;
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
                window.__pendingFlip = undefined;
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
            // 🔥 无预渲染：bounceBack（紧急加载已在 aDrag 中触发，__pendingFlip 已设置）
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
                window.__pendingFlip = undefined;
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
            // 🔥 无预渲染：设置 pending 标记 + 通知 Kotlin
            window.__pendingFlip = adjIdx;
            try { AndroidBridge.onChapterFlipReady(dir); } catch(e) {}
            return;
        }
        try { AndroidBridge.onPageFlip(dir); } catch(e) {}
        flipTo(np);
    }

    // 🔥 Kotlin 异步加载完成后调用（PDF 简化版）
    window.onPreRenderReady = function(chapterIndex) {
        // 🔥 守卫：__currentChapterIdx 可能因 WebView evaluateJavascript 执行顺序尚未初始化
        var cci = window.__currentChapterIdx;
        if (cci !== undefined) {
            var dir = chapterIndex > cci ? 1 : -1;
            if (chapterIndex !== (dir > 0 ? cci + 1 : cci - 1)) return 'guard_adj';
        }
        if (!preRendered[chapterIndex]) return 'guard_nopr';
        if (window.__pendingFlip !== chapterIndex || flipping) return 'pf_skip';
        window.__pendingFlip = undefined;
        // 自动完成章节切换（简单滑入动画）
        var pr = preRendered[chapterIndex];
        flipping = true;
        if (wrap) wrap.remove();
        wrap = pr.wrapper;
        wrap.style.visibility = 'visible';
        total = pr.total;
        cur = dir < 0 ? total - 1 : 0;
        outer.appendChild(wrap);
        window.__currentChapterIdx = chapterIndex;
        delete preRendered[chapterIndex];
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
    };
    """.trimIndent()
}

val pageFlipAnimations = listOf(SlideCoverAnimation, SimpleSlideAnimation)
