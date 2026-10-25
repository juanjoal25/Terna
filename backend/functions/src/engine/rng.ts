/** Generador aleatorio inyectable: devuelve un número en [0, 1). */
export type Rng = () => number;

/**
 * PRNG determinista (mulberry32). Con la misma semilla se obtiene la misma partida,
 * lo que permite reproducir bugs reportados. En producción la semilla sale de
 * `crypto.randomInt` y se guarda en el nodo secreto de la partida.
 */
export function seededRng(seed: number): Rng {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Fisher–Yates sobre una copia del arreglo. */
export function shuffle<T>(items: readonly T[], rng: Rng): T[] {
  const out = [...items];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [out[i], out[j]] = [out[j]!, out[i]!];
  }
  return out;
}
