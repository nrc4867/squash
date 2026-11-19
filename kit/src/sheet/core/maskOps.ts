import { blankMask, components, type Mask } from "./mask.ts";

/** The pixels set in both masks. */
export function intersect(a: Mask, b: Mask): Mask {
  const out = blankMask(a.width, a.height);
  for (let i = 0; i < out.data.length; i++) out.data[i] = a.data[i] & b.data[i];
  return out;
}

/** The pixels set in either mask. */
export function union(a: Mask, b: Mask): Mask {
  const out = blankMask(a.width, a.height);
  for (let i = 0; i < out.data.length; i++) out.data[i] = a.data[i] | b.data[i];
  return out;
}

/** The pixels of `a` that `b` does not hold. */
export function subtract(a: Mask, b: Mask): Mask {
  const out = blankMask(a.width, a.height);
  for (let i = 0; i < out.data.length; i++) out.data[i] = a.data[i] & (b.data[i] ^ 1);
  return out;
}

/** The mask with its holes of at most `maxArea` pixels filled: background not joined to the mask's border through background. */
export function fillHoles(mask: Mask, maxArea = Infinity): Mask {
  const { width, height, data } = mask;
  const outside = new Uint8Array(width * height);
  const stack: number[] = [];
  const push = (x: number, y: number) => {
    const at = y * width + x;
    if (data[at] || outside[at]) return;
    outside[at] = 1;
    stack.push(at);
  };
  for (let x = 0; x < width; x++) {
    push(x, 0);
    push(x, height - 1);
  }
  for (let y = 0; y < height; y++) {
    push(0, y);
    push(width - 1, y);
  }
  while (stack.length > 0) {
    const at = stack.pop()!;
    const x = at % width;
    const y = (at - x) / width;
    if (x > 0) push(x - 1, y);
    if (x < width - 1) push(x + 1, y);
    if (y > 0) push(x, y - 1);
    if (y < height - 1) push(x, y + 1);
  }
  const out = blankMask(width, height);
  out.data.set(data);
  if (maxArea === Infinity) {
    for (let i = 0; i < out.data.length; i++) out.data[i] = outside[i] ? 0 : 1;
    return out;
  }
  const holes = blankMask(width, height);
  for (let i = 0; i < holes.data.length; i++) holes.data[i] = !data[i] && !outside[i] ? 1 : 0;
  for (const piece of components(holes).pieces) if (piece.area <= maxArea) for (const at of piece.pixels) out.data[at] = 1;
  return out;
}

/** `seed` grown by `steps` pixels, but only through the pixels `within` holds. */
export function growWithin(seed: Mask, within: Mask, steps: number): Mask {
  const { width, height } = seed;
  let now = Uint8Array.from(seed.data);
  for (let s = 0; s < steps; s++) {
    const next = Uint8Array.from(now);
    for (let y = 0; y < height; y++) {
      for (let x = 0; x < width; x++) {
        const at = y * width + x;
        if (now[at] || !within.data[at]) continue;
        let near = false;
        for (let dy = -1; dy <= 1 && !near; dy++) {
          const ny = y + dy;
          if (ny < 0 || ny >= height) continue;
          for (let dx = -1; dx <= 1; dx++) {
            const nx = x + dx;
            if (nx >= 0 && nx < width && now[ny * width + nx]) {
              near = true;
              break;
            }
          }
        }
        if (near) next[at] = 1;
      }
    }
    now = next;
  }
  return { width, height, data: now };
}

/** The mask made of the given pixels (indices) of a mask of the same size. */
export function maskFromIndices(width: number, height: number, indices: ArrayLike<number>): Mask {
  const out = blankMask(width, height);
  for (let i = 0; i < indices.length; i++) out.data[indices[i]] = 1;
  return out;
}

/** The number of set pixels of `mask` inside the box `x, y, w, h` of its own pixels. */
export function countIn(mask: Mask, x0: number, y0: number, w: number, h: number): number {
  let n = 0;
  for (let y = Math.max(0, y0); y < Math.min(mask.height, y0 + h); y++) for (let x = Math.max(0, x0); x < Math.min(mask.width, x0 + w); x++) n += mask.data[y * mask.width + x];
  return n;
}
