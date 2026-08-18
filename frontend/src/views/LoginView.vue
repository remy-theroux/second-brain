<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// Le serveur redirige ici avec un code, pas un message : c'est une navigation, et faire
// voyager le texte en query string le collerait dans l'historique du navigateur et dans
// les logs du proxy. Les libellés vivent donc ici — au prix d'une duplication avec les
// messages du domaine, dont aucun test ne surveille la divergence.
const VERIFICATION_MESSAGES = {
  ok: 'Votre adresse est vérifiée. Vous pouvez vous connecter.',
  'lien-invalide': "Ce lien de vérification n'est pas valide.",
  'lien-expire': 'Ce lien de vérification a expiré.',
  'lien-deja-utilise': 'Ce lien de vérification a déjà été utilisé.',
}

const email = ref('')
const password = ref('')
const errorMessage = ref('')

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

// `computed` légitime ici, à l'inverse d'`isAuthenticated()` : la valeur ne dépend que de
// l'URL, qui est réactive.
const verificationMessage = computed(() => VERIFICATION_MESSAGES[route.query.verification])

async function submit() {
  errorMessage.value = ''
  try {
    await auth.login(email.value, password.value)
    await router.push({ name: 'home' })
  } catch (error) {
    // Le message vient du serveur (error_description) et est affichable tel quel.
    errorMessage.value = error.message
  }
}
</script>

<template>
  <main>
    <h1>Se connecter</h1>

    <p v-if="verificationMessage" role="status">{{ verificationMessage }}</p>

    <p v-if="errorMessage" role="alert">{{ errorMessage }}</p>

    <form @submit.prevent="submit">
      <p>
        <label for="email">Email</label><br>
        <input id="email" v-model="email" type="email" autocomplete="username">
      </p>
      <p>
        <label for="password">Mot de passe</label><br>
        <input id="password" v-model="password" type="password" autocomplete="current-password">
      </p>
      <p>
        <button type="submit">Se connecter</button>
      </p>
    </form>

    <p>
      <RouterLink :to="{ name: 'register' }">Créer mon compte</RouterLink>
    </p>
  </main>
</template>
