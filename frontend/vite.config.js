import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 0.0.0.0: without it the server only listens on the container's loopback and the
    // reverse proxy could not reach it.
    host: '0.0.0.0',
    port: 5173,
    // The browser reaches Vite through the reverse proxy, which routes /api to Spring:
    // a single origin, so nothing to configure for CORS. There is no proxy here anymore —
    // keeping one would leave dead configuration telling a false story about who owns
    // that single origin.
    //
    // The hot-reload WebSocket, however, must target the public port, not the container's
    // 5173, which is no longer published.
    hmr: { clientPort: Number(process.env.VITE_PUBLIC_PORT ?? 8080) },
  },
  test: {
    // The store reads localStorage: we need a browser environment, without a browser.
    environment: 'jsdom',
  },
})
