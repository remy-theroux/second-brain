---
status: accepté
date: 2026-08-04
decision-makers: Rémy Theroux
---

# La politique autorise 128 caractères là où BCrypt en lit 72

## Contexte et problème

`PasswordPolicy` accepte les mots de passe jusqu'à 128 caractères. BCrypt, lui, ignore
silencieusement les octets au-delà du 72e : deux mots de passe qui partagent leurs 72
premiers octets ont la même empreinte.

## Facteurs de décision

- C'est le comportement standard de BCrypt, pas un défaut de cette implémentation.
- Un utilisateur qui saisit plus de 72 octets n'apprend rien de l'écart, et ne perd rien
  d'exploitable : 72 octets de mot de passe sont déjà hors de portée d'une recherche
  exhaustive.
- Abaisser la limite affichée à 72 exposerait un détail de l'algorithme de hachage dans
  une règle métier.

## Options envisagées

- Aligner la politique sur 72 caractères
- Pré-hacher le mot de passe (SHA-256) avant BCrypt, pour couvrir toute la longueur
- Laisser l'écart, et le documenter

## Décision

Retenu : **laisser l'écart**. Le comportement est standard, la conséquence est nulle en
pratique, et les deux parades coûtent plus qu'elles ne rapportent : l'une fait entrer le
nom de l'algorithme dans le domaine, l'autre ajoute une étape de hachage maison au chemin
d'authentification.

### Conséquences

- Bien : `PasswordPolicy` reste une règle métier lisible, sans référence à BCrypt.
- Mal : la limite annoncée à l'utilisateur est plus généreuse que la limite effective.
  Personne ne le remarquera, et c'est précisément ce qui rend l'écart discret.

### Condition de réouverture

Un changement d'algorithme de hachage. Argon2 ou scrypt n'ont pas cette borne : l'écart
disparaîtrait sans qu'on ait rien à décider, et la politique deviendrait exacte.

## Pour aller plus loin

- `users/domain/PasswordPolicy.java`
- `users/infrastructure/security/` — l'adapter du port `PasswordHasher`
