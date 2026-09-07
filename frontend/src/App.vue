<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AuthenticatedLayout from '@/components/AuthenticatedLayout.vue'
import GuestLayout from '@/components/GuestLayout.vue'

const route = useRoute()
// The layout is derived from the authentication metas the routes already carry: a dedicated
// `layout` meta would say the same thing twice. The guest layout is the default.
//
// Only exception: `layout: 'bare'`, for a page that lays out its own container (the
// design system, too wide for the guest card). A third layout for a single page
// would be one too many; `null` renders the slot as is.
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
