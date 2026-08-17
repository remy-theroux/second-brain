<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const errorMessage = ref('')

// Le garde a laissé passer sur la foi de l'expiration mémorisée côté navigateur ; cet
// appel demande au serveur ce qu'il en pense vraiment. Un 401 déconnecte (le store l'a déjà
// fait) et renvoie vers la connexion. Toute autre panne (backend éteint, 500, proxy en
// erreur) ne déconnecte pas : rediriger quand même laisserait une navigation dupliquée
// silencieuse et l'utilisateur sur une page vide, sans explication. On affiche donc l'erreur
// à la place.
onMounted(async () => {
  try {
    await auth.loadProfile()
  } catch (error) {
    if (!auth.isAuthenticated()) {
      await router.push({ name: 'login' })
      return
    }
    errorMessage.value = error.message
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
    <p v-else-if="errorMessage" role="alert">{{ errorMessage }}</p>

    <p>
      <button type="button" @click="logout">Se déconnecter</button>
    </p>
  </main>
</template>
