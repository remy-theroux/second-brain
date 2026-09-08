// Google sends the browser back to the server, which redirects here with a code, not a
// message: it is a navigation, and making the text travel in a query string would stick it
// into the browser history and into the proxy logs (ADR-0007, ADR-0017). So the labels live
// here, as `VERIFICATION_MESSAGES` does in LoginView. The severity travels with them: a
// screen must not have to deduce it from the code.
const DRIVE_MESSAGES = {
  ok: { text: 'Votre compte Google Drive est connecté.', severity: 'success' },
  refus: { text: "L'accès à Google Drive n'a pas été autorisé.", severity: 'error' },
  'lien-invalide': { text: "Ce retour de connexion n'est pas valide.", severity: 'error' },
  echec: { text: 'La connexion à Google Drive a échoué.', severity: 'error' },
}

// A code the front does not know is still a code: staying silent would let a failed
// authorization pass for a successful one.
const UNKNOWN_OUTCOME = {
  text: "La connexion à Google Drive n'a pas abouti.",
  severity: 'error',
}

export function driveMessage(code) {
  if (!code) {
    return null
  }
  return DRIVE_MESSAGES[code] ?? UNKNOWN_OUTCOME
}
