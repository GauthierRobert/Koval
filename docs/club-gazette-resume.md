# Club Gazette — Résumé des fonctionnalités

Inspirée de Famileo et adaptée à un club sportif. Une "édition" périodique du club, **publiée manuellement** par un owner/admin/coach via Claude (pas de scheduler), à laquelle les membres peuvent contribuer entre-temps via des posts qui ne sont révélés qu'à la publication. La cadence cible est hebdomadaire mais reste à la discrétion de l'admin.

## Cadence et cycle

- Pas de scheduler. **Publication entièrement manuelle**, déclenchée par un propriétaire / admin / coach via Claude.
- Cadence cible : hebdomadaire, mais **non imposée** — le déclencheur choisit la période couverte par chaque édition au moment de la publication.
- Période *suggérée* par défaut : `[dimanche 23h59 semaine N → dimanche 23h59 semaine N+1)` (semaine calendaire complète Mon-Sun). Le déclencheur peut la modifier.
- Les brouillons sont créés automatiquement à la lecture (lazy) lorsqu'un membre veut contribuer. Plusieurs brouillons peuvent coexister (par exemple si une publication a pris du retard).
- **Curation via Claude** : au moment de publier, le propriétaire/admin/coach utilise Claude pour :
  - lister les brouillons et les posts en attente
  - choisir explicitement quels posts inclure ou exclure
  - choisir quelles sections auto-curées inclure (stats, leaderboard, top séances, jalons, membres actifs)
  - ajuster la période couverte
  - générer la PDF
  - publier
- Aucune notification automatique de "brouillon prêt à publier" — le déclencheur agit quand il le souhaite.

## Contenu auto-généré (gelé à la publication)

Chaque édition agrège automatiquement, à partir des données existantes du club :

- **Statistiques de la semaine** : km nage / vélo / course, heures totales, TSS total, nombre de séances.
- **Classement (leaderboard)** : top membres par TSS sur la période.
- **Top séances club** : séances avec le plus de participants pendant la période.
- **Membres les plus actifs** : top 5 par heures cumulées.
- **Jalons (milestones)** : records personnels, premières séances, anniversaires de membres, courses terminées.

Pas d'éditorial humain. Pas d'image de couverture choisie manuellement (bandeau auto-généré : numéro d'édition + dates + logo du club + titre statistique).

## Posts membres (contributions)

- Chaque membre actif peut publier des posts liés à un brouillon ouvert. Plusieurs brouillons peuvent coexister ; le composeur permet de choisir explicitement la cible.
- **Visibilité** : un post de brouillon n'est visible que par son auteur et par les admins/coachs/propriétaire (pour modération). Les autres membres ne voient qu'un compteur agrégé. À la publication, tous les posts deviennent visibles à tous les membres et deviennent en lecture seule.
- Un post a obligatoirement un **type** parmi :
  - `SESSION_RECAP` — débrief d'une séance club (lien obligatoire vers une `ClubTrainingSession` de la période)
  - `RACE_RESULT` — résultat de course (lien obligatoire vers un `RaceGoal` dont la course a eu lieu dans la période)
  - `PERSONAL_WIN` — record personnel / accomplissement (sans lien)
  - `SHOUTOUT` — coup de chapeau à un coéquipier (sans lien)
  - `REFLECTION` — réflexion / résumé de semaine (sans lien)
- Champs : titre court (optionnel), contenu texte (markdown autorisé, plafonné à 2000 caractères).
- **Photos** : jusqu'à 4 photos par post (champ `mediaIds`). Stockage via Google Cloud Storage — voir section dédiée.
- Les détails du lien (titre de séance, date de course, etc.) sont **figés (snapshot)** au moment de la publication, pour que la lecture historique reste cohérente même si la séance ou le goal sont supprimés plus tard.

## Génération et curation via Claude/MCP

- La PDF n'est **pas générée par le serveur**. Le déclencheur (propriétaire/admin/coach) ouvre Claude et conduit le flow de curation puis de publication.
- Outils MCP exposés pour Claude :
  - `listClubGazetteEditions(clubId, limit)` — éditions passées (références).
  - `listOpenDrafts(clubId)` — brouillons ouverts.
  - `getGazettePayload(editionId)` — renvoie le contenu structuré (stats live + posts + liens résolus + photos résolues).
  - `previewAutoSections(clubId, periodStart, periodEnd)` — recalcule les sections auto-curées sur une période arbitraire (utilisée si l'admin ajuste les bornes).
  - `publishGazetteWithPdf(editionId, request)` — publication atomique. Le `request` contient :
    - `pdfBase64`, `filename`
    - `includedPostIds: string[]` — la liste **explicite** des posts à inclure (curation ; les autres posts du brouillon ne sont pas publiés)
    - `includedSections: string[]` — drapeaux pour les sections auto (stats, leaderboard, topSessions, milestones, mostActiveMembers)
    - `periodStart`, `periodEnd` — bornes finales retenues (si modifiées par l'admin)
- La PDF est stockée en base (`byte[]`) et servie via `GET /editions/{id}/pdf` (`application/pdf`). Limite : 10 Mo.
- Les posts non inclus restent dans leur brouillon (réutilisables) ou sont supprimables — comportement à choisir au moment de l'implémentation.

## API REST

Sous `/api/clubs/{clubId}/gazette` :

- Lecture des éditions : `GET /editions`, `GET /editions/{id}`, `GET /editions/drafts` (tous les brouillons ouverts), `GET /editions/current` (brouillon de la semaine en cours, créé à la volée si absent).
- PDF : `GET /editions/{id}/pdf`.
- Marquage lu / commentaires sur édition publiée : `POST /editions/{id}/read`, `POST /editions/{id}/comments`.
- Posts membres : `GET|POST /editions/{id}/posts`, `PATCH|DELETE /editions/{id}/posts/{postId}`.
- Mes posts du brouillon courant : `GET /editions/current/posts/mine`.
- Endpoints admin/MCP : `GET /editions/{id}/payload`, `POST /editions/{id}/publish`.

## Frontend (Angular)

- Page **Liste des éditions** : grille des numéros parus.
- Page **Lecteur** : présentation type magazine — bandeau, stats, classement, top séances, jalons, mur des posts groupés par type.
- Page **Composeur** : sélection du type → pickers conditionnels (séance / goal) → titre + contenu.
- Vue **Mes contributions** dans le brouillon courant.
- Carte d'événement `GAZETTE_PUBLISHED` dans le feed live à chaque publication.

## Notifications

- **Aucune** notification automatique de "brouillon prêt" (pas de scheduler).
- Push à tous les membres actifs à la publication ("Édition #N est sortie — découvrez les contributions").

## Permissions

- **Lecture des éditions publiées** : tout membre actif du club.
- **Création/édition/suppression de ses propres posts** : tout membre actif (pendant `DRAFT` uniquement).
- **Suppression de posts d'autres membres (modération)** : admin/coach/propriétaire.
- **Déclenchement de la publication via Claude** (curation + génération PDF + publication) : admin/coach/propriétaire.

## Stockage des photos (Google Cloud Storage)

- Solution : **Google Cloud Storage (GCS)**, l'équivalent direct d'Amazon S3 chez Google. S'intègre naturellement à la stack GCP déjà en place (Pub/Sub, FCM).
- Un bucket privé par environnement : `koval-media-{env}`.
- Pattern d'upload : URL signée PUT (V4) émise par le backend, le client upload directement vers GCS (le serveur n'est pas dans le chemin du transfert → pas de coûts de bande passante).
- Pattern de lecture : URL signée GET de longue durée (≈ 24h, suffisamment long pour bénéficier du cache CDN), régénérée à chaque consultation.
- MIME autorisés : `image/jpeg`, `image/png`, `image/webp`, `image/heic`. Taille max 8 Mo.
- Nouvelle entité `Media` (collection Mongo) qui référence l'objet GCS et son owner. Les posts (gazette ou feed) référencent ces `Media` par leur `mediaId`.
- Workflow client :
  1. `POST /api/media/upload-url` → reçoit `{ mediaId, signedUrl }`
  2. `PUT signedUrl` (upload direct GCS)
  3. `POST /api/media/{mediaId}/confirm` → backend valide la présence sur GCS, lance le pipeline d'optimisation (variantes + BlurHash)
  4. Le `mediaId` est utilisé dans la création du post

## Affichage rapide des photos (perfs façon Instagram / Facebook)

Pour que les photos s'affichent instantanément (rendu perçu < 50 ms) on combine plusieurs techniques :

1. **Variantes multi-résolution générées à l'upload** : `thumb` (240w), `small` (480w), `medium` (960w), `large` (1920w). Le frontend ne télécharge que la taille adaptée au viewport via `<picture>` + `srcset`.
2. **Format moderne WebP** par défaut (≈ 30 % plus léger que JPEG), avec fallback JPEG si nécessaire. EXIF strippé, qualité 80.
3. **Cloud CDN devant le bucket GCS** (`cdn.koval.app`). Edge caching : la première requête atteint l'origine, toutes les suivantes sont servies depuis le POP le plus proche.
4. **Headers de cache agressifs** : `Cache-Control: public, max-age=31536000, immutable`. Les noms d'objets sont des UUID immuables, donc rien ne change jamais.
5. **BlurHash** (~30 caractères) calculé à l'upload et stocké sur le `Media`. Le frontend rend instantanément un fond flou avant que la vraie image arrive — zéro latence perçue.
6. **Lazy loading** (`loading="lazy"`, IntersectionObserver) : seules les images visibles téléchargent.
7. **Dimensions stockées** : `width` / `height` sur le `Media`. Le frontend réserve la place via CSS `aspect-ratio` → pas de saut de mise en page.
8. **Preconnect** sur le CDN dans `index.html`.
9. **Composant Angular `<koval-image>`** réutilisable qui encapsule tout cela : prend un `Media`, choisit la variante, rend le BlurHash, gère le `<picture>`/`srcset`.

API : la réponse `Media` inclut directement `blurHash`, `width`, `height` et un dictionnaire `variants` avec une URL signée par taille.

## Photos sur les posts du feed (enrichissement)

- Les membres peuvent désormais **attacher des photos aux événements existants du feed** (typiquement à un `SESSION_COMPLETION` : "voici une photo de la sortie").
- Plusieurs membres peuvent enrichir le même événement. Chaque photo conserve l'auteur de l'enrichissement.
- Endpoints : `POST /api/clubs/{clubId}/feed/{eventId}/photos` (attacher), `DELETE /api/clubs/{clubId}/feed/{eventId}/photos/{mediaId}` (auteur ou admin).
- Visibilité : identique à l'événement parent.

## Hors périmètre v1 (à prévoir plus tard)

- Publication automatique / scheduler (la publication reste manuelle via Claude).
- Format AVIF (encore plus léger que WebP, à ajouter quand le support navigateur sera total).
- Modération automatique des images (détection NSFW).
- Vidéos.
- Éditorial humain (introduction rédigée par un coach).
- Génération de PDF côté serveur.
- UI in-app dédiée à la curation (la curation passe pour l'instant uniquement par Claude).
