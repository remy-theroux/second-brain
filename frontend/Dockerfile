# syntax=docker/dockerfile:1

###############################################################################
# Étape 1 — build : produit les fichiers statiques de la SPA.
###############################################################################
FROM node:24-alpine AS build
WORKDIR /app

# Descripteurs d'abord : les dépendances restent en cache tant que le lock ne change pas.
# npm ci et non npm install : le lock est la référence.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

###############################################################################
# Étape 2 — runtime : nginx ne sert que du statique, il ne proxifie rien.
# Le routage vers l'API est l'affaire du reverse proxy placé devant (Traefik en
# développement, Coolify en production).
###############################################################################
FROM nginx:alpine AS runtime

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
