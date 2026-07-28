(function () {
  'use strict';
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
