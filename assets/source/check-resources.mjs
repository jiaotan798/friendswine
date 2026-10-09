/** 校验生产模型、贴图、音频与随仓库保存的原始素材是否一致。 */
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const source = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(source, '../..');
const assets = path.join(root, 'mod/src/main/resources/assets/friendswine');
const model = JSON.parse(fs.readFileSync(path.join(assets, 'models/entity/doll.json'), 'utf8'));
const original = JSON.parse(fs.readFileSync(path.join(source, 'doll.bbmodel'), 'utf8'));
assert.equal(model.texture, 'friendswine:textures/entity/doll.png');
assert.equal(model.vertices.length, 1836);
assert.equal(model.vertices.length % 3, 0);
let reversed = 0, degenerate = 0;
for (const v of model.vertices) {
  assert.equal(v.length, 8);
  assert(v.every(Number.isFinite));
  assert(v[3] >= 0 && v[3] <= 1 && v[4] >= 0 && v[4] <= 1, 'UV outside the texture');
  assert(Math.abs(Math.hypot(...v.slice(5)) - 1) < 0.000001, 'Normal is not unit length');
}
for (let i = 0; i < model.vertices.length; i += 3) {
  const [a,b,c] = model.vertices.slice(i,i+3);
  const ab=b.slice(0,3).map((x,k)=>x-a[k]), ac=c.slice(0,3).map((x,k)=>x-a[k]);
  const cross=[ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0]];
  if (Math.hypot(...cross) < 0.00000001) degenerate++;
  else if (cross.reduce((sum,x,k)=>sum+x*a[k+5],0) < 0) reversed++;
}
assert.equal(reversed, 0, 'Triangle winding contradicts normal');

const particle = JSON.parse(fs.readFileSync(path.join(assets, 'models/block/doll.json'), 'utf8')).textures.particle;
// 灰色羊毛由原版方块图集提供，不需要注册自定义破坏粒子贴图。
assert.equal(particle, 'minecraft:block/gray_wool');

const png = fs.readFileSync(path.join(assets, 'textures/entity/doll.png'));
const remoteModel = JSON.parse(fs.readFileSync(path.join(assets, 'models/item/remote.json'), 'utf8'));
assert.equal(remoteModel.parent, 'minecraft:item/generated');
assert.equal(remoteModel.textures.layer0, 'friendswine:item/remote');
const remotePng = fs.readFileSync(path.join(assets, 'textures/item/remote.png'));
assert.equal(remotePng.readUInt32BE(16), 16);
assert.equal(remotePng.readUInt32BE(20), 16);
const wineModel = JSON.parse(fs.readFileSync(path.join(assets, 'models/item/wine.json'), 'utf8'));
assert.equal(wineModel.parent, 'minecraft:item/generated');
assert.equal(wineModel.textures.layer0, 'friendswine:item/wine');
for (const sprite of ['textures/item/wine.png', 'textures/mob_effect/tipsy.png']) {
  const data = fs.readFileSync(path.join(assets, sprite));
  assert.equal(data.readUInt32BE(16), 16);
  assert.equal(data.readUInt32BE(20), 16);
}
const zh = JSON.parse(fs.readFileSync(path.join(assets, 'lang/zh_cn.json'), 'utf8'));
assert.equal(zh['item.friendswine.wine'], '朋友的酒');
assert.equal(png.readUInt32BE(16), 64);
assert.equal(png.readUInt32BE(20), 64);
assert.deepEqual(png, Buffer.from(original.textures[0].source.split(',')[1], 'base64'));
// 解码器由环境提供，避免检查工具依赖开发者工作区的缓存位置。
const ffmpeg = process.env.FFMPEG || 'ffmpeg';
const rgba = execFileSync(ffmpeg, ['-v','error','-i',path.join(assets,'textures/entity/doll.png'),'-f','rawvideo','-pix_fmt','rgba','pipe:1'], {maxBuffer:1048576});
const alpha = {};
for (let i=3; i<rgba.length; i+=4) alpha[rgba[i]]=(alpha[rgba[i]]||0)+1;

const ogg = fs.readFileSync(path.join(assets, 'sounds/doll_music.ogg'));
let offset=0, lastGranule=0n, packetParts=[], identification;
while(offset<ogg.length) {
  assert.equal(ogg.toString('ascii',offset,offset+4),'OggS');
  const granule=ogg.readBigUInt64LE(offset+6);
  if(granule!==0xffffffffffffffffn) lastGranule=granule;
  const segmentCount=ogg[offset+26];
  const sizes=ogg.subarray(offset+27,offset+27+segmentCount);
  let body=offset+27+segmentCount;
  for(const size of sizes) {
    if(!identification) {
      packetParts.push(ogg.subarray(body,body+size));
      if(size<255) identification=Buffer.concat(packetParts);
    }
    body+=size;
  }
  offset=body;
}
assert.equal(identification[0],1);
assert.equal(identification.toString('ascii',1,7),'vorbis');
assert.equal(identification[11],1,'Audio must be mono');
assert.equal(identification.readUInt32LE(12),44100);
assert.equal(lastGranule,2482560n,'Audio duration differs from the full input');
const samples=JSON.parse(fs.readFileSync(path.join(source,'animation-samples.json'),'utf8'));
assert.equal(samples.length,0.91667);
assert.deepEqual(samples.restored.rootScale,[1,1,1]);
const sound=JSON.parse(fs.readFileSync(path.join(assets,'sounds.json'),'utf8')).doll_music.sounds[0];
assert.equal(sound.name,'friendswine:doll_music');
assert.equal(sound.stream,true);
assert.equal(sound.attenuation_distance,20);
for (const key of ['compressionPercent', 'widthPercent', 'rotationSpeed']) {
  assert(zh[`friendswine.config.${key}`]);
  assert(zh[`friendswine.config.${key}.tooltip`]);
}
const sha=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const result={
  vertices:model.vertices.length, triangles:model.vertices.length/3, reversed, degenerate,
  remoteTexture:{size:[16,16],sha256:sha(path.join(assets,'textures/item/remote.png'))},
  wineTexture:{size:[16,16],sha256:sha(path.join(assets,'textures/item/wine.png'))},
  blockParticle:particle,
  bounds:[0,1,2].map(k=>[Math.min(...model.vertices.map(v=>v[k])),Math.max(...model.vertices.map(v=>v[k]))]),
  texture:{size:[64,64],alphaPixels:alpha,uvOrigin:'top-left',sha256:sha(path.join(assets,'textures/entity/doll.png'))},
  audio:{codec:'vorbis',channels:identification[11],sampleRate:44100,sampleFrames:Number(lastGranule),seconds:Number(lastGranule)/44100,sha256:sha(path.join(assets,'sounds/doll_music.ogg'))},
  sourceModelSha256:sha(path.join(source,'doll.bbmodel')),
  sourceAudioSha256:sha(path.join(source,'doll_music.mp3'))
};
console.log(JSON.stringify(result,null,2));
