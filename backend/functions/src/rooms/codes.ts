import { randomInt } from "node:crypto";

export const CODE_PREFIX = "TRN-";
/** Sin caracteres ambiguos: no hay 0/O, 1/I/L. 31 símbolos → 31⁴ ≈ 923 000 códigos. */
const ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
const CODE_LENGTH = 4;

export function randomRoomCode(): string {
  let body = "";
  for (let i = 0; i < CODE_LENGTH; i++) body += ALPHABET[randomInt(0, ALPHABET.length)];
  return CODE_PREFIX + body;
}

/**
 * Normaliza lo que escribe el jugador: acepta "trn-7k4q", "7K4Q" o " TRN7K4Q ".
 * Devuelve `null` si no tiene el formato de un código.
 */
export function normalizeRoomCode(input: string): string | null {
  const letters = input.toUpperCase().replace(/[^A-Z0-9]/g, "");
  // Solo se quita el prefijo si lo trae completo: un código sin prefijo puede empezar por "TRN".
  const hasPrefix = letters.length === CODE_LENGTH + 3 && letters.startsWith("TRN");
  const body = hasPrefix ? letters.slice(3) : letters;
  if (body.length !== CODE_LENGTH || [...body].some((c) => !ALPHABET.includes(c))) return null;
  return CODE_PREFIX + body;
}
