import { RULESETS, type RuleSet, type TurnOptions } from "../engine/index.js";
import { GameError } from "../lib/errors.js";
import type { RoomConfig } from "./types.js";

export const DEFAULT_RULESET = "TERNA_CLASSIC";
export const TURN_SECONDS = { min: 10, max: 60, default: 20 } as const;

/** Mínimo y máximo de jugadores que admite un modo (salen de su tabla de reparto). */
export function playerRange(ruleSet: RuleSet): { min: number; max: number } {
  const counts = Object.keys(ruleSet.deal).map(Number);
  return { min: Math.min(...counts), max: Math.max(...counts) };
}

export function ruleSetOrThrow(id: string): RuleSet {
  const ruleSet = RULESETS[id];
  if (!ruleSet) throw new GameError("invalid-argument", "UNKNOWN_RULESET", `Modo desconocido: ${id}`);
  return ruleSet;
}

const BOOLEAN_OPTIONS = ["allowSelfReveal", "allowClaim", "claimRequiresEnd", "claimFailEndsTurn", "allowPass", "continueAfterSet", "oncePerTarget", "hideMismatch", "allowMultiClaim"] as const;

/** Acepta solo las opciones conocidas, con su tipo correcto. Lo demás se ignora. */
export function parseOptions(raw: unknown): Partial<TurnOptions> {
  if (raw === undefined || raw === null) return {};
  if (typeof raw !== "object" || Array.isArray(raw)) throw new GameError("invalid-argument", "INVALID_OPTIONS");
  const input = raw as Record<string, unknown>;
  const out: Partial<TurnOptions> = {};

  for (const key of BOOLEAN_OPTIONS) {
    if (input[key] === undefined) continue;
    if (typeof input[key] !== "boolean") throw new GameError("invalid-argument", "INVALID_OPTIONS", `${key} debe ser verdadero o falso`);
    out[key] = input[key];
  }
  if (input.minRevealsBeforePass !== undefined) {
    const n = input.minRevealsBeforePass;
    if (typeof n !== "number" || !Number.isInteger(n) || n < 0 || n > 3) throw new GameError("invalid-argument", "INVALID_OPTIONS");
    out.minRevealsBeforePass = n;
  }
  return out;
}

export interface CreateRoomInput {
  maxPlayers: number;
  isPrivate: boolean;
  config: RoomConfig;
}

export function parseCreateRoom(raw: unknown): CreateRoomInput {
  const input = (raw ?? {}) as Record<string, unknown>;

  const ruleSetId = input.ruleSet === undefined ? DEFAULT_RULESET : String(input.ruleSet);
  const ruleSet = ruleSetOrThrow(ruleSetId);
  const range = playerRange(ruleSet);

  const maxPlayers = input.maxPlayers;
  if (typeof maxPlayers !== "number" || !Number.isInteger(maxPlayers) || maxPlayers < range.min || maxPlayers > range.max) {
    throw new GameError("invalid-argument", "INVALID_MAX_PLAYERS", `Entre ${range.min} y ${range.max} jugadores`);
  }
  if (typeof input.isPrivate !== "boolean") throw new GameError("invalid-argument", "INVALID_PRIVACY");

  const turnSeconds = input.turnSeconds ?? TURN_SECONDS.default;
  if (typeof turnSeconds !== "number" || !Number.isInteger(turnSeconds) || turnSeconds < TURN_SECONDS.min || turnSeconds > TURN_SECONDS.max) {
    throw new GameError("invalid-argument", "INVALID_TURN_SECONDS");
  }
  if (input.fillWithBots !== undefined && typeof input.fillWithBots !== "boolean") throw new GameError("invalid-argument", "INVALID_ARGUMENT");

  const options = parseOptions(input.options);
  return {
    maxPlayers,
    isPrivate: input.isPrivate,
    config: {
      ruleSet: ruleSetId,
      turnSeconds,
      fillWithBots: (input.fillWithBots as boolean | undefined) ?? true,
      ...(Object.keys(options).length > 0 ? { options } : {}),
    },
  };
}
