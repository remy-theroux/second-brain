<script setup>
import { useAuthStore } from '@/stores/auth'

// Loading the profile and signing out live in AuthenticatedLayout: the sidebar needs them
// on every signed-in page, this one is only content.
const auth = useAuthStore()
</script>

<template>
  <section>
    <h2>Accueil</h2>

    <p v-if="auth.profile">Bienvenue dans votre espace, {{ auth.profile.email }}.</p>
    <p v-else>Bienvenue dans votre espace.</p>
  </section>
</template>
