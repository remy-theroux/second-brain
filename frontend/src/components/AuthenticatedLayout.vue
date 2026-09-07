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

// The main menu carries only the application's actions, not account management, which
// lives in the area at the bottom of the bar. The model grows with the features.
const menuItems = [
  { label: 'Accueil', icon: 'pi pi-home', route: { name: 'home' } },
  { label: 'Documents', icon: 'pi pi-file', route: { name: 'documents' } },
]

// The guard let this through on the strength of the expiration remembered by the browser; this
// call asks the server what it really thinks. A 401 signs out (the store has already done so)
// and sends back to the login. Any other failure (backend down, 500, proxy in error) does not
// sign out: redirecting anyway would leave a silent duplicated navigation and the user on an
// empty page, without explanation. So the error is displayed instead.
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

/* Pushes the account area to the bottom of the bar. */
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
