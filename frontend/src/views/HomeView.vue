<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

// Le garde a laissé passer sur la foi de l'expiration mémorisée côté navigateur ; cet
// appel demande au serveur ce qu'il en pense vraiment. Un refus déconnecte.
onMounted(async () => {
  try {
    await auth.loadProfile()
  } catch {
    await router.push({ name: 'login' })
  }
})

async function logout() {
  auth.logout()
  await router.push({ name: 'login' })
}
</script>

<template>
  <main>
    <h1>Second Brain</h1>

    <p v-if="auth.profile">Connecté avec l'adresse {{ auth.profile.email }}.</p>

    <p>
      <button type="button" @click="logout">Se déconnecter</button>
    </p>
  </main>
</template>
