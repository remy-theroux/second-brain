# Règles des décisions d'architecture — ADR

Les décisions structurantes de ce dépôt vivent dans `docs/decisions/`, un fichier par
décision, au format MADR. Le gabarit est `docs/decisions/0000-adr-template.md` ; le
pourquoi du dispositif est dans ADR-0001, qui est lui-même un ADR.

## Lire avant d'écrire

- **Un ADR n'est pas un défaut à réparer.** L'index de `CLAUDE.md` liste des décisions
  prises, pas des bugs en attente. Avant de proposer autre chose sur un sujet couvert,
  ouvrir l'ADR : il porte les options déjà écartées et la condition à laquelle on rouvre.
- Si la condition de réouverture est remplie, on ne modifie pas l'ADR existant : on en
  écrit un nouveau qui le remplace (voir « Remplacer »).

## Aucun ADR sans accord préalable

**Ne jamais écrire un ADR sans me consulter d'abord.** Ni en cours de tâche, ni parce qu'un
plan en annonçait un, ni parce que le choix paraît évidemment structurant.

Un ADR n'est pas de la documentation : c'est une décision qui engage le projet, et qui,
une fois acceptée, ne se modifie plus — elle se remplace. La rédiger seul revient à décider
seul, puis à laisser une trace qui a l'autorité d'un arbitrage qui n'a pas eu lieu.

La marche à suivre quand un ADR semble dû :

1. Le signaler, en une phrase : la décision à prendre, et l'alternative qu'elle ferme.
2. Attendre l'accord — sur le principe **et** sur l'option retenue.
3. Écrire l'ADR ensuite, dans le commit du code qu'il justifie.

Un plan d'implémentation peut **prévoir** qu'un ADR sera dû : il ne vaut pas accord pour
l'écrire. L'accord se donne au moment de la décision, pas à l'approbation du plan.

Si le travail ne peut pas attendre, écrire le code **sans** l'ADR et le dire : un ADR
manquant se rattrape, un ADR écrit d'autorité oriente toutes les relectures qui suivent.

## Quand un ADR est dû

Ces critères disent quand la question se pose. Ils ne dispensent pas de la poser — voir
ci-dessus.

- Quand un choix **ferme une alternative crédible** et qu'un lecteur futur pourrait
  raisonnablement proposer l'autre. C'est le seul critère.
- Pas pour un choix sans alternative, pas pour une règle de style : celles-là vont dans
  `backend.md` ou `frontend.md`.
- Une contrainte subie (une version imposée par un BOM, un comportement de bibliothèque)
  n'est pas une décision. Elle se documente là où elle mord, pas en ADR.

## Rédiger

- **Le titre est la décision à l'affirmative**, pas le sujet : « Le système de fichiers ne
  participe à aucune transaction », pas « Transactions et fichiers ». Il doit se lire seul
  dans l'index et suffire à décider s'il faut ouvrir le fichier.
- **Numérotation continue sur quatre chiffres**, jamais réattribuée. Un ADR supprimé
  laisse son trou.
- Nom de fichier : `<numéro>-<titre-en-kebab-sans-accent>.md`, comme les slugs de
  `docs/superpowers/plans/`.
- Les clés du front matter restent en anglais, les valeurs sont en français — comme le
  reste du projet, où seuls les identifiants de production sont anglais.
- **« Condition de réouverture » se remplit toujours.** « Jamais » est une réponse valable
  à condition d'être écrite. Une décision sans condition de sortie devient un dogme.
- **Un ADR ne recopie pas une spec.** `docs/superpowers/specs/` porte l'étude, l'ADR porte
  la décision et y renvoie.

## Référencer

- **Se citer par identifiant** — `ADR-0020` — depuis un Javadoc, un commentaire de
  configuration, un message de commit ou un autre ADR. Jamais par un chemin relatif, qui
  casserait au renommage du slug ; jamais par l'ancienne numérotation des « écarts
  assumés », qui n'existe plus.
- **Le même commit porte l'ADR, sa ligne d'index dans `CLAUDE.md` et le code concerné.**
  Un ADR qui arrive au commit suivant est un ADR écrit après coup pour justifier.
- L'index de `CLAUDE.md` est le seul index. Ne pas ajouter de `README.md` dans
  `docs/decisions/` : deux index divergent, c'est exactement la dette d'ADR-0017 et
  d'ADR-0022.

## Remplacer

- **Un ADR accepté ne se modifie pas.** Le nouveau porte `status: accepté` et renvoie vers
  l'ancien ; l'ancien passe à `status: remplacé par ADR-XXXX` et rien d'autre ne change
  chez lui. Réécrire en place efface la trace de ce qu'on croyait au moment de décider.
- Seules les coquilles et les liens morts se corrigent en place.
- L'index de `CLAUDE.md` ne liste pas les ADR remplacés : ils restent lisibles par leur
  fichier et par les renvois du nouvel ADR.
