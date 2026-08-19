<script setup>
import { ref } from 'vue'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Password from 'primevue/password'
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
  <main class="register">
    <h1>Créer mon compte</h1>

    <!-- Un statut, pas une alerte : le fallthrough remplace le role="alert" du composant. -->
    <Message v-if="registered" severity="success" role="status">
      Votre compte est créé. Un lien de vérification vient de vous être envoyé par email.
    </Message>

    <template v-else>
      <Message v-if="errorMessage" severity="error">{{ errorMessage }}</Message>

      <form @submit.prevent="submit">
        <div class="field">
          <label for="email">Email</label>
          <InputText
            id="email"
            v-model="email"
            type="email"
            autocomplete="username"
            :invalid="!!fieldErrors.email"
            fluid
          />
          <Message v-if="fieldErrors.email" severity="error" size="small" variant="simple">
            {{ fieldErrors.email }}
          </Message>
        </div>
        <div class="field">
          <label for="password">Mot de passe</label>
          <Password
            v-model="password"
            input-id="password"
            :feedback="false"
            toggle-mask
            :invalid="!!fieldErrors.password"
            fluid
            :input-props="{ autocomplete: 'new-password' }"
          />
          <Message v-if="fieldErrors.password" severity="error" size="small" variant="simple">
            {{ fieldErrors.password }}
          </Message>
        </div>
        <Button type="submit" label="Créer mon compte" fluid />
      </form>
    </template>

    <p class="switch">
      <RouterLink :to="{ name: 'login' }">J'ai déjà un compte</RouterLink>
    </p>
  </main>
</template>

<style scoped>
.register {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

h1 {
  margin: 0;
  font-size: 1.5rem;
}

form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.switch {
  margin: 0;
  text-align: center;
}
</style>
