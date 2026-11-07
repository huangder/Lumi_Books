(function(){'use strict';
document.documentElement.classList.add('js');
var nav=document.querySelector('.top-nav'),toggle=document.querySelector('.nav-more-btn'),menu=document.querySelector('.nav-menu,.nav-more-dropdown');
function setMenu(open){if(!toggle||!menu)return;menu.classList.toggle('open',open);toggle.setAttribute('aria-expanded',String(open));}
if(toggle&&menu){
  toggle.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();setMenu(!menu.classList.contains('open'));});
  document.addEventListener('click',function(e){if(menu.classList.contains('open')&&!menu.contains(e.target)&&e.target!==toggle)setMenu(false);});
  document.addEventListener('keydown',function(e){if(e.key==='Escape')setMenu(false);});
  menu.querySelectorAll('a').forEach(function(a){a.addEventListener('click',function(){setMenu(false);});});
}
if(nav){var ticking=false;window.addEventListener('scroll',function(){if(!ticking){requestAnimationFrame(function(){nav.classList.toggle('scrolled',window.scrollY>8);ticking=false;});ticking=true;}},{passive:true});}
/* Download chooser: the nav "获取 Lumi" button opens a dialog; its href stays as the no-JS fallback. */
var DL=[
  {href:'https://github.com/huangder/Lumi_Books/releases',name:'GitHub Releases',note:'官方发布页 · 版本最新，推荐',primary:true},
  {href:'https://pan.baidu.com/s/1vDdFsoqQuuZlntUTUjssZw?pwd=lumi',name:'百度网盘',note:'提取码 lumi'},
  {href:'https://wwaug.lanzoub.com/b0xxfmxvi',name:'蓝奏云',note:'密码 lumi'},
  {href:'https://gitee.com/huangder/lumi_-books_-gitee',name:'Gitee',note:'国内镜像'}
];
var dlg=null,lastFocus=null;
function buildDialog(){
  dlg=document.createElement('dialog');dlg.className='dl-dialog';dlg.setAttribute('aria-labelledby','dl-title');
  dlg.innerHTML='<div class="dl-sheet"><button type="button" class="dl-close" aria-label="关闭">×</button>'+
    '<img class="dl-icon" src="media/lumi2.webp" alt=""><h2 id="dl-title">获取 Lumi</h2><p class="dl-sub">适用于 Android 8.0 及以上 · 免费、开源、无广告</p>'+
    '<ul class="dl-list">'+DL.map(function(d){return '<li><a class="dl-item'+(d.primary?' is-primary':'')+'" href="'+d.href+'" target="_blank" rel="noopener">'+
      '<span><strong>'+d.name+'</strong><small>'+d.note+'</small></span><i aria-hidden="true">↗</i></a></li>';}).join('')+'</ul>'+
    '<p class="dl-foot">GitHub 打不开时，可以使用其他下载渠道，安装包内容相同。</p></div>';
  document.body.appendChild(dlg);
  dlg.querySelector('.dl-close').addEventListener('click',closeDialog);
  dlg.addEventListener('click',function(e){if(e.target===dlg)closeDialog();});
  dlg.addEventListener('close',function(){document.documentElement.classList.remove('dl-lock');if(lastFocus)lastFocus.focus();});
}
function openDialog(e){
  if(typeof HTMLDialogElement!=='function')return; /* very old browsers: follow the link */
  e.preventDefault();if(!dlg)buildDialog();
  lastFocus=document.activeElement;setMenu(false);
  document.documentElement.classList.add('dl-lock');dlg.showModal();
  var first=dlg.querySelector('.dl-item');if(first)first.focus();
}
function closeDialog(){if(dlg&&dlg.open)dlg.close();}
document.querySelectorAll('.btn-nav-download').forEach(function(b){b.addEventListener('click',openDialog);});
/* Small screens hide the nav button, so offer the same chooser from the menu. */
if(menu&&!menu.querySelector('.menu-download')){var md=document.createElement('a');md.className='menu-download';md.href='https://github.com/huangder/Lumi_Books/releases';md.textContent='获取 Lumi';md.addEventListener('click',openDialog);menu.appendChild(md);}

var reveal=document.querySelectorAll('.reveal');
if('IntersectionObserver' in window&&!matchMedia('(prefers-reduced-motion: reduce)').matches){
  var io=new IntersectionObserver(function(entries){entries.forEach(function(entry){if(entry.isIntersecting){entry.target.classList.add('visible');io.unobserve(entry.target);}});},{threshold:.12,rootMargin:'0px 0px -40px'});
  reveal.forEach(function(el){io.observe(el);});
}else reveal.forEach(function(el){el.classList.add('visible');});
})();
