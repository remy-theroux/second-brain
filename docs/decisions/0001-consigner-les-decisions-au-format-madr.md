---
status: accepté
date: 2026-08-26
decision-makers: Rémy Theroux
---

# Consigner les décisions d'architecture au format MADR

## Contexte et problème

Les décisions structurantes de ce projet sont écrites — c'est déjà mieux que la moyenne —
mais elles le sont à trois endroits qui ne disent pas la même chose. `CLAUDE.md` porte le
résultat sous forme de 22 « écarts assumés » numérotés ; `docs/superpowers/specs/` porte
l'étude qui a précédé certaines d'entre elles ; les messages de commit portent le
déclencheur. Aucun des trois ne porte les options écartées.

Le coût est double. Un coût de contexte : `CLAUDE.md` est chargé intégralement à chaque
session, et les écarts en occupent 136 lignes sur 661, payées que la tâche les concerne
ou non. Un coût de décision : sans trace de ce qui a été écarté et pourquoi, une
alternative déjà rejetée revient sur la table à chaque passage sur le fichier concerné —
et le passage est fait par un agent qui ne se souvient de rien d'une session à l'autre.

## Facteurs de décision

- **Le format doit résister à l'oubli.** Le lecteur principal de ces textes est un agent
  sans mémoire entre deux sessions. Ce qui n'est pas écrit n'existe pas.
- **Le format doit dire « ne pas corriger ».** Une décision consignée ressemble souvent à
  un défaut ; c'est même sa définition ici. Le texte doit couper le réflexe de réparation.
- **Le format doit dire quand rouvrir.** Une décision sans condition de sortie devient un
  dogme, puis une dette qu'on n'ose plus toucher.
- **Le coût de rédaction doit rester payable par une personne seule.**
  `docs/ticket-template.md` a déjà tranché ce type d'arbitrage : « mieux vaut un template
  court réellement rempli qu'un template riche survolé ».
- **Un fichier par décision.** C'est ce qui rend le chargement paresseux possible, et ce
  qui rend l'historique lisible : `git log` sur un fichier raconte une décision.

## Options envisagées

- Statu quo — la section « Écarts assumés » de `CLAUDE.md`
- MADR complet, un fichier par décision
- MADR court — contexte, décision, conséquences
- ADR de Nygard — le format d'origine, quatre sections
- Y-statements — une décision par phrase à trous

## Décision

Retenu : **MADR complet, un fichier par décision, dans `docs/decisions/`**, parce que
c'est le seul des cinq qui impose d'écrire les options écartées — et que c'est
précisément ce qui manque aujourd'hui.

Trois règles de mise en œuvre viennent avec :

1. **`CLAUDE.md` ne garde qu'un index** : une ligne par ADR, sous l'avertissement « ne pas
   corriger spontanément ». Le raisonnement vit dans le fichier d'ADR, lu à la demande.
2. **Un ADR accepté ne se modifie pas** : il se remplace. Le nouveau porte
   `status: accepté`, l'ancien passe à `remplacé par ADR-XXXX`. Une décision réécrite en
   place efface la trace de ce qu'on croyait au moment où on l'a prise.
3. **Le gabarit ajoute une section « Condition de réouverture »**, absente de MADR. Sans
   elle, un ADR n'apprend pas la seule chose qu'un agent ait besoin de savoir : ai-je le
   droit de proposer autre chose aujourd'hui ?

Les 22 écarts existants migrent en bloc, dans l'ordre : l'écart n° X devient
ADR-000(X+1). Le décalage constant rend tout ancien renvoi mécaniquement traduisible, y
compris ceux qui traînent dans un commit ou une conversation passée.

### Conséquences

- Bien : `CLAUDE.md` perd 136 lignes et n'en reprend qu'une trentaine — le budget de
  contexte cesse de croître avec le nombre de décisions.
- Bien : chaque décision porte enfin une date, un statut et ce qu'elle a écarté. Le
  « pourquoi pas X » cesse d'être reperdu à chaque session.
- Bien : une décision se cite par un identifiant stable (`ADR-0020`) depuis un Javadoc, un
  `application.yml` ou un message de commit, au lieu d'un numéro d'ordre dans une liste
  qui bouge.
- Mal : le raisonnement n'est plus sous les yeux par défaut. Un ADR non ouvert est un ADR
  non lu — l'index doit donc rester assez descriptif pour donner envie de l'ouvrir, ce qui
  est un travail de rédaction à chaque ajout.
- Mal : une décision se paie désormais en un fichier, un numéro et une ligne d'index. Le
  réflexe « je note ça dans `CLAUDE.md` » disparaît, et le risque est que des décisions
  cessent d'être écrites du tout.
- Mal : deux endroits peuvent diverger — l'index et le titre de l'ADR. C'est la même
  nature de dette qu'ADR-0017 et ADR-0022 ; elle est bornée à une ligne par fichier et se
  vérifie au grep (voir « Vérification »).

### Condition de réouverture

Si le nombre d'ADR dépasse ce qu'un index de `CLAUDE.md` peut porter sans redevenir le
problème qu'on résout ici — de l'ordre de cinquante — l'index devra être remplacé par un
sommaire généré, ou découpé par contexte borné. Si à l'inverse aucun ADR n'est écrit
pendant deux features consécutives alors que des décisions ont été prises, c'est que le
coût de rédaction est trop élevé : le gabarit court redevient candidat.

## Avantages et inconvénients des options

### Statu quo — la section « Écarts assumés » de `CLAUDE.md`

- Bien : coût de rédaction nul, tout est déjà là, aucun renvoi à réécrire.
- Bien : le texte est sous les yeux à chaque session, sans avoir à l'ouvrir.
- Mal : le contexte payé croît linéairement avec le nombre de décisions.
- Mal : aucune date, aucun statut, aucune option écartée — et rien dans le format n'invite
  à les écrire.
- Mal : le numéro d'ordre est l'identité. Supprimer l'écart n° 7 renumérote les quinze
  suivants et invalide tous les renvois.

### MADR complet, un fichier par décision — *retenu*

- Bien : les options envisagées et leurs avantages/inconvénients sont des sections du
  gabarit, donc leur absence se voit.
- Bien : `status` et `date` en front matter, remplacement traçable par
  `remplacé par ADR-XXXX`.
- Bien : format répandu, conventions stables, aucune dépendance à un outil.
- Mal : le plus verbeux des cinq. Une décision de dix lignes en coûte quarante.
- Mal : plusieurs sections sont sans objet à un seul décideur (`consulted`, `informed`) —
  d'où leur marquage explicite en optionnel dans le gabarit du projet.

### MADR court

- Bien : rapide à écrire, donc réellement écrit.
- Mal : la liste des options y figure sans les raisons de leur rejet — c'est-à-dire que le
  seul manque identifié au départ n'est pas comblé.

### ADR de Nygard

- Bien : quatre sections, format historique, très largement compris.
- Mal : « Decision » et « Consequences » ne prévoient aucune place pour les alternatives.
  On retomberait sur une prose libre, comme aujourd'hui.
- Mal : pas de front matter, donc pas de statut ni de date exploitables sans une
  convention ajoutée à la main.

### Y-statements

- Bien : une décision tient en une phrase structurée, imbattable en coût d'écriture.
- Mal : la forme contrainte force le raisonnement dans un moule qui ne convient pas aux
  décisions de ce dépôt, dont plusieurs tiennent leur valeur d'une nuance longue
  (ADR-0003 sur CSRF, ADR-0023 sur la publication d'événements).

## Vérification

- Aucune occurrence de « écart n° » ne subsiste dans le dépôt, hors du présent ADR — qui
  porte la correspondance avec l'ancienne numérotation et doit donc la citer.
- Tout `ADR-XXXX` cité dans le code ou la documentation correspond à un fichier de
  `docs/decisions/`.
- Tout fichier de `docs/decisions/` autre que le gabarit apparaît dans l'index de
  `CLAUDE.md`.

Les trois se vérifient au grep ; ils ne sont pas accrochés à la CI, qui n'a pas à arbitrer
de la documentation.

## Pour aller plus loin

- MADR — <https://adr.github.io/madr/>
- `docs/decisions/0000-adr-template.md` — le gabarit du projet
- `.claude/rules/decisions.md` — quand écrire un ADR et comment le référencer
- `docs/superpowers/specs/` — les études qui précèdent certaines de ces décisions ; un ADR
  y renvoie plutôt que de les recopier
