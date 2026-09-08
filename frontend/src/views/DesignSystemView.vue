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
import DocumentSourceTag from '@/components/DocumentSourceTag.vue'
import DocumentStatusTag from '@/components/DocumentStatusTag.vue'
import AnswerText from '@/components/AnswerText.vue'
import AnswerSources from '@/components/AnswerSources.vue'
import DownloadDocumentButton from '@/components/DownloadDocumentButton.vue'
import DriveSourceCard from '@/components/DriveSourceCard.vue'
import DriveFolderPicker from '@/components/DriveFolderPicker.vue'

// Static catalogue: everything that is shared — tokens, project components, PrimeVue
// components as we use them — in each of its states. Mostly no store, no network call —
// the download button below is the one exception, and it really fetches. A component that
// does not appear here is not shared.

const PROJECT_TOKENS = [
  '--sb-space-xs',
  '--sb-space-sm',
  '--sb-space-md',
  '--sb-space-lg',
  '--sb-space-xl',
  '--sb-sidebar-width',
  '--sb-guest-width',
  '--sb-picker-max-height',
  '--sb-title-size',
  '--sb-section-title-size',
  '--sb-text-small',
]

// The Aura tokens the project consumes. The list is maintained by hand: it is what says
// which `--p-*` we allow ourselves outside PrimeVue components.
const THEME_TOKENS = [
  '--p-content-background',
  '--p-content-border-color',
  '--p-text-color',
  '--p-text-muted-color',
  '--p-primary-color',
  '--p-content-border-radius',
]

const BUTTON_SEVERITIES = ['primary', 'secondary', 'success', 'info', 'warn', 'danger', 'contrast']
const MESSAGE_SEVERITIES = ['success', 'info', 'warn', 'error', 'secondary', 'contrast']

const menuItems = [
  { label: 'Accueil', icon: 'pi pi-home' },
  { label: 'Documents', icon: 'pi pi-file' },
]

// The document list, as DocumentsView renders it: three columns and one action.
// The second row carries the class of the duplicate designated by the server.
// The statuses are given as CODES, the way the API serialises them: `DocumentStatusTag`
// is what carries the label, and the catalogue must show the screen as it is.
const DOCUMENT_STATUSES = ['PENDING', 'EXTRACTED', 'READY', 'FAILED']

const DOCUMENTS = [
  {
    id: 'a',
    filename: 'notes-de-lecture.md',
    status: 'EXTRACTED',
    source: 'MANUAL',
    createdAt: '25 août 2026, 09:12',
  },
  {
    id: 'b',
    filename: 'rapport-annuel.pdf',
    status: 'PENDING',
    source: 'GOOGLE_DRIVE',
    driveLink: 'https://drive.google.com/file/d/exemple/view',
    createdAt: '24 août 2026, 18:40',
  },
  {
    id: 'c',
    filename: 'compte-rendu.docx',
    status: 'FAILED',
    source: 'MANUAL',
    createdAt: '23 août 2026, 11:05',
  },
]

const ANSWER_SOURCES = [
  {
    number: 1,
    documentId: 'doc-1',
    filename: 'rapport.pdf',
    position: 3,
    heading: 'Rétractation',
    text: 'Le délai de rétractation est de quatorze jours à compter de la réception.',
  },
  {
    number: 2,
    documentId: 'doc-2',
    filename: 'conditions-generales.md',
    position: 0,
    heading: 'Remboursement',
    text: 'Le remboursement intervient au plus tard trente jours après le retour.',
  },
]

// The four states of a watched folder. The one that never ran carries NEITHER `lastImportAt`,
// NOR `lastImportStatus`, NOR `lastImportError`: the API leaves the three out, and it is their
// absence that says so — the fixture must be missing them, not carry them null.
const WATCHED_FOLDERS = [
  {
    id: '1',
    name: 'Notes de réunion',
    watchedAt: '2026-09-07T08:00:00Z',
    documentCount: 0,
    rejections: [],
  },
  {
    id: '2',
    name: 'Contrats',
    watchedAt: '2026-09-01T08:00:00Z',
    lastImportAt: '2026-09-08T07:30:00Z',
    lastImportStatus: 'SUCCEEDED',
    documentCount: 12,
    rejections: [],
  },
  {
    id: '3',
    name: 'Photos de chantier',
    watchedAt: '2026-09-02T08:00:00Z',
    lastImportAt: '2026-09-08T07:31:00Z',
    lastImportStatus: 'SUCCEEDED',
    documentCount: 3,
    rejections: [
      { filename: 'facade.jpg', reason: "Ce format de fichier n'est pas accepté." },
      { filename: 'plan.dwg', reason: "Ce format de fichier n'est pas accepté." },
    ],
  },
  {
    id: '4',
    name: 'Archives 2019',
    watchedAt: '2026-09-03T08:00:00Z',
    lastImportAt: '2026-09-08T07:32:00Z',
    lastImportStatus: 'FAILED',
    lastImportError: 'Google Drive est momentanément injoignable.',
    documentCount: 5,
    rejections: [],
  },
]

const openedSources = ref([1])

const confirm = useConfirm()

// The very message of DocumentsView: a document that came from a watched folder warns that it
// will be back at the next synchronisation, the others do not.
function showConfirmation(event, document) {
  confirm.require({
    target: event.currentTarget,
    message:
      document.source === 'GOOGLE_DRIVE'
        ? `Supprimer « ${document.filename} » ? Il vient d'un dossier surveillé : il reviendra à la prochaine synchronisation.`
        : `Supprimer « ${document.filename} » ?`,
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Annuler', severity: 'secondary', outlined: true },
    acceptProps: { label: 'Supprimer', severity: 'danger' },
  })
}

// The values are read from the document once mounted: it is the effective value that is
// displayed, not the one we think we wrote in main.css.
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
            <td>
              <span
                v-if="token.endsWith('-radius')"
                class="radius-sample"
                :style="{ borderRadius: `var(${token})` }"
              />
              <span v-else class="color-sample" :style="{ background: `var(${token})` }" />
            </td>
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
      <h2>Téléchargement d'un original — DownloadDocumentButton</h2>
      <p class="muted">
        Le même geste pour la liste et pour le détail : le jeton voyageant en en-tête, un lien ne
        peut rien télécharger — le fichier est lu par <code>src/api/</code> puis remis au navigateur
        par une ancre temporaire. Le bouton porte son état occupé ; il émet ses erreurs, la vue
        garde la déconnexion. Ici, le clic part vraiment et échoue sans conséquence : le catalogue
        n'écoute pas l'événement.
      </p>
      <div class="row">
        <DownloadDocumentButton
          document-id="00000000-0000-0000-0000-000000000000"
          filename="rapport.pdf"
        />
        <DownloadDocumentButton
          document-id="00000000-0000-0000-0000-000000000000"
          filename="rapport.pdf"
          disabled
        />
      </div>
    </section>

    <section>
      <h2>Compte Drive à reconnecter</h2>
      <p class="muted">
        L'état <code>NEEDS_RECONNECTION</code> d'une connexion Drive, tel que le rend
        <code>DocumentsView</code> : le refus est annoncé, et le geste qui répare reste offert
        <strong>même quand le sélecteur de dossier est ouvert</strong> — c'est en l'ouvrant qu'on
        découvre le plus souvent que l'autorisation ne tient plus. L'état <code>ACTIVE</code>
        n'affiche ni l'un ni l'autre.
      </p>
      <div class="stack">
        <Message severity="warn">
          L'autorisation Google Drive n'est plus valide. Reconnectez le compte pour que les dossiers
          surveillés reprennent.
        </Message>
        <div class="row">
          <Button type="button" label="Ajouter un dossier" icon="pi pi-plus" text />
          <Button type="button" label="Reconnecter le compte" icon="pi pi-google" />
        </div>
      </div>
    </section>

    <section>
      <h2>Dossier surveillé — DriveSourceCard</h2>
      <p class="muted">
        Un dossier Drive, le bilan de son dernier import et ses deux gestes. Un dossier
        <strong>jamais synchronisé</strong> se reconnaît à l'<em>absence</em> des trois champs de
        bilan, pas à une valeur nulle. Le motif d'un échec et la raison d'un fichier écarté viennent
        du serveur et s'affichent tels quels ; l'état, lui, est un code que l'écran traduit —
        ADR-0022. Les boutons émettent, la vue garde les confirmations et la déconnexion : ici, ils
        ne font rien.
      </p>
      <div class="stack">
        <DriveSourceCard v-for="folder in WATCHED_FOLDERS" :key="folder.id" :folder="folder" />
        <DriveSourceCard :folder="WATCHED_FOLDERS[1]" busy />
      </div>
    </section>

    <section>
      <h2>Choix d'un dossier — DriveFolderPicker</h2>
      <p class="muted">
        Un fil d'Ariane, pas un arbre : le parcours se fait par appels successifs, un niveau à la
        fois. Seuls des dossiers y sont listés. Comme le bouton de téléchargement ci-dessus, le
        composant appelle vraiment <code>src/api/</code> : sans Drive connecté, c'est son état de
        refus qui s'affiche ici — l'état chargé se regarde sur l'écran des sources.
      </p>
      <DriveFolderPicker />
    </section>

    <section>
      <h2>Provenance d'un document — DocumentSourceTag</h2>
      <p class="muted">
        D'où vient un document, dans la liste. Un document importé porte un lien vers son fichier
        Drive, en <code>target="_blank" rel="noopener"</code> — c'est une URL tierce. Le lien peut
        manquer : l'identifiant du fichier n'est pas exposé, seul le lien l'est, et Drive n'en rend
        pas toujours un.
      </p>
      <div class="row">
        <DocumentSourceTag source="MANUAL" />
        <DocumentSourceTag
          source="GOOGLE_DRIVE"
          drive-link="https://drive.google.com/file/d/exemple/view"
        />
        <DocumentSourceTag source="GOOGLE_DRIVE" />
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
        <Column header="Provenance">
          <template #body="{ data }">
            <DocumentSourceTag :source="data.source" :drive-link="data.driveLink" />
          </template>
        </Column>
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
              @click="showConfirmation($event, data)"
            />
          </template>
        </Column>
      </DataTable>
      <DataTable :value="[]">
        <template #empty>Aucun document pour l'instant.</template>
        <Column header="Fichier" />
        <Column header="Provenance" />
        <Column header="Statut" />
        <Column header="Déposé le" />
      </DataTable>
    </section>

    <section>
      <h2>Réponse de l'agent — AnswerText</h2>
      <p class="muted">
        Les <code>[n]</code> dont la source est connue deviennent cliquables ; les autres restent du
        texte. Pendant que la réponse s'écrit, aucune source n'est encore arrivée : c'est la
        deuxième forme qu'on voit.
      </p>
      <div class="stack">
        <AnswerText
          text="Le délai de rétractation est de quatorze jours [1], et le remboursement intervient sous trente jours [2]. La note [7] du document n'est pas une source."
          :sources="ANSWER_SOURCES"
        />
        <AnswerText text="Le délai de rétractation est de quatorze jours [1]." :sources="[]" />
      </div>
    </section>

    <section>
      <h2>Sources d'une réponse — AnswerSources</h2>
      <p class="muted">
        Une entrée par source citée, dépliable. Suivre un <code>[n]</code> dans le texte ouvre
        l'entrée correspondante : l'état est tenu par l'écran, pas par le composant.
      </p>
      <AnswerSources
        :sources="ANSWER_SOURCES"
        :opened="openedSources"
        @update:opened="openedSources = $event"
      />
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

.radius-sample {
  display: inline-block;
  width: 3rem;
  height: 1.5rem;
  border: 1px solid var(--p-content-border-color);
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
