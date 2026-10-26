import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // Todos los tests comparten el mismo emulador: se ejecutan en serie.
    fileParallelism: false,
    testTimeout: 20_000,
    hookTimeout: 30_000,
  },
});
