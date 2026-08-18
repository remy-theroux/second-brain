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
    // 0.0.0.0 : sans ça, le serveur n'écoute que la boucle locale du conteneur et le
    // reverse proxy ne l'atteindrait pas.
    host: '0.0.0.0',
    port: 5173,
    // Le navigateur atteint Vite à travers le reverse proxy, qui route /api vers Spring :
    // une seule origine, donc rien à configurer en CORS. Il n'y a plus de proxy ici — le
    // garder laisserait une configuration morte racontant une histoire fausse sur qui
    // tient cette origine unique.
    //
    // En revanche le WebSocket du rechargement à chaud doit viser le port public, pas le
    // 5173 du conteneur, qui n'est plus publié.
    hmr: { clientPort: Number(process.env.VITE_PUBLIC_PORT ?? 8080) },
  },
  test: {
    // Le store lit localStorage : il faut un environnement navigateur, sans navigateur.
    environment: 'jsdom',
  },
})
