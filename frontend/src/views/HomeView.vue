<script setup>
import { useAuthStore } from '@/stores/auth'

// Le chargement du profil et la déconnexion vivent dans AuthenticatedLayout : la barre
// latérale en a besoin sur toutes les pages connectées, celle-ci n'est que du contenu.
const auth = useAuthStore()
</script>

<template>
  <section>
    <h2>Accueil</h2>

    <p v-if="auth.profile">Bienvenue dans votre espace, {{ auth.profile.email }}.</p>
    <p v-else>Bienvenue dans votre espace.</p>
  </section>
</template>
