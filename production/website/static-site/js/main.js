/**
 * 懒狗输入法官网 - 主脚本 v8
 * 导航栏 · 语言切换 · 滚动动画 · FAQ
 */
(function(){'use strict';
var Lang={current:'zh',savedKey:'langou-locale',
init:function(){var s=localStorage.getItem(this.savedKey);if(s==='en'||s==='zh'){this.current=s}else if(navigator.language&&!navigator.language.startsWith('zh')){this.current='en'}this.apply()},
toggle:function(){this.current=this.current==='zh'?'en':'zh';localStorage.setItem(this.savedKey,this.current);var p=window.location.pathname;if(this.current==='en'&&!p.includes('/en/')){window.location.href='/en/'}else if(this.current==='zh'&&p.includes('/en/')){window.location.href='/zh/'}},
apply:function(){document.body.classList.toggle('en',this.current==='en');var b=document.querySelector('.lang-btn');if(b)b.textContent=this.current==='zh'?'EN':'中文'},
isEn:function(){return this.current==='en'}};

function initNavbar(){
  var n=document.getElementById('navbar');if(!n)return;
  window.addEventListener('scroll',function(){n.classList.toggle('scrolled',window.scrollY>50)});
  var m=document.querySelector('.mobile-menu'),l=document.querySelector('.nav-links');
  if(m&&l){m.addEventListener('click',function(){l.classList.toggle('open')});
  l.querySelectorAll('a').forEach(function(a){a.addEventListener('click',function(){l.classList.remove('open')})})}
}

function initScroll(){
  document.querySelectorAll('.fade-up').forEach(function(e){e.classList.add('animate')});
  var o=new IntersectionObserver(function(e){e.forEach(function(e){if(e.isIntersecting){e.target.classList.add('visible');o.unobserve(e.target)}})},{threshold:0.1,rootMargin:'0px 0px -30px 0px'});
  document.querySelectorAll('.fade-up.animate').forEach(function(e){o.observe(e)})
}

function initFaq(){
  document.querySelectorAll('.faq-q').forEach(function(q){q.addEventListener('click',function(){
    var was=q.classList.contains('open');document.querySelectorAll('.faq-q').forEach(function(o){o.classList.remove('open')});if(!was)q.classList.add('open')
  })})
}

function detectLang(){if(window.location.pathname.includes('/en/'))Lang.current='en';Lang.apply()}

document.addEventListener('DOMContentLoaded',function(){
  detectLang();initNavbar();initScroll();initFaq();
  document.querySelectorAll('.lang-btn').forEach(function(b){b.addEventListener('click',Lang.toggle.bind(Lang))});
  if(window.scrollY>50&&document.getElementById('navbar'))document.getElementById('navbar').classList.add('scrolled')
});

window.Lang=Lang;
})();
