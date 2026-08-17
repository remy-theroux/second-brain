import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Cible du proxy : le nom de service Compose du back en conteneur, localhost sinon.
const apiTarget = process.env.VITE_API_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 0.0.0.0 : sans ça, le serveur n'écoute que la boucle locale du conteneur et le
    // port publié par Compose ne mène nulle part.
    host: '0.0.0.0',
    port: 5173,
    // Le navigateur ne voit qu'une seule origine : rien à configurer en CORS côté Spring.
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true },
    },
  },
  test: {
    // Le store lit localStorage : il faut un environnement navigateur, sans navigateur.
    environment: 'jsdom',
  },
})
