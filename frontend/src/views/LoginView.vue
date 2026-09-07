<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Password from 'primevue/password'
import FormField from '@/components/FormField.vue'
import PageTitle from '@/components/PageTitle.vue'
import { useAuthStore } from '@/stores/auth'

// The server redirects here with a code, not a message: this is a navigation, and making
// the text travel in a query string would stick it into the browser history and into the
// proxy logs. So the labels live here — at the price of a duplication with the domain
// messages, whose divergence no test watches.
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

// A legitimate `computed` here, unlike `isAuthenticated()`: the value depends only on the
// URL, which is reactive.
const verificationMessage = computed(() => VERIFICATION_MESSAGES[route.query.verification])

async function submit() {
  errorMessage.value = ''
  try {
    await auth.login(email.value, password.value)
    await router.push({ name: 'home' })
  } catch (error) {
    // The message comes from the server (error_description) and is displayable as is.
    errorMessage.value = error.message
  }
}
</script>

<template>
  <main class="guest-form">
    <PageTitle>Se connecter</PageTitle>

    <!-- A status, not an alert: the fallthrough replaces the component's role="alert". -->
    <Message v-if="verificationMessage" severity="success" role="status">
      {{ verificationMessage }}
    </Message>

    <Message v-if="errorMessage" severity="error">{{ errorMessage }}</Message>

    <form @submit.prevent="submit">
      <FormField id="email" label="Email">
        <InputText id="email" v-model="email" type="email" autocomplete="username" fluid />
      </FormField>
      <FormField id="password" label="Mot de passe">
        <Password
          v-model="password"
          input-id="password"
          :feedback="false"
          toggle-mask
          fluid
          :input-props="{ autocomplete: 'current-password' }"
        />
      </FormField>
      <Button type="submit" label="Se connecter" fluid />
    </form>

    <p class="guest-switch">
      <RouterLink :to="{ name: 'register' }">Créer mon compte</RouterLink>
    </p>
  </main>
</template>
