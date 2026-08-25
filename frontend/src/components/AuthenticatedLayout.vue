<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Menu from 'primevue/menu'
import Message from 'primevue/message'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const errorMessage = ref('')

// Le menu principal ne porte que les actions de l'application, pas la gestion du compte,
// qui vit dans la zone en bas de la barre. Le modèle grandit avec les features.
const menuItems = [
  { label: 'Accueil', icon: 'pi pi-home', route: { name: 'home' } },
  { label: 'Documents', icon: 'pi pi-file', route: { name: 'documents' } },
]

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
  <div class="authenticated-layout">
    <aside class="sidebar">
      <h1 class="app-title">Second Brain</h1>

      <Menu :model="menuItems" class="sidebar-menu">
        <template #item="{ item, props }">
          <RouterLink v-slot="{ href, navigate }" :to="item.route" custom>
            <a :href="href" v-bind="props.action" @click="navigate">
              <span :class="item.icon" />
              <span>{{ item.label }}</span>
            </a>
          </RouterLink>
        </template>
      </Menu>

      <div class="account">
        <p v-if="auth.profile" class="account-email">{{ auth.profile.email }}</p>
        <Message v-else-if="errorMessage" severity="error">{{ errorMessage }}</Message>
        <Button
          type="button"
          label="Se déconnecter"
          icon="pi pi-sign-out"
          severity="secondary"
          fluid
          @click="logout"
        />
      </div>
    </aside>

    <main class="content">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.authenticated-layout {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  display: flex;
  flex-direction: column;
  width: var(--sb-sidebar-width);
  flex-shrink: 0;
  padding: var(--sb-space-lg) var(--sb-space-md);
  border-right: 1px solid var(--p-content-border-color);
}

.app-title {
  margin: 0 0 var(--sb-space-lg);
  font-size: var(--sb-section-title-size);
}

/* Pousse la zone compte en bas de la barre. */
.sidebar-menu {
  flex: 1;
  border: none;
  background: transparent;
}

.account {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-sm);
  padding-top: var(--sb-space-md);
  border-top: 1px solid var(--p-content-border-color);
}

.account-email {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--sb-text-small);
  color: var(--p-text-muted-color);
}

.content {
  flex: 1;
  padding: var(--sb-space-xl);
}
</style>
