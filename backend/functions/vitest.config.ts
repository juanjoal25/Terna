import { defineConfig } from "vitest/config";

// Pruebas unitarias del motor: rápidas y sin emuladores. Las de integración (`*.int.test.ts`)
// necesitan Realtime Database y Firestore locales: se corren con `npm run test:int`.
export default defineConfig({
  test: {
    exclude: ["**/node_modules/**", "**/lib/**", "**/*.int.test.ts"],
  },
});
