import { describe, expect, it } from "vitest";
import { secureRng, seededRng, shuffle } from "./rng.js";

describe("secureRng (el generador real de producción)", () => {
  it("devuelve números en [0, 1) sin lanzar errores", () => {
    const rng = secureRng();
    for (let i = 0; i < 1000; i++) {
      const value = rng();
      expect(value).toBeGreaterThanOrEqual(0);
      expect(value).toBeLessThan(1);
    }
  });

  it("baraja sin perder ni duplicar cartas", () => {
    const cards = Array.from({ length: 36 }, (_, i) => i);
    const shuffled = shuffle(cards, secureRng());
    expect([...shuffled].sort((a, b) => a - b)).toEqual(cards);
  });
});

describe("seededRng", () => {
  it("con la misma semilla produce la misma secuencia", () => {
    const a = seededRng(42);
    const b = seededRng(42);
    expect([a(), a(), a()]).toEqual([b(), b(), b()]);
  });
});
