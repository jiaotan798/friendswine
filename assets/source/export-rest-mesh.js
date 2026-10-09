// Run as a Blockbench execute_script function body. Does not change the model.
if (Mode.selected?.id !== 'edit' || Timeline.playing || Timeline.time !== 0) throw new Error('Expected stopped edit mode at time zero');
const root = Group.all.find(g => g.name === 'root');
if (!root || root.origin.join(',') !== '0,0,1' || root.mesh.scale.toArray().join(',') !== '1,1,1') throw new Error('Unexpected root rest pose');
const vertices = [];
let cubeCount = 0;
const clean = n => Number(n.toFixed(8));
for (const cube of Cube.all) {
  const mesh = cube.mesh;
  let shown = cube.visibility !== false;
  for (let object = mesh; object; object = object.parent) shown = shown && object.visible !== false;
  if (!shown) continue;
  mesh.updateWorldMatrix(true, false);
  const geometry = mesh.geometry;
  const position = geometry.getAttribute('position'), uv = geometry.getAttribute('uv'), normal = geometry.getAttribute('normal');
  const index = geometry.index;
  if (!position || !uv || !normal || !index) throw new Error('Missing geometry attributes: ' + cube.name);
  const normalMatrix = new THREE.Matrix3().getNormalMatrix(mesh.matrixWorld);
  const groups = geometry.groups.length ? geometry.groups : [{start:0, count:index.count, materialIndex:0}];
  for (const group of groups) {
    const material = Array.isArray(mesh.material) ? mesh.material[group.materialIndex] : mesh.material;
    if (!material || material.visible === false) continue;
    if (material.map !== Texture.all[0].getMaterial().map || material.map.flipY !== true) throw new Error('Unexpected material or texture orientation');
    const start = Math.max(group.start, geometry.drawRange.start || 0);
    const end = Math.min(index.count, group.start + group.count, (geometry.drawRange.start || 0) + geometry.drawRange.count);
    for (let i = start; i < end; i++) {
      const vertexIndex = index.getX(i);
      const p = new THREE.Vector3().fromBufferAttribute(position, vertexIndex).applyMatrix4(mesh.matrixWorld);
      const n = new THREE.Vector3().fromBufferAttribute(normal, vertexIndex).applyNormalMatrix(normalMatrix);
      vertices.push([p.x,p.y,p.z,uv.getX(vertexIndex),1-uv.getY(vertexIndex),n.x,n.y,n.z].map(clean));
    }
  }
  cubeCount++;
}
return {model:{texture:'friendswine:textures/entity/doll.png',vertices},metadata:{cubeCount,vertexCount:vertices.length,triangleCount:vertices.length/3,rootPivot:root.origin.slice(),units:'Blockbench pixels',uvOrigin:'top-left',textureSize:[Texture.all[0].width,Texture.all[0].height],materialSide:'DoubleSide'}};

