// The status travels as a code, like everything the API serialises from an enum; the label is
// a screen matter, and this copy is assumed — ADR-0022. The failure reason, on the other hand,
// comes from the server and is displayed as is — the front rewrites none.
export const STATUS_LABELS = {
  PENDING: 'En attente de traitement',
  EXTRACTED: 'Texte extrait',
  READY: 'Prêt à être interrogé',
  FAILED: 'Traitement en échec',
}

// The severity is a rendering decision, not data: "pending" is neither a success nor an error.
export const STATUS_SEVERITIES = {
  PENDING: 'secondary',
  EXTRACTED: 'info',
  READY: 'success',
  FAILED: 'danger',
}

const SETTLED = ['READY', 'FAILED']

export function isSettled(status) {
  return SETTLED.includes(status)
}

export function hasUnsettled(documents) {
  return documents.some((document) => !isSettled(document.status))
}
