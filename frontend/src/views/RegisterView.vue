<script setup>
import { ref } from 'vue'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Password from 'primevue/password'
import FormField from '@/components/FormField.vue'
import PageTitle from '@/components/PageTitle.vue'
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
  <main class="guest-form">
    <PageTitle>Créer mon compte</PageTitle>

    <!-- Un statut, pas une alerte : le fallthrough remplace le role="alert" du composant. -->
    <Message v-if="registered" severity="success" role="status">
      Votre compte est créé. Un lien de vérification vient de vous être envoyé par email.
    </Message>

    <template v-else>
      <Message v-if="errorMessage" severity="error">{{ errorMessage }}</Message>

      <form @submit.prevent="submit">
        <FormField id="email" label="Email" :error="fieldErrors.email">
          <InputText
            id="email"
            v-model="email"
            type="email"
            autocomplete="username"
            :invalid="!!fieldErrors.email"
            fluid
          />
        </FormField>
        <FormField id="password" label="Mot de passe" :error="fieldErrors.password">
          <Password
            v-model="password"
            input-id="password"
            :feedback="false"
            toggle-mask
            :invalid="!!fieldErrors.password"
            fluid
            :input-props="{ autocomplete: 'new-password' }"
          />
        </FormField>
        <Button type="submit" label="Créer mon compte" fluid />
      </form>
    </template>

    <p class="guest-switch">
      <RouterLink :to="{ name: 'login' }">J'ai déjà un compte</RouterLink>
    </p>
  </main>
</template>
