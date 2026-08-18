<script setup>
import { ref } from 'vue'
import { register, ValidationError } from '@/api/client'

const email = ref('')
const password = ref('')
const fieldErrors = ref({})
const errorMessage = ref('')
const registered = ref(false)

async function submit() {
  fieldErrors.value = {}
  errorMessage.value = ''
  try {
    await register(email.value, password.value)
    registered.value = true
  } catch (error) {
    // Les messages viennent du serveur et sont affichables tels quels.
    if (error instanceof ValidationError) {
      fieldErrors.value = error.errors
      return
    }
    errorMessage.value = error.message
  }
}
</script>

<template>
  <main>
    <h1>Créer mon compte</h1>

    <p v-if="registered" role="status">
      Votre compte est créé. Un lien de vérification vient de vous être envoyé par email.
    </p>

    <template v-else>
      <p v-if="errorMessage" role="alert">{{ errorMessage }}</p>

      <form @submit.prevent="submit">
        <p>
          <label for="email">Email</label><br>
          <input id="email" v-model="email" type="email" autocomplete="username">
          <span v-if="fieldErrors.email" role="alert">{{ fieldErrors.email }}</span>
        </p>
        <p>
          <label for="password">Mot de passe</label><br>
          <input id="password" v-model="password" type="password" autocomplete="new-password">
          <span v-if="fieldErrors.password" role="alert">{{ fieldErrors.password }}</span>
        </p>
        <p>
          <button type="submit">Créer mon compte</button>
        </p>
      </form>
    </template>

    <p>
      <RouterLink :to="{ name: 'login' }">J'ai déjà un compte</RouterLink>
    </p>
  </main>
</template>
