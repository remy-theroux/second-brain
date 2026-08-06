# Ticket dev IA — format standard

Format de ticket destiné à être développé par une IA (Claude Code) sans
aller-retour. Générique : utilisable sur n'importe quel projet.

Cinq sections, pas une de plus. Un ticket à moitié rempli produit une IA qui
invente — mieux vaut un template court réellement rempli qu'un template riche
survolé.

---

## Mode d'emploi

**Titre** = verbe à l'infinitif + objet, une seule intention.
S'il faut un « et » dans le titre, c'est deux tickets.

**La barrière qualité, c'est le statut.** Un ticket ne passe en `Prêt` que si les
cinq sections sont remplies. C'est ce qui remplace une checklist de Definition of
Ready — inutile de la répéter dans chaque ticket.

**Écriture du Gherkin :**

1. Déclaratif, pas impératif. « Quand je soumets une note sans titre », pas
   « Quand je clique sur le bouton bleu ». On décrit l'intention métier, pas
   l'implémentation — sinon le ticket fige des choix de conception.
2. Un seul `Quand` par scénario. Deux actions = deux scénarios.
3. 2 à 5 scénarios par ticket : le nominal, plus les cas d'erreur qui portent une
   vraie règle métier. Au-delà de 5, le ticket est trop gros.
4. Ce qui n'est pas observable côté métier ne fait pas un scénario — ça va dans
   **Contraintes**.

**Ce qu'on n'ajoute pas, volontairement :**

- *User story « En tant que… je veux… afin de… »* — redondante avec Contexte +
  Objectif, et remplie mécaniquement neuf fois sur dix.
- *Critères d'acceptation en liste à puces* — doublon du Gherkin ; l'un des deux
  finit désynchronisé.
- *Estimation / story points* — sans valeur pour un développement par IA.
- *Definition of Done* — elle appartient au `CLAUDE.md` du repo (tests verts,
  conventions de commit…). Un ticket ne redit pas les règles du projet.
- *Section « solution technique »* — si elle est remplie, on écrit le code dans
  le ticket. Une contrainte d'implémentation réellement imposée tient en une puce
  de **Contraintes**.

---

## Propriétés de la base Notion

| Propriété | Type | Valeurs |
|---|---|---|
| Nom | Titre | verbe + objet |
| Statut | Statut | Backlog → Prêt → En cours → En revue → Fait |
| Type | Select | Feature / Bug / Tech / Spike |
| Projet | Select (ou Relation) | second-brain, hermes, openclaw… |
| Priorité | Select | Haute / Moyenne / Basse |
| Lien PR | URL | rempli après coup |

Mise en place : créer une base « Tickets » en vue Tableau, ajouter ces
propriétés, puis créer une page contenant le corps ci-dessous et la transformer
en **modèle** (« Ticket dev IA », défini par défaut). Le bloc Gherkin se saisit
comme bloc de code, langage `Gherkin` (ou `Plain text` s'il n'est pas proposé).

---

# ▼ Template — copier à partir d'ici

## Contexte

_3 à 5 lignes. Où on en est, pour qui, pourquoi maintenant. Le « pourquoi », pas
le « comment » : c'est ce qui permet à l'IA d'arbitrer les zones grises sans
poser de question._

## Problème / Objectif

_1 à 2 phrases : ce qui ne va pas aujourd'hui, ou ce qu'on veut permettre._

**Réussi si :** _une condition observable de l'extérieur._

## Attendus métier

```gherkin
Fonctionnalité: <intitulé métier>

  Scénario: <cas nominal>
    Étant donné <état initial>
    Quand <une seule action>
    Alors <résultat observable>

  Scénario: <cas d'erreur ou cas limite>
    Étant donné <...>
    Quand <...>
    Alors <...>
```

## Contraintes & hors-périmètre

- _contrat d'API / format attendu, si applicable_
- _règle de sécurité, perf, compatibilité_
- **Hors-périmètre :** _ce qu'on ne fait PAS dans ce ticket_

## Pointeurs

- Code : `<chemin/fichier>` — _là où ça se greffe_
- Voir aussi : _ticket ou doc liée_

# ▲ Template — fin

---

## Exemple rempli

> **Nom** : Paginer la liste des notes · **Type** : Feature · **Projet** :
> second-brain · **Priorité** : Moyenne · **Statut** : Prêt

### Contexte

`GET /api/notes` renvoie aujourd'hui toutes les notes en une fois. En usage
réel la base grossit vite (c'est le point d'entrée de tout le second brain), et
la réponse devient inexploitable côté client comme côté réseau. On veut poser la
pagination maintenant, tant qu'aucun client externe ne dépend du format actuel.

### Problème / Objectif

La liste des notes n'est pas paginée : temps de réponse et taille de payload
croissent linéairement avec le nombre de notes.

**Réussi si :** un appel sur une base de 1 000 notes renvoie au plus 20 notes et
indique au client comment obtenir la suite.

### Attendus métier

```gherkin
Fonctionnalité: Pagination de la liste des notes

  Scénario: Première page par défaut
    Étant donné 45 notes existantes
    Quand je demande la liste des notes sans préciser de page
    Alors je reçois les 20 notes les plus récentes
    Et je sais qu'il y a 45 notes au total et 3 pages

  Scénario: Page suivante
    Étant donné 45 notes existantes
    Quand je demande la page 3 avec une taille de page de 20
    Alors je reçois les 5 notes restantes

  Scénario: Page au-delà des résultats
    Étant donné 45 notes existantes
    Quand je demande la page 10
    Alors je reçois une liste vide, pas une erreur

  Scénario: Taille de page hors limites
    Étant donné 45 notes existantes
    Quand je demande une taille de page de 500
    Alors la demande est refusée avec un message expliquant la limite
```

### Contraintes & hors-périmètre

- Paramètres `page` (0-indexé, défaut 0) et `size` (défaut 20, max 100).
- Tri par date de création décroissante ; le tri n'est pas paramétrable.
- La réponse expose `content`, `page`, `size`, `totalElements`, `totalPages`.
- **Hors-périmètre :** recherche, filtres, curseur/keyset pagination.

### Pointeurs

- Code : `src/main/java/xyz/sterenn/secondbrain/note/NoteController.java`
- Code : `src/main/java/xyz/sterenn/secondbrain/note/NoteRepository.java`
- Tests : suivre le pattern Testcontainers de
  `src/test/java/xyz/sterenn/secondbrain/TestcontainersConfiguration.java`
