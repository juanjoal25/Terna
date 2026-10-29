import { randomInt } from "node:crypto";

/** Generador aleatorio inyectable: devuelve un número en [0, 1). */
export type Rng = () => number;

/**
 * PRNG determinista (mulberry32). Con la misma semilla se obtiene la misma partida,
 * lo que permite reproducir bugs en las pruebas. Nunca se usa en producción
 * (ver `secureRng`).
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

/**
 * Generador para partidas reales: usa el generador criptográfico del sistema.
 *
 * Nunca se debe barajar con `seededRng` en producción: su estado interno son solo 32 bits, y
 * quien conozca su propia mano podría probar todas las semillas y deducir las manos de los demás.
 */
export function secureRng(): Rng {
  // `randomInt` admite rangos de hasta 2^48 − 1, así que se piden 47 bits (de sobra para barajar).
  return () => randomInt(0, SECURE_RANGE) / SECURE_RANGE;
}

const SECURE_RANGE = 2 ** 47;

/** Fisher–Yates sobre una copia del arreglo. */
export function shuffle<T>(items: readonly T[], rng: Rng): T[] {
  const out = [...items];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [out[i], out[j]] = [out[j]!, out[i]!];
  }
  return out;
}
