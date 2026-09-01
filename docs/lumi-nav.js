(function () {
  'use strict';

  /* ── Scroll reveal（全站共用，页面加 html.js 类后 .reveal 才隐藏） ── */
  var revealed = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
          io.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -30px 0px' });
    revealed.forEach(function (el) { io.observe(el); });
  } else {
    revealed.forEach(function (el) { el.classList.add('visible'); });
  }

  /* ── Top nav scroll state ── */
  var nav = document.querySelector('.top-nav');
  var menuButton = document.querySelector('.nav-more-btn');
  var menu = document.querySelector('.nav-more-dropdown');

  function setMenu(open) {
    if (!menu || !menuButton) return;
    menu.classList.toggle('open', open);
    menuButton.setAttribute('aria-expanded', open ? 'true' : 'false');
  }

  if (menuButton && menu) {
    menuButton.setAttribute('aria-expanded', 'false');
    menuButton.addEventListener('click', function (event) {
      event.preventDefault();
      event.stopPropagation();
      setMenu(!menu.classList.contains('open'));
    });

    document.addEventListener('click', function (event) {
      if (!menu.classList.contains('open')) return;
      if (!menu.contains(event.target) && event.target !== menuButton) setMenu(false);
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') setMenu(false);
    });
  }

  if (nav) {
    var ticking = false;
    function updateNav() {
      nav.classList.toggle('scrolled', (window.scrollY || window.pageYOffset) > 8);
      ticking = false;
    }
    window.addEventListener('scroll', function () {
      if (!ticking) {
        window.requestAnimationFrame(updateNav);
        ticking = true;
      }
    }, { passive: true });
    updateNav();
  }
})();
