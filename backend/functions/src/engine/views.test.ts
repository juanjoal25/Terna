import { describe, expect, it } from "vitest";
import { publicView, toPublicEvent } from "./views.js";
import { fixedGame, play } from "./test-utils.js";

describe("vista pública", () => {
  const g = fixedGame({ hands: { A: [3, 8], B: [1, 11], C: [4, 6] }, center: [10, null, 12] });

  it("publica cuántas cartas tiene cada uno, nunca cuáles", () => {
    const view = publicView(g);
    expect(view.players).toEqual([
      { id: "A", handCount: 2, sets: [] },
      { id: "B", handCount: 2, sets: [] },
      { id: "C", handCount: 2, sets: [] },
    ]);
    expect(view.center).toEqual([true, false, true]);
    // Ningún valor oculto (manos o centro) aparece en la vista serializada.
    const json = JSON.stringify(view);
    for (const hidden of [8, 11, 10, 12]) expect(json).not.toMatch(new RegExp(`\\b${hidden}\\b`));
  });

  it("muestra las cartas reveladas del turno sin el índice interno", () => {
    const { state, events } = play(g, { type: "REVEAL_HAND", player: "A", target: "B", end: "HIGH" });
    expect(publicView(state).turn.revealed).toEqual([{ source: "HAND", owner: "B", end: "HIGH", value: 11, claimed: false }]);
    expect(toPublicEvent(events[0]!)).toEqual({
      seq: 2,
      type: "CARD_REVEALED",
      player: "A",
      reveal: { source: "HAND", owner: "B", end: "HIGH", value: 11, claimed: false },
    });
  });
});
