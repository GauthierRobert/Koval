# Club Gazette — Plan d'implémentation détaillé

Document de référence technique pour l'implémentation. Lire en parallèle de `club-gazette-resume.md`.

## Hypothèses et contraintes confirmées

- **Pas de scheduler.** Publication **entièrement manuelle**, déclenchée par un owner/admin/coach via Claude.
- Cadence cible : hebdomadaire, mais non imposée. Le déclencheur fixe la période couverte au moment de la publication.
- Période *suggérée* par défaut : `[dimanche 23h59 semaine N, dimanche 23h59 semaine N+1)`. Le déclencheur peut la modifier au moment de publier.
- Plusieurs brouillons peuvent coexister (lazy creation à la première contribution sur une période donnée).
- **Curation via Claude** : au moment de publier, le déclencheur passe par Claude pour choisir explicitement quels posts inclure, quelles sections auto-curées activer, et quelles bornes retenir. La sélection est passée au backend dans la requête `publish`.
- Aucun éditorial humain libre. Le contenu auto-curé est dérivé de `ClubStatsService`, `ClubTrainingSessionRepository`, `CompletedSessionRepository`, `RaceGoalRepository`.
- Upload de photos via **Google Cloud Storage** (cf. section dédiée). Les posts gazette portent jusqu'à 4 photos. Le feed existant reçoit également un mécanisme d'enrichissement par photos.
- PDF générée par Claude, stockée en `byte[]` dans le document de l'édition (cohérent avec `RecurringSessionTemplate.gpxData` et `ClubTrainingSession.gpxData`).
- Posts visibles uniquement à leur auteur (et aux admins/coachs/owner pour modération) tant que l'édition est en `DRAFT`. Après publication, seuls les posts **inclus dans la curation** deviennent visibles à tous les membres.

## Stockage des photos — Google Cloud Storage

### Choix de la solution

**Google Cloud Storage (GCS)** est l'équivalent direct d'Amazon S3 chez Google : object storage, buckets, URLs signées, lifecycle policies, IAM. Le backend utilise déjà GCP (`google-cloud-pubsub`, `google-auth-library-oauth2-http` pour FCM). On reste sur le même cloud.

Dépendance Maven à ajouter dans `backend/pom.xml` :

```xml
<dependency>
    <groupId>com.google.cloud</groupId>
    <artifactId>google-cloud-storage</artifactId>
</dependency>
```

(ou `spring-cloud-gcp-starter-storage` si on préfère le wrapper Spring).

### Bucket et nommage

- Un bucket privé par environnement : `koval-media-prod`, `koval-media-dev`.
- Convention de nommage des objets : `{purpose}/{clubId|userId}/{yyyy}/{mm}/{uuid}.{ext}`.
  - Exemple : `gazette-post/abc123/2026/04/550e8400-e29b-41d4-a716-446655440000.jpg`
- Lifecycle policy GCS : suppression automatique des objets non confirmés (label `confirmed=false`) après 24h.
- IAM : seul le service account de l'application a accès en lecture/écriture. Bucket `uniformBucketLevelAccess` activé. Aucun objet public.

### Pattern d'upload

1. Client : `POST /api/media/upload-url` avec `{ purpose, contentType, sizeBytes, clubId? }`.
2. Backend :
   - Authentifie l'utilisateur, valide MIME & taille.
   - Génère un `mediaId` (UUID), construit l'`objectName`.
   - Crée un document `Media` avec `confirmed=false`.
   - Émet une URL signée PUT V4 valide 15 min, avec conditions sur `Content-Type` et `Content-Length`.
   - Retourne `{ mediaId, signedUrl, objectName, expiresAt }`.
3. Client : `PUT signedUrl` avec le binaire.
4. Client : `POST /api/media/{mediaId}/confirm`.
5. Backend :
   - Vérifie que l'objet existe et a la bonne taille via `Storage.get(BlobId.of(...))`.
   - Lit `width` / `height` (via Thumbnailator ou `ImageIO`).
   - Passe `confirmed=true`.

Tant que `confirmed=false`, le `mediaId` ne peut pas être utilisé dans un post.

### Pattern de lecture

- Backend émet des URLs signées GET V4 valides **24h** (TTL longue choisie pour maximiser le hit-rate du CDN ; la signature reste dans le path donc le cache key reste stable pendant la TTL).
- Réponse DTO : chaque `Media` est résolu en un objet riche :
  ```json
  {
    "mediaId": "...",
    "blurHash": "L6PZfSi_.AyE_3t7t7R**0o#DgR4",
    "width": 4032,
    "height": 3024,
    "variants": {
      "thumb":  { "url": "https://cdn.koval.app/.../thumb.webp",  "width": 240,  "height": 180 },
      "small":  { "url": "https://cdn.koval.app/.../small.webp",  "width": 480,  "height": 360 },
      "medium": { "url": "https://cdn.koval.app/.../medium.webp", "width": 960,  "height": 720 },
      "large":  { "url": "https://cdn.koval.app/.../large.webp",  "width": 1920, "height": 1440 }
    },
    "expiresAt": "..."
  }
  ```
- Frontend stocke ces réponses en mémoire courte ; rafraîchit si la session dure plus longtemps que la TTL (rare).

### Performances d'affichage — pipeline d'optimisation

Inspiré directement de Instagram / Facebook. Mis en place dès la confirmation d'upload :

1. **Génération de variantes multi-résolution** (côté serveur, dans `MediaStorageService.processConfirmedUpload`) :
   - Lib recommandée : [Thumbnailator](https://github.com/coobird/thumbnailator) (Java pur, simple) ou `imgscalr`. Pour WebP, utiliser `webp-imageio` ou `TwelveMonkeys ImageIO`.
   - Tailles : `thumb 240w`, `small 480w`, `medium 960w`, `large 1920w` (proportions conservées).
   - L'original n'est jamais servi — supprimable après génération si on veut économiser le stockage.
   - Format de sortie : **WebP qualité 80**. Fallback JPEG seulement si support navigateur insuffisant (rare en 2026).
   - EXIF strippé (privacy + taille).
   - Chaque variante uploadée dans GCS avec le suffixe : `{base}/thumb.webp`, `{base}/small.webp`, etc.
2. **BlurHash** : calcul d'un hash compact (~30 char) représentant un aperçu flou 4×4 ou 8×8. Lib : [`blurhash-java`](https://github.com/woltapp/blurhash). Stocké sur le `Media`.
3. **Cloud CDN devant le bucket** :
   - Configuration GCP : créer un Backend Bucket pointant sur `koval-media-prod`, le rattacher à un Load Balancer HTTPS, activer Cloud CDN dessus.
   - DNS : `cdn.koval.app` → IP du LB.
   - `Cache-Control: public, max-age=31536000, immutable` mis sur chaque objet à l'upload.
   - Les URLs signées sont compatibles avec Cloud CDN (cache key inclut la query string par défaut, mais on peut configurer `IGNORE_QUERY_STRING` pour les URLs durables si on bascule plus tard sur un schéma de tokens applicatifs).
4. **Headers de cache** : posés à l'écriture des objets GCS (champ `cacheControl` sur le `BlobInfo`).
5. **Frontend `<koval-image>`** :
   - Inputs : `media: MediaResponse`, `size?: 'thumb' | 'small' | 'medium' | 'large'` (défaut `medium`).
   - Rend un wrapper avec CSS `aspect-ratio: width / height` pour réserver la place.
   - Fond : BlurHash décodé en data-URL via [`blurhash` npm package](https://www.npmjs.com/package/blurhash).
   - Élément `<img loading="lazy" decoding="async" srcset="...">` avec les 4 variantes pour permettre au navigateur de choisir selon DPR.
   - Sur `load`, fait un fade-out du blur.
6. **Preconnect** dans `index.html` :
   ```html
   <link rel="preconnect" href="https://cdn.koval.app" crossorigin>
   ```

### Modèle `Media` enrichi

```java
@Document(collection = "media")
public class Media {
    @Id private String id;
    @Indexed private String ownerId;
    private String clubId;
    private MediaPurpose purpose;

    private String objectName;            // chemin GCS de l'original
    private String contentType;
    private long sizeBytes;
    private Integer width;
    private Integer height;

    private String blurHash;              // ex. "L6PZfSi_.AyE_3t7t7R**0o#DgR4"
    private List<MediaVariant> variants = new ArrayList<>();
    private MediaProcessingStatus processingStatus;   // PENDING | READY | FAILED

    private boolean confirmed;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime processedAt;

    public record MediaVariant(
        String label,           // "thumb" | "small" | "medium" | "large"
        String objectName,      // chemin GCS de la variante
        int width,
        int height,
        long sizeBytes) {}
}
```

`processingStatus` permet au client de savoir si les variantes sont prêtes. Tant que `READY`, le client peut afficher le BlurHash et l'original (si conservé) — les variantes apparaissent dès traitement terminé.

### Composant Java

```
com.koval.trainingplannerbackend.media/
├── Media.java                          (document Mongo)
├── MediaRepository.java
├── MediaPurpose.java                   (enum: GAZETTE_POST, FEED_POST_ENRICHMENT, AVATAR, ...)
├── MediaStorageService.java            (façade GCS)
├── MediaService.java                   (CRUD + autorisation)
├── MediaController.java                (REST: upload-url, confirm, url)
├── MediaCleanupScheduler.java          (purge des Media non confirmés > 24h)
└── dto/
    ├── RequestUploadUrlRequest.java
    ├── RequestUploadUrlResponse.java
    ├── ConfirmUploadResponse.java
    └── MediaResponse.java
```

### Modèle `Media`

```java
@Document(collection = "media")
@CompoundIndexes({
  @CompoundIndex(name = "owner_purpose_idx", def = "{'ownerId': 1, 'purpose': 1, 'createdAt': -1}")
})
public class Media {
    @Id private String id;
    @Indexed private String ownerId;
    private String clubId;                 // null si non lié à un club
    private MediaPurpose purpose;
    private String objectName;             // chemin GCS
    private String contentType;
    private long sizeBytes;
    private Integer width;
    private Integer height;
    private boolean confirmed;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
}
```

### REST endpoints `/api/media`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/media/upload-url` | authenticated | Demande URL signée upload |
| `POST` | `/api/media/{mediaId}/confirm` | owner | Confirme upload terminé |
| `GET` | `/api/media/{mediaId}/url` | reader autorisé | URL signée lecture |
| `DELETE` | `/api/media/{mediaId}` | owner ou admin | Soft delete + suppression GCS |

### Configuration `application.yml`

```yaml
storage:
  gcs:
    bucket: ${GCS_MEDIA_BUCKET:koval-media-dev}
    project-id: ${GCP_PROJECT_ID}
    signed-url-upload-ttl: PT15M
    signed-url-read-ttl: PT6H
    max-upload-size-bytes: 8388608
    allowed-content-types: image/jpeg,image/png,image/webp,image/heic
```

Authentification GCS : variable d'environnement `GOOGLE_APPLICATION_CREDENTIALS` (Workload Identity en prod GKE/Cloud Run, JSON service account en dev).

### Photos sur le feed (enrichissement)

Les membres peuvent attacher des photos aux événements existants du feed. Le cas d'usage principal : `SESSION_COMPLETION` (un membre poste une photo de la sortie). Mais le mécanisme est générique et marche pour `COACH_ANNOUNCEMENT`, `RACE_COMPLETION`, etc.

Ajout sur `ClubFeedEvent` :

```java
private List<MediaEnrichment> photoEnrichments = new ArrayList<>();

public record MediaEnrichment(
    String id,
    String mediaId,
    String contributedByUserId,
    String contributedByDisplayName,
    String contributedByProfilePicture,
    LocalDateTime addedAt) {}
```

Endpoints supplémentaires sur `ClubFeedController` :

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/clubs/{clubId}/feed/{eventId}/photos` | active member | Body: `{ mediaIds: [...] }`. Attache une ou plusieurs photos à l'event. |
| `DELETE` | `/api/clubs/{clubId}/feed/{eventId}/photos/{enrichmentId}` | contributeur ou admin | Détache et supprime |

Le ajout déclenche un broadcast SSE (`feed_photo_added`) sur le canal du club, comme pour les commentaires.

### MCP — outils photos pour Claude

Pour permettre à Claude d'inclure les photos dans la PDF du gazette, le payload retourné par `getGazettePayload` doit contenir, pour chaque post : la liste des `mediaId` + leurs URLs signées de lecture (TTL 1h, suffisant pour un round-trip). Claude peut télécharger les images directement et les intégrer dans la PDF.

Optionnel : un outil MCP dédié `getMediaReadUrl(mediaId)` si Claude a besoin de re-générer une URL.

### Hors périmètre v1

- Format AVIF (encore plus léger que WebP — à ajouter quand le support navigateur sera total).
- Modération automatique (détection NSFW, ex. via Vision API).
- Vidéos.
- Traitement asynchrone via Pub/Sub + Cloud Run job (en v1 le pipeline tourne synchroniquement dans `confirm` ; si la latence devient un problème on bascule sur un job en arrière-plan).

## Architecture — package backend

Nouveau package : `com.koval.trainingplannerbackend.club.gazette/`

```
gazette/
├── ClubGazetteEdition.java                  (document Mongo)
├── ClubGazetteEditionRepository.java
├── ClubGazettePost.java                     (document Mongo)
├── ClubGazettePostRepository.java
├── GazetteStatus.java                       (enum)
├── GazettePostType.java                     (enum)
├── ClubGazetteService.java                  (CRUD + visibilité + lifecycle)
├── ClubGazettePublisher.java                (compile snapshot + publish, applique la curation)
├── ClubGazetteController.java               (routes membres)
├── ClubGazetteAdminController.java          (routes admin/MCP : payload, publish)
└── dto/
    ├── ClubGazetteEditionResponse.java
    ├── ClubGazetteEditionSummary.java
    ├── ClubGazettePostResponse.java
    ├── CreateGazettePostRequest.java
    ├── UpdateGazettePostRequest.java
    ├── ClubGazettePayloadResponse.java       (utilisé par MCP / Claude)
    └── PublishGazetteRequest.java            (pdfBase64 + filename)
```

Outils MCP : `backend/src/main/java/com/koval/trainingplannerbackend/mcp/McpGazetteTools.java`.

## Modèle de données

### `ClubGazetteEdition`

```java
@Document(collection = "club_gazette_editions")
@CompoundIndexes({
  @CompoundIndex(name = "club_period_idx", def = "{'clubId': 1, 'periodStart': -1}"),
  @CompoundIndex(name = "club_status_idx", def = "{'clubId': 1, 'status': 1}")
})
public class ClubGazetteEdition {
    @Id private String id;
    @Indexed private String clubId;

    private int editionNumber;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private GazetteStatus status;          // DRAFT | PUBLISHED | ARCHIVED
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private String publishedByUserId;

    // Snapshots gelés à la publication
    private WeeklyStatsSnapshot statsSnapshot;
    private List<LeaderboardSnapshot> leaderboardSnapshot = new ArrayList<>();
    private List<TopSessionSnapshot> topSessions = new ArrayList<>();
    private List<MemberHighlightSnapshot> mostActiveMembers = new ArrayList<>();
    private List<MilestoneSnapshot> milestones = new ArrayList<>();

    // Engagement post-publication
    private int viewCount;
    private Set<String> readBy = new HashSet<>();
    private List<CommentEntry> comments = new ArrayList<>();

    // PDF
    @JsonIgnore private byte[] pdfData;
    private String pdfFileName;
    private LocalDateTime pdfGeneratedAt;
    private Long pdfSizeBytes;

    // ... records embedded comme décrits dans le résumé
}
```

### `ClubGazettePost`

```java
@Document(collection = "club_gazette_posts")
@CompoundIndexes({
  @CompoundIndex(name = "edition_author_idx", def = "{'editionId': 1, 'authorId': 1}"),
  @CompoundIndex(name = "edition_created_idx", def = "{'editionId': 1, 'createdAt': 1}")
})
public class ClubGazettePost {
    @Id private String id;
    @Indexed private String editionId;
    @Indexed private String clubId;
    private String authorId;
    private String authorDisplayName;
    private String authorProfilePicture;

    private GazettePostType type;
    private String title;                    // optionnel, ≤ 100 caractères
    private String content;                  // requis, ≤ 2000 caractères

    private String linkedSessionId;
    private String linkedRaceGoalId;
    private LinkedSessionSnapshot linkedSessionSnapshot;
    private LinkedRaceGoalSnapshot linkedRaceGoalSnapshot;

    private List<String> mediaIds = new ArrayList<>();   // jusqu'à 4 photos, référence vers `media` collection

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### Enums

```java
public enum GazetteStatus { DRAFT, PUBLISHED, ARCHIVED }

public enum GazettePostType {
    SESSION_RECAP,    // requires linkedSessionId
    RACE_RESULT,      // requires linkedRaceGoalId
    PERSONAL_WIN,
    SHOUTOUT,
    REFLECTION
}
```

## Repositories

```java
public interface ClubGazetteEditionRepository extends MongoRepository<ClubGazetteEdition, String> {
    Optional<ClubGazetteEdition> findFirstByClubIdAndStatusOrderByPeriodStartDesc(String clubId, GazetteStatus status);
    List<ClubGazetteEdition> findByClubIdOrderByPeriodStartDesc(String clubId, Pageable pageable);
    Optional<ClubGazetteEdition> findByClubIdAndEditionNumber(String clubId, int editionNumber);
    int countByClubId(String clubId);
}

public interface ClubGazettePostRepository extends MongoRepository<ClubGazettePost, String> {
    List<ClubGazettePost> findByEditionIdOrderByCreatedAtAsc(String editionId);
    List<ClubGazettePost> findByEditionIdAndAuthorIdOrderByCreatedAtAsc(String editionId, String authorId);
    long countByEditionId(String editionId);
    long countByEditionIdAndAuthorId(String editionId, String authorId);
    void deleteByEditionId(String editionId);
}
```

## Services — responsabilités

### `ClubGazetteService`

- `getOrCreateCurrentDraft(userId, clubId)` : retourne le DRAFT dont la période contient `now`, le crée s'il n'existe pas.
- `getOpenDrafts(userId, clubId)` : retourne **tous** les DRAFT non publiés du club. Pas de fenêtre de grâce imposée — un DRAFT reste ouvert tant qu'il n'est pas explicitement publié ou abandonné par un admin.
- `getEdition(userId, clubId, editionId)` : autorisation + filtrage des posts selon le statut.
- `listEditions(userId, clubId, page, size)` : paginé, exclut les DRAFT pour les membres non admin.
- `markAsRead(userId, editionId)` : ajoute à `readBy`, incrémente `viewCount` (idempotent).
- `addComment(userId, editionId, content)` : commentaire sur une édition publiée uniquement.
- `discardDraft(userId, editionId)` : admin seulement, supprime un DRAFT obsolète et ses posts.

**Calcul des bornes par défaut** (helper utilitaire, appliqué à la lazy creation) :
- `defaultPeriodStart(LocalDate any)` → dimanche 23h59:59 précédant le lundi de la semaine ISO contenant `any`.
- `defaultPeriodEnd(periodStart)` → `periodStart + 7 jours`.
- Au moment du `publish`, l'admin peut écraser ces bornes via la requête (cf. `PublishGazetteRequest`).

### `ClubGazettePostService` (méthode dans `ClubGazetteService` ou classe séparée si trop long)

- `createPost(userId, editionId, request)` :
  - Vérifie que l'édition est `DRAFT` et appartient au club du user.
  - Vérifie l'appartenance active du user au club.
  - Valide le type vs liens (cf. règles).
  - Pour `SESSION_RECAP` : la session doit être dans la période **de l'édition ciblée** et le user doit être participant.
  - Pour `RACE_RESULT` : le goal doit appartenir au user, la course doit être dans la période **de l'édition ciblée**.
  - Snapshote `authorDisplayName`, `authorProfilePicture` au moment de l'écriture.
- `updatePost(userId, postId, request)` : auteur seul, DRAFT seulement.
- `deletePost(userId, postId)` : auteur OU admin/coach/owner ; DRAFT seulement.
- `getPostsForEdition(userId, editionId)` :
  - PUBLISHED → tous les posts triés par `createdAt`.
  - DRAFT + user est admin/coach/owner → tous les posts.
  - DRAFT + user membre simple → seulement ses propres posts + injecter `othersCount` dans la réponse.
- `getMyPostsInCurrentDraft(userId, clubId)` : helper pour le composeur.

### `ClubGazettePublisher`

Une seule méthode publique, prend en entrée la curation décidée par l'admin via Claude :

```java
public ClubGazetteEdition publish(
    String userId,
    String editionId,
    PublishGazetteRequest request);

public record PublishGazetteRequest(
    byte[] pdfBytes,
    String pdfFilename,
    List<String> includedPostIds,           // posts retenus par la curation (ordre = ordre de rendu)
    Set<AutoSection> includedSections,      // STATS, LEADERBOARD, TOP_SESSIONS, MILESTONES, MOST_ACTIVE_MEMBERS
    LocalDateTime periodStart,              // peut écraser le défaut
    LocalDateTime periodEnd
) {}

public enum AutoSection {
    STATS, LEADERBOARD, TOP_SESSIONS, MILESTONES, MOST_ACTIVE_MEMBERS
}
```

Étapes :
1. Vérifier autorisation (`requireOwnerOrAdminOrCoach`).
2. Vérifier édition existe et est `DRAFT`.
3. Vérifier la PDF (taille ≤ 10 Mo, magic number `%PDF-`).
4. Vérifier que tous les `includedPostIds` appartiennent à cette édition.
5. Mettre à jour `periodStart` / `periodEnd` de l'édition avec les valeurs de la requête (si fournies).
6. Calculer les snapshots **uniquement pour les `includedSections`** (les autres sections restent vides) :
   - `STATS` → `WeeklyStatsSnapshot` via `ClubStatsService` (variante prenant des bornes arbitraires).
   - `LEADERBOARD` → top membres par TSS sur la période.
   - `TOP_SESSIONS` → top 3 séances par participants.
   - `MOST_ACTIVE_MEMBERS` → top 5 par heures cumulées.
   - `MILESTONES` → dérivé des données de la période (PRs, anniversaires, courses).
7. Pour chaque `ClubGazettePost` retenu : geler `linkedSessionSnapshot` / `linkedRaceGoalSnapshot` selon le type. Stocker l'ordre via un champ `displayOrder` (= index dans `includedPostIds`).
8. Pour chaque `ClubGazettePost` non retenu : marquer `excluded=true` (toujours stocké, mais filtré à la lecture après publication). Comportement alternatif possible : suppression — à décider à l'implémentation.
9. Stocker la PDF (`pdfData`, `pdfFileName`, `pdfGeneratedAt`, `pdfSizeBytes`).
10. Passer `status = PUBLISHED`, fixer `publishedAt`, `publishedByUserId`.
11. Sauver l'édition.
12. Émettre un `ClubFeedEvent` de type `GAZETTE_PUBLISHED` (ajouter au `ClubFeedEventType`) pour que la publication apparaisse dans le feed live.
13. Notifier tous les membres actifs via `NotificationService` ("Édition #N est sortie").

Pas de scheduler associé — la publication n'a pas de cadence fixée par le serveur.

## Contrôleurs

### `ClubGazetteController` (`/api/clubs/{clubId}/gazette`)

```java
@GetMapping("/editions")
ResponseEntity<List<ClubGazetteEditionSummary>> listEditions(
    @PathVariable String clubId, @RequestParam int page, @RequestParam int size);

@GetMapping("/editions/{editionId}")
ResponseEntity<ClubGazetteEditionResponse> getEdition(
    @PathVariable String clubId, @PathVariable String editionId);

@GetMapping("/editions/current")
ResponseEntity<ClubGazetteEditionResponse> getCurrentDraft(@PathVariable String clubId);

@GetMapping("/editions/drafts")
ResponseEntity<List<ClubGazetteEditionResponse>> getOpenDrafts(@PathVariable String clubId);

@GetMapping("/editions/{editionId}/pdf")
ResponseEntity<byte[]> downloadPdf(@PathVariable String clubId, @PathVariable String editionId);

@PostMapping("/editions/{editionId}/read")
ResponseEntity<Void> markAsRead(@PathVariable String clubId, @PathVariable String editionId);

@PostMapping("/editions/{editionId}/comments")
ResponseEntity<CommentEntry> addComment(...);

// Posts
@GetMapping("/editions/{editionId}/posts")
ResponseEntity<ClubGazettePostsResponse> listPosts(...);

@PostMapping("/editions/{editionId}/posts")
ResponseEntity<ClubGazettePostResponse> createPost(...);

@PatchMapping("/editions/{editionId}/posts/{postId}")
ResponseEntity<ClubGazettePostResponse> updatePost(...);

@DeleteMapping("/editions/{editionId}/posts/{postId}")
ResponseEntity<Void> deletePost(...);

@GetMapping("/editions/current/posts/mine")
ResponseEntity<List<ClubGazettePostResponse>> myPostsInCurrentDraft(@PathVariable String clubId);
```

### `ClubGazetteAdminController` (même base path, endpoints `/editions/{id}/payload` et `/editions/{id}/publish`)

```java
@GetMapping("/editions/{editionId}/payload")
ResponseEntity<ClubGazettePayloadResponse> getPayload(...);

@PostMapping("/editions/{editionId}/publish")
ResponseEntity<ClubGazetteEditionResponse> publish(
    @PathVariable String editionId, @RequestBody PublishGazetteRequest req);
```

## Outils MCP

`McpGazetteTools.java`, calqué sur `McpClubTools.java` et `ClubToolService.java`. Le flow d'usage est : `listOpenDrafts` → `getGazettePayload` (data brute) → optionnel `previewAutoSections` (test de bornes) → générer PDF → `publishGazetteWithPdf` (curation explicite + PDF).

```java
@Tool(description = "List recent gazette editions for a club (published + drafts).")
public Object listClubGazetteEditions(String clubId, int limit, ToolContext ctx);

@Tool(description = "List all open draft gazette editions for a club.")
public Object listOpenDrafts(String clubId, ToolContext ctx);

@Tool(description = "Get the full structured payload of a gazette edition: live stats, all draft posts (with author, type, links resolved), and signed photo URLs. Use this to know what is available before curating.")
public Object getGazettePayload(String editionId, ToolContext ctx);

@Tool(description = "Recompute the auto-curated sections (stats, leaderboard, top sessions, milestones, most active members) for an arbitrary period. Useful when the admin wants to adjust the period bounds.")
public Object previewAutoSections(String clubId, LocalDateTime periodStart, LocalDateTime periodEnd, ToolContext ctx);

@Tool(description = "Publish a gazette edition with the generated PDF and the curation choices. Atomic: freezes snapshots for the chosen posts and sections only, attaches the PDF, notifies members.")
public Object publishGazetteWithPdf(
    String editionId,
    String pdfBase64,
    String filename,
    List<String> includedPostIds,
    List<String> includedSections,        // STATS, LEADERBOARD, TOP_SESSIONS, MILESTONES, MOST_ACTIVE_MEMBERS
    LocalDateTime periodStart,            // optional, overrides default
    LocalDateTime periodEnd,              // optional, overrides default
    ToolContext ctx);

@Tool(description = "Discard a draft edition (admin only). Removes the edition and all its posts. Used when an edition is no longer relevant.")
public Object discardGazetteDraft(String editionId, ToolContext ctx);
```

Chaque outil :
- Récupère `userId` via `SecurityUtils.getUserId(context)`.
- Délègue au service correspondant.
- Retourne des DTOs lean (pas l'entité Mongo brute).
- En cas d'erreur, retourne une chaîne préfixée par "Error:" (cf. convention `ClubToolService.java`).

Enregistrer le bean dans `McpServerConfig.java` (suivre le pattern existant des autres outils MCP).

## Frontend (Angular)

Nouveau dossier : `frontend/src/app/components/club-gazette/`

```
club-gazette/
├── club-gazette-list/                  (liste éditions)
├── club-gazette-reader/                (lecteur magazine)
├── club-gazette-composer/              (création de post)
├── club-gazette-my-posts/              (mes contributions courantes)
└── club-gazette.routes.ts
```

Service : `frontend/src/app/services/club-gazette.service.ts` (HTTP client + `BehaviorSubject` pour l'édition courante, conformément aux conventions).

Routes (à ajouter dans `app.routes.ts`) :
- `/clubs/:clubId/gazette` → liste
- `/clubs/:clubId/gazette/edition/:editionId` → lecteur
- `/clubs/:clubId/gazette/contribute` → composeur

Le composeur :
- Étape 1 : sélection du type (5 cartes).
- Étape 2 (conditionnelle) :
  - `SESSION_RECAP` → autocomplete sur les séances du club de la période où le user est `participantIds`.
  - `RACE_RESULT` → autocomplete sur les `RaceGoal` du user dont la course est dans la période.
  - Autres : direct étape 3.
- Étape 3 : titre + contenu + soumission.

Le lecteur affiche : bandeau → stats → leaderboard → top séances → jalons → mur des posts (groupés par type, cartes différenciées selon le lien).

Si `status === DRAFT` :
- Membre simple : bannière "Édition #N en cours, publiée manuellement par un admin" + bouton CTA "Contribuer" + compteur agrégé "X posts en cours".
- Admin/coach/owner : voit tous les posts du brouillon en pré-visualisation + bouton "Publier avec Claude" qui ouvre le chat AI pour démarrer le flow de curation et publication.

Le lien feed `GAZETTE_PUBLISHED` (à ajouter dans `ClubFeedEventType`) renvoie vers la route `/clubs/:clubId/gazette/edition/:editionId`.

## Phasage de l'implémentation

### Phase 0a — Stockage Media (GCS, base)
1. Provisionner les buckets dev/prod sur GCP, configurer le service account et les permissions.
2. Ajouter la dépendance `google-cloud-storage`.
3. Implémenter `Media`, `MediaRepository`, `MediaPurpose`.
4. Implémenter `MediaStorageService` (URL signée upload V4, URL signée lecture V4, vérification d'existence d'objet).
5. Implémenter `MediaService` + `MediaController` avec les 4 endpoints (`upload-url`, `confirm`, `url`, `delete`).
6. Implémenter `MediaCleanupScheduler` (cron quotidien : purge des Media non confirmés > 24h + leurs objets GCS).
7. Tests manuels de bout en bout : upload URL → PUT direct → confirm → URL lecture.
8. Documenter la configuration `application.yml` et les variables d'environnement.

### Phase 0b — Pipeline d'optimisation et CDN
1. Ajouter dépendances : `thumbnailator` + lib WebP (TwelveMonkeys ou `webp-imageio`) + `blurhash-java`.
2. Étendre `Media` avec `blurHash`, `variants`, `processingStatus`, `processedAt`.
3. Implémenter le pipeline dans `MediaStorageService.processConfirmedUpload` :
   - Lire l'original depuis GCS.
   - Générer 4 variantes WebP (thumb/small/medium/large) avec strip EXIF + qualité 80.
   - Calculer le BlurHash.
   - Uploader les variantes sur GCS avec `Cache-Control: public, max-age=31536000, immutable`.
   - Mettre à jour le document `Media`.
4. Étendre la réponse `MediaResponse` pour inclure `blurHash`, `width`, `height`, `variants[]`.
5. Provisionner Cloud CDN devant le bucket (Backend Bucket + Load Balancer HTTPS) ; pointer `cdn.koval.app` dessus.
6. Côté frontend : créer `<koval-image>` réutilisable (BlurHash + `<picture>` + `srcset` + lazy + aspect-ratio).
7. Ajouter `<link rel="preconnect" href="https://cdn.koval.app">` dans `index.html`.
8. Tests : mesurer LCP avant/après sur la page lecteur du gazette.

### Phase 1 — Squelette backend (sans logique métier)
1. Créer le package `gazette/` avec les entités, enums, repositories, controllers et DTOs vides.
2. Câbler les routes (controllers retournent 501 ou stubs).
3. Vérifier que le module compile et que les routes sont visibles dans Swagger / Actuator.

### Phase 2 — Lifecycle et CRUD éditions (sans posts)
1. Implémenter `ClubGazetteService.getOrCreateCurrentDraft`, `getOpenDrafts`, `getEdition`, `listEditions`, `discardDraft`.
2. Calculer les bornes par défaut (dimanche 23h59 ↔ dimanche 23h59, fuseau serveur). Bornes modifiables au moment du `publish`.
3. Tests : créer plusieurs DRAFT consécutifs, lister, lire, abandonner.
4. Auto-numérotation `editionNumber` (atomique : utiliser un `findAndModify` sur un compteur ou `count + 1` accepté en v1).

### Phase 3 — Posts membres
1. CRUD complet sur `ClubGazettePost`.
2. Validations (type vs lien, période, ownership).
3. Validation des `mediaIds` : doivent appartenir à l'auteur, être confirmés, purpose = `GAZETTE_POST`, max 4.
4. Filtrage de visibilité dans `getPostsForEdition`.
5. Résolution des photos en URLs signées dans la réponse DTO.
6. Endpoint `mine`.

### Phase 3b — Enrichissement photos du feed existant
1. Ajouter `photoEnrichments: List<MediaEnrichment>` sur `ClubFeedEvent`.
2. Endpoints `POST /api/clubs/{clubId}/feed/{eventId}/photos` et `DELETE .../photos/{enrichmentId}`.
3. Broadcast SSE `feed_photo_added` / `feed_photo_removed`.
4. Mise à jour de `ClubFeedEventResponse` pour inclure les photos résolues (URLs signées).

### Phase 4 — Publication avec curation (sans Claude pour l'instant)
1. `ClubGazettePublisher.publish` complet : applique la curation (`includedPostIds`, `includedSections`, bornes) + freeze posts retenus + status + notifications.
2. Ajouter `GAZETTE_PUBLISHED` à `ClubFeedEventType` et `ClubFeedEvent`.
3. Endpoint `POST /editions/{id}/publish` opérationnel (PDF base64 + curation acceptés ; PDF non générée côté serveur).
4. Endpoint `GET /editions/{id}/pdf`.
5. Notifications de publication aux membres actifs.
6. Tests d'intégration manuels avec un PDF placeholder et une liste de posts curés.

### Phase 5 — Outils MCP
1. Créer `McpGazetteTools.java` avec les outils (`listClubGazetteEditions`, `listOpenDrafts`, `getGazettePayload`, `previewAutoSections`, `publishGazetteWithPdf`, `discardGazetteDraft`).
2. Enregistrer dans `McpServerConfig`.
3. Tester depuis Claude Desktop ou Claude.ai connecté au MCP : récupérer un payload, choisir des posts, générer une PDF mocké, publier.

### Phase 6 — Frontend
1. Service `MediaService` côté Angular : `requestUploadUrl`, `uploadToSignedUrl` (PUT direct), `confirm`. Wrapper utilisable partout dans l'app.
2. Composant réutilisable `<photo-uploader>` : drag & drop / click, prévisualisation, gestion d'erreurs, callback avec les `mediaId`.
3. Service `ClubGazetteService` avec tous les endpoints.
4. Liste des éditions.
5. Lecteur d'édition (mode publié uniquement d'abord) avec galerie photos par post.
6. Composeur de posts intégrant `<photo-uploader>` (max 4 photos).
7. Mode brouillon dans le lecteur (bannière "publication manuelle par un admin" + actions admin pour ouvrir Claude).
8. Enrichissement photos du feed (bouton "Ajouter une photo" sur les cartes existantes).
9. Carte feed `GAZETTE_PUBLISHED`.

### Phase 7 — Polish et e2e
1. Tests Playwright (s'inspirer de `frontend/e2e/club/club-feed.spec.ts`).
2. Documentation skills/ MCP côté utilisateur : fichier `skills/koval-publish-club-gazette.md` qui décrit à Claude le flow de curation et publication (lister les drafts, choisir les posts, générer la PDF, appeler `publishGazetteWithPdf`).

## Points d'attention

- **Réutiliser `ClubStatsService`** pour les stats hebdo plutôt que de dupliquer la logique. Ajouter une variante prenant des bornes `LocalDateTime` arbitraires (la version actuelle suppose lundi minuit).
- **Atomicité de la publication** : utiliser une transaction Mongo si possible (replicat set requis), sinon ordre d'écriture pdfData → snapshots → status, et accepter le risque de stale state en cas de crash.
- **Suppression d'un post déjà snapshoté** : impossible une fois l'édition publiée.
- **Cas où aucun membre ne contribue** : l'édition se publie avec stats + leaderboard + top séances + jalons uniquement. Le mur des posts affiche un message d'encouragement pour la prochaine édition.
- **Numérotation des éditions** : `count + 1` est sujet à race condition si deux DRAFT sont créés en parallèle. Acceptable en v1 (très peu probable). Si problème, passer par un compteur dédié type `club_counters` avec `findAndModify`.
- **Coexistence de plusieurs brouillons** : si une publication tarde à être déclenchée, plusieurs DRAFT peuvent rester ouverts simultanément. Le composeur frontend doit afficher un sélecteur explicite (par défaut, l'édition correspondant à la date de l'activité liée).
- **Posts dans des brouillons "anciens"** : aucun rejet basé sur le temps. Tant qu'un DRAFT n'est pas publié ou abandonné, un membre peut y ajouter des posts. Si un admin veut clore un DRAFT sans publier, il appelle `discardGazetteDraft`.
- **Migration de données** : aucune. Pas de données existantes à migrer.

## Définition de "fait" v1

Un club peut, sur une semaine complète :
1. Voir le DRAFT auto-créé apparaître dans l'app à la première contribution.
2. Plusieurs membres ajoutent des posts de différents types.
3. **Quand il le souhaite**, un owner/admin/coach ouvre Claude et lance le flow de publication :
   - Claude liste les posts du brouillon via `getGazettePayload`.
   - L'admin choisit avec Claude quels posts inclure et quelles sections auto-curées activer.
   - Claude génère la PDF.
   - Claude appelle `publishGazetteWithPdf` avec la PDF + la curation.
4. Tous les membres reçoivent une notification, voient l'édition publiée avec uniquement les posts inclus dans la curation, et peuvent télécharger la PDF.
5. Un nouveau DRAFT peut être créé à tout moment (lazy à la première contribution sur la prochaine période).
