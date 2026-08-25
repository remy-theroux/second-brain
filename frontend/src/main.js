import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ConfirmationService from 'primevue/confirmationservice'
import Aura from '@primeuix/themes/aura'
import { fr } from 'primelocale/js/fr.js'
import App from '@/App.vue'
import router from '@/router'
import 'primeicons/primeicons.css'
import '@/assets/main.css'

const app = createApp(App)
  .use(createPinia())
  .use(router)
  .use(PrimeVue, { theme: { preset: Aura }, locale: fr })
  // Service des confirmations (ConfirmPopup) : un seul, pour toute action destructive.
  .use(ConfirmationService)

// L'App choisit son layout d'après route.meta ; monter avant la résolution de la route
// initiale afficherait le layout invité un instant, puis le bon. isReady() supprime ce flash.
router.isReady().then(() => app.mount('#app'))
