import type { DealSpec, RuleSet, TurnOptions } from "./types.js";

/**
 * Reparto oficial de Trio (36 cartas). A más jugadores, menos cartas al centro:
 * 3 → 9, 4 → 8, 5 → 6, 6 → 6.
 */
export const TRIO_DEAL: Record<number, DealSpec> = {
  3: { hand: 9, center: 9 },
  4: { hand: 7, center: 8 },
  5: { hand: 6, center: 6 },
  6: { hand: 5, center: 6 },
};

/** Reglas estrictas del Trio original. */
export const ORIGINAL_OPTIONS: TurnOptions = {
  allowSelfReveal: true,
  allowClaim: false,
  claimRequiresEnd: false,
  claimFailEndsTurn: true,
  allowPass: false,
  minRevealsBeforePass: 1,
  continueAfterSet: false,
  askOncePerTurn: false,
  hideMismatch: false,
  allowMultiClaim: false,
};

/**
 * Reglas de TERNA!: como el original, más "deducir" (pedir un número que esté en la menor o la
 * mayor de otro jugador), y pensadas para que no se regale información:
 *  - la carta que no coincide no se muestra, solo se avisa de que no era la correcta;
 *  - "Pedir carta" solo se puede usar una vez por turno, y siempre saca una sola carta (si un rival tiene dos
 *    iguales, se le pregunta dos veces);
 *  - se puede pasar el turno tras destapar al menos una carta.
 * Cada una es una opción: el anfitrión puede apagarla.
 */
export const TERNA_OPTIONS: TurnOptions = {
  ...ORIGINAL_OPTIONS,
  allowClaim: true,
  allowPass: true,
  askOncePerTurn: true,
  hideMismatch: true,
};

const BASE = {
  version: 1,
  maxNumber: 12,
  copies: 3,
  setSize: 3,
  deal: TRIO_DEAL,
} as const;

/** Modo por defecto de la app: 3 ternas o la terna de 7. */
export const TERNA_CLASSIC: RuleSet = {
  ...BASE,
  id: "TERNA_CLASSIC",
  win: { setsNeeded: 3, instantWinValues: [7], linkedPairTarget: null },
  options: TERNA_OPTIONS,
};

/** Modo Picante: 2 ternas enlazadas (suman o restan 7) o la terna de 7. */
export const TERNA_SPICY: RuleSet = {
  ...BASE,
  id: "TERNA_SPICY",
  win: { setsNeeded: null, instantWinValues: [7], linkedPairTarget: 7 },
  options: TERNA_OPTIONS,
};

/** Trio tal cual el juego de mesa, para quien quiera las reglas puras. */
export const TRIO_ORIGINAL: RuleSet = {
  ...TERNA_CLASSIC,
  id: "TRIO_ORIGINAL",
  options: ORIGINAL_OPTIONS,
};

export const RULESETS: Record<string, RuleSet> = {
  [TERNA_CLASSIC.id]: TERNA_CLASSIC,
  [TERNA_SPICY.id]: TERNA_SPICY,
  [TRIO_ORIGINAL.id]: TRIO_ORIGINAL,
};

/** Aplica la configuración de la sala (casas de reglas) sobre un modo base. */
export function withOptions(ruleSet: RuleSet, overrides: Partial<TurnOptions>): RuleSet {
  return { ...ruleSet, options: { ...ruleSet.options, ...overrides } };
}

/** Lanza un error si el RuleSet es incoherente. Se llama al crear cada partida. */
export function validateRuleSet(ruleSet: RuleSet): void {
  const deckSize = ruleSet.maxNumber * ruleSet.copies;
  const fail = (msg: string): never => {
    throw new Error(`RuleSet ${ruleSet.id} inválido: ${msg}`);
  };

  if (ruleSet.setSize < 2) fail("setSize debe ser al menos 2");
  // Con más copias que tamaño de set podrían quedar cartas sueltas imposibles de agrupar.
  if (ruleSet.copies !== ruleSet.setSize) fail("copies debe ser igual a setSize");
  if (ruleSet.win.setsNeeded === null && ruleSet.win.linkedPairTarget === null && ruleSet.win.instantWinValues.length === 0) {
    fail("no hay ninguna condición de victoria");
  }
  for (const v of ruleSet.win.instantWinValues) {
    if (v < 1 || v > ruleSet.maxNumber) fail(`instantWinValue ${v} fuera de rango`);
  }
  if (ruleSet.options.minRevealsBeforePass < 0) fail("minRevealsBeforePass negativo");

  const counts = Object.keys(ruleSet.deal).map(Number).sort((a, b) => a - b);
  if (counts.length === 0) fail("deal vacío");
  let previousCenter = Infinity;
  for (const n of counts) {
    const { hand, center } = ruleSet.deal[n]!;
    if (n < 2) fail("se necesitan al menos 2 jugadores");
    if (hand * n + center !== deckSize) fail(`con ${n} jugadores el reparto no suma ${deckSize} cartas`);
    if (center > previousCenter) fail(`con ${n} jugadores hay más cartas al centro que con menos jugadores`);
    previousCenter = center;
  }
}
