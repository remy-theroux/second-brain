<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const email = ref('')
const password = ref('')
const errorMessage = ref('')

const auth = useAuthStore()
const router = useRouter()

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
  </main>
</template>
