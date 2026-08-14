/**
 * 赛博朋克粒子系统 - 最终版
 * 200粒子，适中亮度，快速移动
 */
(function(){var c=document.getElementById("particles-canvas");if(!c)return;var x=c.getContext("2d"),W,H,P=[],mx=-1e3,my=-1e3,N=200;
function R(){W=c.width=window.innerWidth;H=c.height=window.innerHeight}
function S(){this.r(true)}
S.prototype.r=function(i){this.x=i?Math.random()*W:Math.random()*W;this.y=i?Math.random()*H:Math.random()*H;this.s=Math.random()*2+0.3;this.vx=(Math.random()-0.5)*1.2;this.vy=(Math.random()-0.5)*1.2;this.o=Math.random()*0.22+0.06;this.cc=Math.random()>0.55?"0,210,255":"255,0,127"}
S.prototype.u=function(){var dx=mx-this.x,dy=my-this.y,dist=Math.sqrt(dx*dx+dy*dy);if(dist<250){var f=(250-dist)/250*0.05;this.vx+=dx*f*0.015;this.vy+=dy*f*0.015}this.x+=this.vx;this.y+=this.vy;this.vx*=0.99;this.vy*=0.99;if(this.x<-20)this.x=W+20;if(this.x>W+20)this.x=-20;if(this.y<-20)this.y=H+20;if(this.y>H+20)this.y=-20}
S.prototype.d=function(x){x.beginPath();x.arc(this.x,this.y,this.s,0,2*Math.PI);x.fillStyle="rgba("+this.cc+","+this.o+")";x.fill();if(this.s>1.5){x.beginPath();x.arc(this.x,this.y,this.s*2.5,0,2*Math.PI);x.fillStyle="rgba("+this.cc+","+this.o*0.3+")";x.fill()}}
function L(){for(var i=0;i<P.length;i++)for(var j=i+1;j<P.length;j++){var dx=P[i].x-P[j].x,dy=P[i].y-P[j].y,dist=Math.sqrt(dx*dx+dy*dy);if(dist<150){var a=(1-dist/150)*0.06;x.beginPath();x.moveTo(P[i].x,P[i].y);x.lineTo(P[j].x,P[j].y);x.strokeStyle="rgba(0,210,255,"+a+")";x.lineWidth=0.4;x.stroke()}}}
function A(){x.clearRect(0,0,W,H);for(var i=0;i<P.length;i++){P[i].u();P[i].d(x)}L();requestAnimationFrame(A)}
document.addEventListener("mousemove",function(e){mx=e.clientX;my=e.clientY});document.addEventListener("touchmove",function(e){if(e.touches.length){mx=e.touches[0].clientX;my=e.touches[0].clientY}},{passive:true});window.addEventListener("resize",R);R();for(var i=0;i<N;i++)P.push(new S());A()})();
