# Évaluation de la qualité des réponses — baseline v1

> **Ce fichier est versionné, et c'est tout son intérêt.** Il ne sert à rien s'il n'est pas rejoué
> à l'identique en v1.1, quand la recherche hybride arrivera. Sans mesure prise **avant**, il sera
> impossible de dire si elle améliore quoi que ce soit — et l'intuition, sur un RAG, se trompe
> systématiquement.

## Ce que ce fichier n'est pas

Ce n'est pas une suite de tests. Rien ici ne s'exécute, rien n'échoue en CI. La notation est
**manuelle et assumée comme telle** : évaluer automatiquement demanderait un LLM-as-judge, qui est
hors périmètre et qui déplacerait simplement le problème de confiance.

## Le corpus

L'évaluation ne vaut que sur **de vrais documents personnels**. Un corpus fabriqué mesurerait la
capacité du système à retrouver ce qu'on y a mis exprès, ce qui n'apprend rien.

| | |
|---|---|
| Nombre de documents | *(à remplir — cible : 50)* |
| Formats représentés | *(à remplir : combien de PDF, DOCX, MD, TXT)* |
| Date du passage | *(à remplir)* |
| Modèle d'embedding | `bge-m3`, 1024 dimensions |
| Modèle de génération | `qwen3:4b` |
| Commit évalué | *(à remplir : le SHA, pour que le passage soit rejouable)* |

**Le commit compte autant que le corpus.** Un score sans le SHA du code évalué n'est comparable à
rien.

## Les dix questions

Comment les choisir, pour que la mesure serve à quelque chose :

- **Au moins trois questions dont la réponse tient dans un seul passage d'un seul document.** Ce
  sont les cas faciles ; s'ils échouent, le problème est en amont de la recherche.
- **Au moins trois qui demandent de croiser deux documents.** C'est là que le découpage et le
  nombre d'extraits rendus (huit, `SearchPolicy.RESULTS`) se font sentir.
- **Au moins deux dont la réponse est absente du corpus.** L'aveu d'ignorance est un résultat
  correct, et c'est le seul moyen de mesurer l'invention. Sans elles, le score ne dit rien du
  risque le plus grave.
- **Au moins une question conversationnelle** (« qu'est-ce que tu sais faire ? »), qui ne doit
  déclencher aucune recherche — c'est le trou assumé du garde-fou, celui que `CLAUDE.md` signale :
  une réponse sans recherche n'est protégée que par la consigne écrite du prompt.

Éviter les questions dont la formulation reprend mot pour mot une phrase du document : elles
mesurent la recherche lexicale, que ce système ne fait pas.

---

### Q1 — *(à rédiger)*

**Question posée :**

**Réponse attendue :**

**Document et section qui la portent :**

---

### Q2 — *(à rédiger)*

**Question posée :**

**Réponse attendue :**

**Document et section qui la portent :**

---

*(Q3 à Q10 sur le même gabarit.)*

---

## La notation

Trois axes **indépendants** par question, parce qu'ils désignent trois maillons différents :

| Axe | Ce qu'il mesure | Ce qu'un échec désigne |
|---|---|---|
| **Réponse correcte** | Le contenu de la réponse est juste | La génération, ou tout ce qui précède |
| **Source correcte** | Les `[n]` cités désignent bien les passages qui portent la réponse | La citation : le modèle a répondu juste en citant à côté |
| **Extrait pertinent retrouvé** | Le bon passage figurait dans les huit résultats de la recherche | La recherche, le découpage ou l'extraction |

**L'ordre de lecture importe :** si l'extrait pertinent n'était pas dans les résultats, la
génération n'avait aucune chance, et il est inutile d'accuser le modèle. C'est pour ça que le
troisième axe se note en regardant `GET /api/search?q=…` **avec la même question**, avant même de
lire la réponse de l'agent.

### Résultats

| # | Réponse correcte | Source correcte | Extrait retrouvé | Note |
|---|---|---|---|---|
| Q1 | | | | |
| Q2 | | | | |
| Q3 | | | | |
| Q4 | | | | |
| Q5 | | | | |
| Q6 | | | | |
| Q7 | | | | |
| Q8 | | | | |
| Q9 | | | | |
| Q10 | | | | |
| **Total** | **… / 10** | **… / 10** | **… / 10** | |

**Cible du PRD : 8 réponses correctes sur 10.**

## Analyse des échecs

Pour les deux ou trois pires cas, une hypothèse de cause, et une seule des trois :

- **Extraction** — le texte n'est pas dans la base, ou il y est mal. Se vérifie sur
  `GET /api/documents/{id}`, qui montre le texte extrait tel quel. Un PDF numérisé, un tableau, une
  colonne perdue.
- **Découpage** — le texte est là mais l'extrait qui le porte est mal taillé : une réponse coupée
  en deux, ou noyée dans un extrait de 600 tokens dont le reste parle d'autre chose. Se vérifie en
  regardant la position et le texte de l'extrait rendu par la recherche.
- **Recherche** — l'extrait existe et est bien taillé, mais il n'arrive pas dans les huit. Se
  vérifie au score : `GET /api/search` rend la similarité, et un bon extrait à 0,42 derrière huit
  extraits à 0,45 est un diagnostic complètement différent d'un bon extrait absent.

### Échec 1 — *(à remplir)*

**Question :** · **Ce qui a été répondu :** · **Hypothèse :** · **Ce qui l'étaye :**

### Échec 2 — *(à remplir)*

### Échec 3 — *(à remplir)*

## Comment rejouer ce passage

1. Ingérer le même corpus (les mêmes fichiers, dans les mêmes formats).
2. Attendre que tous les documents soient `READY` — un document resté `EXTRACTED` n'est pas
   cherchable, et le compter fausserait le score.
3. Pour chaque question : `GET /api/search?q=…` d'abord, noter si l'extrait pertinent est dans les
   huit ; puis `POST /api/chat`, noter la réponse et ses sources.
4. Remplir un nouveau tableau **sous** celui-ci, avec sa date, son SHA et son modèle. **Ne pas
   écraser le passage précédent** : c'est la comparaison qui a de la valeur, pas le dernier chiffre.
