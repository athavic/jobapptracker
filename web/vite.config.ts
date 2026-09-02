import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
// From 'vitest/config', not 'vite'. Same function, but this overload knows
// about the `test` key below; importing it from 'vite' typechecks everything
// else and then rejects that one block as an unknown property.
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Fail loudly instead of silently sliding to 5174, which would then be
    // blocked by CORS because the API only trusts 5173.
    strictPort: true,
  },
  test: {
    /*
     * 'node', so there is deliberately no DOM here.
     *
     * That is a limit on what these tests can cover, and it is chosen rather
     * than settled for. The components lean on <dialog>.showModal() and the
     * Popover API, and a simulated DOM implements those badly or not at all -
     * so a component test would be asserting against a stand-in that behaves
     * differently from the browser. RowMenu's own comments record two places
     * where those APIs surprised us in a REAL browser; a fake one would have
     * reproduced neither, and would have reported success.
     *
     * So this suite covers the logic that is honestly testable without a
     * browser: the pure functions that decide what the components render.
     * Anything needing a real dialog wants Playwright, not a DOM shim.
     */
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
