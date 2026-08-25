<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AuthenticatedLayout from '@/components/AuthenticatedLayout.vue'
import GuestLayout from '@/components/GuestLayout.vue'

const route = useRoute()
// Le layout se déduit des métas d'authentification déjà portées par les routes : une méta
// `layout` dédiée dirait deux fois la même chose. Le layout invité est le défaut.
//
// Seule exception : `layout: 'bare'`, pour une page qui pose son propre conteneur (le
// design system, trop large pour la carte invité). Un troisième layout pour une seule
// page serait de trop ; `null` rend le slot tel quel.
const layout = computed(() => {
  if (route.meta.layout === 'bare') return null
  return route.meta.requiresAuth ? AuthenticatedLayout : GuestLayout
})
</script>

<template>
  <component :is="layout" v-if="layout">
    <RouterView />
  </component>
  <RouterView v-else />
</template>
