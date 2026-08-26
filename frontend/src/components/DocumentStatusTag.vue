<script setup>
import Tag from 'primevue/tag'

// Le statut voyage en code, comme tout ce que l'API sérialise d'une énumération ; le
// libellé est une affaire d'écran, et cette copie est assumée — ADR-0022. Il vit ici et
// non dans une vue parce que deux écrans l'affichent : la liste et le détail. Le motif
// d'échec, lui, vient du serveur et s'affiche tel quel — le front n'en réécrit aucun.
const LABELS = {
  PENDING: 'En attente de traitement',
  EXTRACTED: 'Texte extrait',
  FAILED: 'Traitement en échec',
}

// La sévérité est une décision de rendu, pas une donnée : « en attente » n'est ni un
// succès ni une erreur.
const SEVERITIES = {
  PENDING: 'secondary',
  EXTRACTED: 'success',
  FAILED: 'danger',
}

defineProps({
  status: { type: String, required: true },
})
</script>

<template>
  <Tag :value="LABELS[status] ?? status" :severity="SEVERITIES[status] ?? 'secondary'" />
</template>
