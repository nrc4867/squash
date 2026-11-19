/** Solves the square system `a x = b` by Gaussian elimination with partial pivoting; a singular column gets 0. */
export function solveLinear(a: number[][], b: number[]): number[] {
  const n = b.length;
  const m = a.map((row, i) => [...row, b[i]]);
  for (let col = 0; col < n; col++) {
    let pivot = col;
    for (let row = col + 1; row < n; row++) if (Math.abs(m[row][col]) > Math.abs(m[pivot][col])) pivot = row;
    [m[col], m[pivot]] = [m[pivot], m[col]];
    if (Math.abs(m[col][col]) < 1e-12) continue;
    for (let row = 0; row < n; row++) {
      if (row === col) continue;
      const f = m[row][col] / m[col][col];
      for (let k = col; k <= n; k++) m[row][k] -= f * m[col][k];
    }
  }
  return m.map((row, i) => (Math.abs(row[i]) < 1e-12 ? 0 : row[n] / row[i]));
}

/** The least-squares weights of `features` that best give `targets`, one feature row a sample. */
export function leastSquares(features: number[][], targets: number[]): number[] {
  const k = features[0]?.length ?? 0;
  const ata = Array.from({ length: k }, () => new Array<number>(k).fill(0));
  const atb = new Array<number>(k).fill(0);
  features.forEach((row, s) => {
    for (let i = 0; i < k; i++) {
      atb[i] += row[i] * targets[s];
      for (let j = 0; j < k; j++) ata[i][j] += row[i] * row[j];
    }
  });
  return solveLinear(ata, atb);
}

/** The middle value; 0 for none. */
export function median(values: readonly number[]): number {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = sorted.length >> 1;
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}

export function clamp(value: number, low: number, high: number): number {
  return Math.min(high, Math.max(low, value));
}
