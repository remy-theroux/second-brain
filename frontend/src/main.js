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
  // Confirmation service (ConfirmPopup): a single one, for every destructive action.
  .use(ConfirmationService)

// App picks its layout from route.meta; mounting before the initial route is resolved
// would show the guest layout for an instant, then the right one. isReady() removes that flash.
router.isReady().then(() => app.mount('#app'))
