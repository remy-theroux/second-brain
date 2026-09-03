<script setup>
import { onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Column from 'primevue/column'
import ConfirmPopup from 'primevue/confirmpopup'
import DataTable from 'primevue/datatable'
import FileUpload from 'primevue/fileupload'
import InputText from 'primevue/inputtext'
import Menu from 'primevue/menu'
import Message from 'primevue/message'
import Password from 'primevue/password'
import { useConfirm } from 'primevue/useconfirm'
import FormField from '@/components/FormField.vue'
import PageTitle from '@/components/PageTitle.vue'
import DocumentStatusTag from '@/components/DocumentStatusTag.vue'

// Catalogue statique : tout ce qui est partagé — tokens, composants du projet, composants
// PrimeVue tels qu'on les emploie — dans chacun de ses états. Aucun store, aucun appel
// réseau : la page se regarde, elle ne fait rien. Un composant qui n'apparaît pas ici n'est
// pas partagé.

const PROJECT_TOKENS = [
  '--sb-space-xs',
  '--sb-space-sm',
  '--sb-space-md',
  '--sb-space-lg',
  '--sb-space-xl',
  '--sb-sidebar-width',
  '--sb-guest-width',
  '--sb-title-size',
  '--sb-section-title-size',
  '--sb-text-small',
]

// Les tokens Aura que le projet consomme. La liste est tenue à la main : c'est elle qui
// dit quels `--p-*` on s'autorise hors des composants PrimeVue.
const THEME_TOKENS = [
  '--p-content-background',
  '--p-content-border-color',
  '--p-text-color',
  '--p-text-muted-color',
  '--p-primary-color',
]

const BUTTON_SEVERITIES = ['primary', 'secondary', 'success', 'info', 'warn', 'danger', 'contrast']
const MESSAGE_SEVERITIES = ['success', 'info', 'warn', 'error', 'secondary', 'contrast']

const menuItems = [
  { label: 'Accueil', icon: 'pi pi-home' },
  { label: 'Documents', icon: 'pi pi-file' },
]

// La liste des documents, telle que DocumentsView la rend : trois colonnes et une action.
// La deuxième ligne porte la classe du doublon désigné par le serveur.
// Les statuts sont donnés en CODE, comme l'API les sérialise : c'est `DocumentStatusTag`
// qui porte le libellé, et le catalogue doit montrer l'écran tel qu'il est.
const DOCUMENT_STATUSES = ['PENDING', 'EXTRACTED', 'READY', 'FAILED']

const DOCUMENTS = [
  {
    id: 'a',
    filename: 'notes-de-lecture.md',
    status: 'EXTRACTED',
    createdAt: '25 août 2026, 09:12',
  },
  {
    id: 'b',
    filename: 'rapport-annuel.pdf',
    status: 'PENDING',
    createdAt: '24 août 2026, 18:40',
  },
  {
    id: 'c',
    filename: 'compte-rendu.docx',
    status: 'FAILED',
    createdAt: '23 août 2026, 11:05',
  },
]

const confirm = useConfirm()

function showConfirmation(event) {
  confirm.require({
    target: event.currentTarget,
    message: 'Supprimer « rapport-annuel.pdf » ?',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Annuler', severity: 'secondary', outlined: true },
    acceptProps: { label: 'Supprimer', severity: 'danger' },
  })
}

// Les valeurs sont lues sur le document une fois monté : c'est la valeur effective qui
// est affichée, pas celle qu'on croit avoir écrite dans main.css.
const tokenValues = ref({})

onMounted(() => {
  const styles = getComputedStyle(document.documentElement)
  const values = {}
  for (const token of [...PROJECT_TOKENS, ...THEME_TOKENS]) {
    values[token] = styles.getPropertyValue(token).trim()
  }
  tokenValues.value = values
})
</script>

<template>
  <main class="design-system">
    <header class="design-system-header">
      <PageTitle>Design system</PageTitle>
      <p class="muted">
        Tokens et composants partagés du front, dans chacun de leurs états. Page de développement,
        absente du bundle de production.
      </p>
    </header>

    <section>
      <h2>Tokens du projet</h2>
      <p class="muted">
        Préfixe <code>--sb-</code>, définis dans <code>src/assets/main.css</code>. Espacements,
        largeurs et tailles de titre : un composant n'écrit jamais un <code>rem</code> nu.
      </p>
      <table class="tokens">
        <tbody>
          <tr v-for="token in PROJECT_TOKENS" :key="token">
            <th scope="row">
              <code>{{ token }}</code>
            </th>
            <td>{{ tokenValues[token] }}</td>
            <td>
              <span
                v-if="token.startsWith('--sb-space')"
                class="space-sample"
                :style="{ width: `var(${token})`, height: `var(${token})` }"
              />
              <span
                v-else-if="token.endsWith('-size') || token.endsWith('-small')"
                :style="{ fontSize: `var(${token})` }"
                >Aa</span
              >
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section>
      <h2>Tokens du thème</h2>
      <p class="muted">
        Préfixe <code>--p-</code>, fournis par Aura. Toutes les couleurs passent par eux, et suivent
        le thème clair ou sombre du système. Seuls ceux listés ici sont employés hors des composants
        PrimeVue.
      </p>
      <table class="tokens">
        <tbody>
          <tr v-for="token in THEME_TOKENS" :key="token">
            <th scope="row">
              <code>{{ token }}</code>
            </th>
            <td>{{ tokenValues[token] }}</td>
            <td><span class="color-sample" :style="{ background: `var(${token})` }" /></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section>
      <h2>Typographie</h2>
      <div class="stack">
        <PageTitle>Titre d'écran — PageTitle</PageTitle>
        <h2 class="no-margin">Titre de section — h2</h2>
        <p class="no-margin">
          Paragraphe courant. Le corps de texte garde la taille du navigateur ; seule la police est
          fixée, sur la pile système.
        </p>
        <p class="no-margin muted">Texte atténué — <code>--p-text-muted-color</code>.</p>
        <p class="no-margin small">Texte petit — <code>--sb-text-small</code>.</p>
        <p class="no-margin"><a href="#">Lien</a></p>
      </div>
    </section>

    <section>
      <h2>Boutons</h2>
      <div class="row">
        <Button
          v-for="severity in BUTTON_SEVERITIES"
          :key="severity"
          :label="severity"
          :severity="severity"
        />
      </div>
      <div class="row">
        <Button label="Avec icône" icon="pi pi-check" />
        <Button label="Contour" outlined />
        <Button label="Texte" text />
        <Button label="Désactivé" disabled />
        <Button label="Chargement" loading />
      </div>
    </section>

    <section>
      <h2>Champs de formulaire — FormField</h2>
      <p class="muted">
        Libellé, slot pour l'input, message d'erreur. L'input reste à la charge de la vue :
        <code>id</code> ou <code>input-id</code>, <code>invalid</code>, <code>autocomplete</code>.
      </p>
      <div class="form-grid">
        <FormField id="ds-text" label="Champ texte">
          <InputText id="ds-text" model-value="Une valeur" fluid />
        </FormField>
        <FormField id="ds-text-invalid" label="Champ en erreur" error="Ce champ est refusé.">
          <InputText id="ds-text-invalid" model-value="valeur refusée" invalid fluid />
        </FormField>
        <FormField id="ds-text-disabled" label="Champ désactivé">
          <InputText id="ds-text-disabled" model-value="Lecture seule" disabled fluid />
        </FormField>
        <FormField id="ds-password" label="Mot de passe">
          <Password
            input-id="ds-password"
            model-value="secret"
            :feedback="false"
            toggle-mask
            fluid
          />
        </FormField>
        <FormField id="ds-password-invalid" label="Mot de passe en erreur" error="Trop court.">
          <Password
            input-id="ds-password-invalid"
            model-value="abc"
            :feedback="false"
            toggle-mask
            invalid
            fluid
          />
        </FormField>
      </div>
    </section>

    <section>
      <h2>Messages</h2>
      <div class="stack">
        <Message v-for="severity in MESSAGE_SEVERITIES" :key="severity" :severity="severity">
          Message <code>{{ severity }}</code
          >.
        </Message>
        <Message severity="error" size="small" variant="simple">
          Variante <code>simple</code> en taille <code>small</code> : celle des erreurs de champ.
        </Message>
      </div>
    </section>

    <section>
      <h2>Carte</h2>
      <p class="muted">
        Le conteneur des écrans invités (<code>GuestLayout</code>), à sa largeur réelle
        <code>--sb-guest-width</code>.
      </p>
      <Card class="guest-sample">
        <template #content>
          <div class="guest-form">
            <PageTitle>Écran invité</PageTitle>
            <form>
              <FormField id="ds-card-email" label="Email">
                <InputText id="ds-card-email" fluid />
              </FormField>
              <Button label="Valider" fluid />
            </form>
            <p class="guest-switch"><a href="#">Lien vers l'autre écran</a></p>
          </div>
        </template>
      </Card>
    </section>

    <section>
      <h2>Menu</h2>
      <p class="muted">
        Le menu de la barre latérale (<code>AuthenticatedLayout</code>), à sa largeur réelle
        <code>--sb-sidebar-width</code>.
      </p>
      <div class="sidebar-sample">
        <Menu :model="menuItems" class="sidebar-menu" />
      </div>
    </section>

    <section>
      <h2>Dépôt de fichier — FileUpload</h2>
      <p class="muted">
        Mode <code>basic</code>, envoi automatique à la sélection, <code>custom-upload</code> :
        l'appel HTTP part de <code>src/api/</code>, jamais du composant. Ici, la sélection ne fait
        rien.
      </p>
      <div class="row">
        <FileUpload
          mode="basic"
          custom-upload
          auto
          choose-label="Déposer un document"
          choose-icon="pi pi-upload"
        />
        <FileUpload
          mode="basic"
          custom-upload
          auto
          choose-label="Désactivé pendant l'envoi"
          choose-icon="pi pi-upload"
          disabled
        />
      </div>
    </section>

    <section>
      <h2>Statut de document — DocumentStatusTag</h2>
      <p class="muted">
        Le libellé et la sévérité d'un statut, au même endroit pour la liste et pour le détail. Le
        code vient de l'API, le libellé est une affaire d'écran — ADR-0022.
      </p>
      <div class="row">
        <DocumentStatusTag v-for="status in DOCUMENT_STATUSES" :key="status" :status="status" />
      </div>
    </section>

    <section>
      <h2>Tableau — DataTable</h2>
      <p class="muted">
        La liste des documents (<code>DocumentsView</code>). La ligne en gras, soulignée à gauche
        par <code>--p-primary-color</code>, est le doublon que le serveur a désigné après un dépôt
        refusé. Le bouton ouvre la confirmation (<code>ConfirmPopup</code>) qui précède toute
        suppression.
      </p>
      <ConfirmPopup />
      <DataTable
        :value="DOCUMENTS"
        data-key="id"
        :row-class="(row) => (row.id === 'b' ? 'table-duplicate-row' : '')"
      >
        <Column field="filename" header="Fichier" />
        <Column header="Statut">
          <template #body="{ data }"><DocumentStatusTag :status="data.status" /></template>
        </Column>
        <Column field="createdAt" header="Déposé le" />
        <Column class="table-actions">
          <template #body="{ data }">
            <Button
              type="button"
              icon="pi pi-eye"
              text
              rounded
              :aria-label="`Voir ${data.filename}`"
            />
            <Button
              type="button"
              icon="pi pi-trash"
              severity="danger"
              text
              rounded
              :aria-label="`Supprimer ${data.filename}`"
              @click="showConfirmation"
            />
          </template>
        </Column>
      </DataTable>
      <DataTable :value="[]">
        <template #empty>Aucun document pour l'instant.</template>
        <Column header="Fichier" />
        <Column header="Statut" />
        <Column header="Déposé le" />
      </DataTable>
    </section>
  </main>
</template>

<style scoped>
.design-system {
  max-width: 64rem;
  margin: 0 auto;
  padding: var(--sb-space-xl);
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-xl);
}

.design-system-header {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-xs);
}

section {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-md);
}

h2 {
  margin: 0;
  font-size: var(--sb-section-title-size);
  padding-bottom: var(--sb-space-xs);
  border-bottom: 1px solid var(--p-content-border-color);
}

p {
  margin: 0;
}

.no-margin {
  margin: 0;
}

.muted {
  color: var(--p-text-muted-color);
}

.small {
  font-size: var(--sb-text-small);
}

code {
  font-size: var(--sb-text-small);
}

.tokens {
  border-collapse: collapse;
  width: 100%;
}

.tokens th,
.tokens td {
  text-align: left;
  padding: var(--sb-space-xs) var(--sb-space-sm);
  border-bottom: 1px solid var(--p-content-border-color);
  vertical-align: middle;
}

.tokens th {
  font-weight: normal;
  width: 16rem;
}

.space-sample {
  display: inline-block;
  background: var(--p-primary-color);
  vertical-align: middle;
}

.color-sample {
  display: inline-block;
  width: 3rem;
  height: 1.5rem;
  border: 1px solid var(--p-content-border-color);
  vertical-align: middle;
}

.stack {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-sm);
}

.row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sb-space-sm);
  align-items: center;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(18rem, 1fr));
  gap: var(--sb-space-md);
}

.guest-sample {
  width: 100%;
  max-width: var(--sb-guest-width);
}

.sidebar-sample {
  width: var(--sb-sidebar-width);
  padding: var(--sb-space-lg) var(--sb-space-md);
  border-right: 1px solid var(--p-content-border-color);
}

.sidebar-menu {
  border: none;
  background: transparent;
}
</style>
