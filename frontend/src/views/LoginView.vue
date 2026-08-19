<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Password from 'primevue/password'
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
  <main class="login">
    <h1>Se connecter</h1>

    <!-- Un statut, pas une alerte : le fallthrough remplace le role="alert" du composant. -->
    <Message v-if="verificationMessage" severity="success" role="status">
      {{ verificationMessage }}
    </Message>

    <Message v-if="errorMessage" severity="error">{{ errorMessage }}</Message>

    <form @submit.prevent="submit">
      <div class="field">
        <label for="email">Email</label>
        <InputText id="email" v-model="email" type="email" autocomplete="username" fluid />
      </div>
      <div class="field">
        <label for="password">Mot de passe</label>
        <Password
          v-model="password"
          input-id="password"
          :feedback="false"
          toggle-mask
          fluid
          :input-props="{ autocomplete: 'current-password' }"
        />
      </div>
      <Button type="submit" label="Se connecter" fluid />
    </form>

    <p class="switch">
      <RouterLink :to="{ name: 'register' }">Créer mon compte</RouterLink>
    </p>
  </main>
</template>

<style scoped>
.login {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

h1 {
  margin: 0;
  font-size: 1.5rem;
}

form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.switch {
  margin: 0;
  text-align: center;
}
</style>
