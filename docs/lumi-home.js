/* Lumi homepage: scroll-scrubbed pinned scenes + pointer interaction. */
(function(){'use strict';
var root=document.documentElement,doc=document;
var reduce=matchMedia('(prefers-reduced-motion: reduce)').matches;
var finePointer=matchMedia('(hover: hover) and (pointer: fine)').matches;
var narrowQ=matchMedia('(max-width: 834px)');
var nav=doc.querySelector('.top-nav');

function clamp(v,a,b){return v<a?a:v>b?b:v;}
function range(p,a,b){return clamp((p-a)/(b-a),0,1);}
function lerp(a,b,t){return a+(b-a)*t;}
function ease(t){return t<.5?4*t*t*t:1-Math.pow(-2*t+2,3)/2;}
function easeOut(t){return 1-Math.pow(1-t,3);}

/* ── Split manifesto into per-character / per-word spans (CSS does the fade) ── */
doc.querySelectorAll('[data-words]').forEach(function(el){
  var i=0;
  function split(node){
    Array.prototype.slice.call(node.childNodes).forEach(function(child){
      if(child.nodeType===3){
        var frag=doc.createDocumentFragment();
        (child.textContent.match(/[A-Za-z0-9]+|\s+|[^\sA-Za-z0-9]/g)||[]).forEach(function(tok){
          if(/^\s+$/.test(tok)){frag.appendChild(doc.createTextNode(tok));return;}
          var s=doc.createElement('span');s.className='w';s.style.setProperty('--i',i++);s.textContent=tok;frag.appendChild(s);
        });
        node.replaceChild(frag,child);
      }else if(child.nodeType===1)split(child);
    });
  }
  split(el);el.style.setProperty('--n',i);el.setAttribute('aria-label',el.textContent);
});

/* ── Privacy stats roll down to zero ── */
function rollStats(){
  var stats=doc.querySelectorAll('b[data-from]');
  if(reduce||!('IntersectionObserver' in window))return;
  stats.forEach(function(b){b.textContent=b.dataset.from;});
  var io=new IntersectionObserver(function(entries){entries.forEach(function(en){
    if(!en.isIntersecting)return;io.unobserve(en.target);
    var b=en.target,from=+b.dataset.from,to=+(b.dataset.to||0),t0=performance.now(),dur=1600+Math.random()*400;
    (function step(now){var t=clamp((now-t0)/dur,0,1);b.textContent=Math.round(lerp(from,to,easeOut(t)));if(t<1)requestAnimationFrame(step);})(t0);
  });},{threshold:.6});
  stats.forEach(function(b){io.observe(b);});
}
rollStats();

/* ── Bento cards: 3D tilt + cursor spotlight ── */
if(finePointer&&!reduce){
  doc.querySelectorAll('.card').forEach(function(card){
    card.addEventListener('pointermove',function(e){
      var r=card.getBoundingClientRect(),x=(e.clientX-r.left)/r.width,y=(e.clientY-r.top)/r.height;
      card.classList.add('is-hover');
      card.style.setProperty('--mx',(x*100).toFixed(1)+'%');card.style.setProperty('--my',(y*100).toFixed(1)+'%');
      card.style.setProperty('--rx',((.5-y)*6).toFixed(2)+'deg');card.style.setProperty('--ry',((x-.5)*8).toFixed(2)+'deg');
    });
    card.addEventListener('pointerleave',function(){card.classList.remove('is-hover');card.style.setProperty('--rx','0deg');card.style.setProperty('--ry','0deg');});
  });
}

/* ── Nav theme follows the section under it ── */
var themed=[],fanTheme='dark';
function navTheme(y){
  var probe=y+24,theme='dark';
  for(var k=0;k<themed.length;k++){var t=themed[k];if(probe>=t.top&&probe<t.bottom){theme=t.fan?fanTheme:t.el.dataset.theme;break;}}
  if(nav)nav.classList.toggle('nav-dark',theme==='dark');
}

if(reduce){
  /* Static layout: still keep the nav colour in sync. */
  var measureStatic=function(){themed=[].map.call(doc.querySelectorAll('[data-theme]'),function(el){var r=el.getBoundingClientRect();return{el:el,top:r.top+scrollY,bottom:r.bottom+scrollY};});navTheme(scrollY);};
  addEventListener('scroll',function(){navTheme(scrollY);},{passive:true});addEventListener('resize',measureStatic);addEventListener('load',measureStatic);measureStatic();
  return;
}
root.classList.add('motion');

/* ── Pointer (smoothed) ── */
var ptr={x:0,y:0,tx:0,ty:0};
if(finePointer){
  addEventListener('pointermove',function(e){ptr.tx=e.clientX/innerWidth*2-1;ptr.ty=e.clientY/innerHeight*2-1;request();},{passive:true});
  doc.addEventListener('pointerleave',function(){ptr.tx=0;ptr.ty=0;request();});
}

/* ── Scenes ── */
function q(s,c){return (c||doc).querySelector(s);}
function qa(s,c){return Array.prototype.slice.call((c||doc).querySelectorAll(s));}
var vw=innerWidth,vh=innerHeight,narrow=narrowQ.matches;

function tiltOf(el){return function(){if(!el)return;el.style.transform='rotateX('+(-ptr.y*6).toFixed(2)+'deg) rotateY('+(ptr.x*9).toFixed(2)+'deg)';};}
function glare(phone){phone.style.setProperty('--gx',(60-ptr.x*30).toFixed(1)+'%');}

var builders={
  hero:function(el){
    var title=q('.hero-title',el),phone=q('.hero-phone',el),shots=qa('.phone__screen img',phone),end=q('.hero-end',el),hint=q('.scroll-hint',el),glow=q('.hero-glow',el),tilt=tiltOf(q('[data-tilt]',el));
    return function(p){
      var t1=ease(range(p,0,.3)),t2=easeOut(range(p,.04,.5)),t3=ease(range(p,.52,.64)),t4=ease(range(p,.66,.92));
      title.style.opacity=(1-t1).toFixed(3);
      title.style.transform='translate3d(0,'+(-t1*vh*.1).toFixed(1)+'px,0) scale('+(1-t1*.22).toFixed(4)+')';
      var ty=lerp(vh*.72,0,t2),rx=(1-t2)*58,s=lerp(1.08,1,t2),tx=0;
      if(narrow){ty-=t4*vh*.16;s*=1-t4*.36;}else{tx=-t4*Math.min(vw*.2,300);s*=1-t4*.06;}
      phone.style.transform='translate3d('+tx.toFixed(1)+'px,'+ty.toFixed(1)+'px,0) rotateX('+rx.toFixed(2)+'deg) scale('+s.toFixed(4)+')';
      shots[1].style.opacity=t3.toFixed(3);
      end.style.opacity=t4.toFixed(3);
      end.style.transform=narrow?'translate3d(-50%,'+((1-t4)*30).toFixed(1)+'px,0)':'translate3d('+((1-t4)*40).toFixed(1)+'px,-50%,0)';
      end.style.pointerEvents=t4>.5?'auto':'none';
      hint.style.opacity=(1-range(p,0,.06)).toFixed(3);
      glow.style.opacity=(.35+t2*.65-t4*.3).toFixed(3);
      glow.style.transform='translate(calc(-50% + '+tx.toFixed(1)+'px),-50%) scale('+(.8+t2*.3).toFixed(3)+')';
      tilt();glare(phone);
    };
  },
  words:function(el){
    var w=q('[data-words]',el);
    return function(p){w.style.setProperty('--p',range(p,.08,.82).toFixed(4));};
  },
  carousel:function(el){
    var steps=qa('.step',el),phone=q('.carousel-phone',el),shots=qa('.phone__screen img',phone),bars=qa('.progress i',el),N=steps.length,tilt=tiltOf(q('[data-tilt]',el));
    return function(p){
      var s=range(p,.03,.97)*N;
      for(var i=0;i<N;i++){
        var d=s-(i+.5);if(i===0&&d<0)d=0;if(i===N-1&&d>0)d=0;
        var op=clamp((.62-Math.abs(d))/.24,0,1);
        shots[i].style.opacity=op.toFixed(3);
        steps[i].style.opacity=op.toFixed(3);
        steps[i].style.transform='translate3d(0,'+(-d*48).toFixed(1)+'px,0)';
        steps[i].style.visibility=op>0?'visible':'hidden';
        bars[i].style.setProperty('--f',clamp(s-i,0,1).toFixed(3));
      }
      var ry=Math.sin(s*Math.PI)*-10,enter=easeOut(range(p,0,.06));
      phone.style.transform='translate3d(0,'+((1-enter)*vh*.08).toFixed(1)+'px,0) rotateY('+ry.toFixed(2)+'deg) rotateZ('+(Math.sin(s*Math.PI*2)*1.2).toFixed(2)+'deg)';
      tilt();glare(phone);
    };
  },
  card:function(el){
    var title=q('.pcard-title',el),card=q('.pcard',el),end=q('.pcard-end',el),glow=q('.hero-glow',el),hint=q('.scroll-hint',el),tilt=tiltOf(q('[data-tilt]',el));
    return function(p){
      var t1=ease(range(p,0,.28)),t2=easeOut(range(p,.02,.55)),t4=ease(range(p,.6,.9));
      title.style.opacity=(1-t1).toFixed(3);
      title.style.transform='translate3d(0,'+(-t1*vh*.12).toFixed(1)+'px,0) scale('+(1-t1*.15).toFixed(4)+')';
      var ty=lerp(vh*.55,0,t2)-(narrow?t4*vh*.14:0),rx=(1-t2)*62,ry=(1-t2)*-38+t4*(narrow?0:-12),rz=(1-t2)*14,s=lerp(.8,1,t2)*(narrow?1-t4*.12:1),tx=narrow?0:-t4*Math.min(vw*.18,260);
      card.style.transform='translate3d('+tx.toFixed(1)+'px,'+ty.toFixed(1)+'px,0) rotateX('+rx.toFixed(2)+'deg) rotateY('+ry.toFixed(2)+'deg) rotateZ('+rz.toFixed(2)+'deg) scale('+s.toFixed(4)+')';
      card.style.setProperty('--sheen',(120-t2*80-ptr.x*25).toFixed(1)+'%');
      end.style.opacity=t4.toFixed(3);
      end.style.transform=narrow?'translate3d(-50%,'+((1-t4)*30).toFixed(1)+'px,0)':'translate3d('+((1-t4)*40).toFixed(1)+'px,-50%,0)';
      end.style.pointerEvents=t4>.5?'auto':'none';
      if(hint)hint.style.opacity=(1-range(p,0,.06)).toFixed(3);
      if(glow)glow.style.opacity=(.3+t2*.7).toFixed(3);
      tilt();
    };
  },
  fan:function(el){
    var bg=q('.fan-bg',el),copy=q('.fan-copy',el),phones=qa('.fan-phone',el);
    var data=phones.map(function(ph){return{el:ph,j:+ph.dataset.fan};});
    return function(p){
      var zoom=ease(range(p,0,.32)),k=ease(range(p,.22,.62)),b=range(p,.4,.66),c=ease(range(p,.56,.84));
      bg.style.opacity=(1-b).toFixed(3);
      fanTheme=b>.5?'light':'dark';el.classList.toggle('is-light',b>.5);
      copy.style.opacity=c.toFixed(3);copy.style.transform='translate3d(0,'+((1-c)*40).toFixed(1)+'px,0)';
      var spacing=narrow?Math.min(vw*.3,170):Math.min(vw*.17,240),drop=c*vh*.04;
      data.forEach(function(d){
        var j=d.j,a=Math.abs(j),x=j*k*spacing+ptr.x*(8+a*10),y=drop+a*k*vh*.03-ptr.y*(4+a*4);
        var ry=-j*k*16+ptr.x*5,z=-a*k*140,s=j===0?lerp(1.75,1,zoom):lerp(.82,.9,k);
        d.el.style.transform='translate3d('+x.toFixed(1)+'px,'+y.toFixed(1)+'px,'+z.toFixed(1)+'px) rotateY('+ry.toFixed(2)+'deg) scale('+s.toFixed(4)+')';
        d.el.style.opacity=j===0?1:clamp(k*2.2,0,1).toFixed(3);
        d.el.style.zIndex=10-a;
        glare(d.el);
      });
    };
  }
};

var scenes=qa('[data-scene]').map(function(el){var b=builders[el.dataset.scene];return b?{el:el,update:b(el),top:0,h:0}:null;}).filter(Boolean);

function measure(){
  vw=innerWidth;vh=innerHeight;narrow=narrowQ.matches;
  var y=scrollY;
  scenes.forEach(function(s){var r=s.el.getBoundingClientRect();s.top=r.top+y;s.h=s.el.offsetHeight;});
  themed=qa('[data-theme]').map(function(el){var r=el.getBoundingClientRect();return{el:el,top:r.top+y,bottom:r.bottom+y,fan:el.dataset.scene==='fan'};});
  request(true);
}

var queued=false,force=false;
function request(all){if(all)force=true;if(!queued){queued=true;requestAnimationFrame(frame);}}
function frame(){
  queued=false;
  var y=scrollY;
  ptr.x+=(ptr.tx-ptr.x)*.08;ptr.y+=(ptr.ty-ptr.y)*.08;
  var moving=Math.abs(ptr.tx-ptr.x)>.002||Math.abs(ptr.ty-ptr.y)>.002;
  scenes.forEach(function(s){
    var visible=y+vh>s.top&&y<s.top+s.h;
    if(!visible&&!force)return;
    s.update(clamp((y-s.top)/Math.max(1,s.h-vh),0,1));
  });
  force=false;
  navTheme(y);
  if(moving)request();
}

addEventListener('scroll',function(){request();},{passive:true});
addEventListener('resize',measure);
addEventListener('load',measure);
if('ResizeObserver' in window)new ResizeObserver(function(){measure();}).observe(doc.body);
narrowQ.addEventListener&&narrowQ.addEventListener('change',measure);
measure();
})();
