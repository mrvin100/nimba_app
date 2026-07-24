# Nimba — Modèle de domaine

Document canonique du modèle métier de Nimba (plateforme interne de gestion du cycle de
vie des dossiers de crédit d'une banque). Il décrit **l'état actuel** du code, le
**vocabulaire fixé**, et la **direction cible** de la refonte (Client unique + catalogue
produit). Il est la référence pour l'ajout de tout nouveau produit de crédit.

> Convention : les sections marquées **[ACTUEL]** décrivent le code tel qu'il est ;
> les sections **[CIBLE]** décrivent l'architecture visée par la refonte en cours
> (voir la section « Refonte » en fin de document).

---

## 1. Objectif fonctionnel

Nimba couvre, pour chaque **produit de crédit** de la banque, quatre temps :

1. **Constituer** le dossier de crédit d'un client (pièces, TA, FA, garanties…) ;
2. **Traiter** le dossier selon son workflow d'approbation (revues, comité) ;
3. **Suivre** le crédit une fois approuvé (mise en place, remboursement) ;
4. **Archiver** une fois le crédit terminé.

La banque dispose de **plusieurs produits de crédit** (Leasing, MC2/MUFFA, Caution, et à
venir Crédit particulier…). **Chaque produit a son propre process d'approbation.** L'enjeu
d'architecture est de numériser ces workflows par produit et d'intégrer chaque nouveau
produit **de façon progressive, simple et robuste**, sans réécrire le socle.

---

## 2. Vocabulaire fixé

| Terme | Définition | Réalité technique |
|---|---|---|
| **Client** | Un client de la banque (entreprise aujourd'hui ; particulier / autre à venir). Un même client peut souscrire à **plusieurs produits**. | Module `client` — **[CIBLE]** source unique d'identité. |
| **Produit** (famille) | Un produit de crédit : Leasing, MC2/MUFFA, Caution, Crédit particulier… | `ProductFamily` — **[CIBLE]** catalogue produit. |
| **Variante** | Une déclinaison d'un produit au **même workflow** : Leasing {avec / sans contrat}, Caution {SMS / ACF / AFC / PRO / AVD…}. | `ContractType` (leasing), `CautionDocumentType` (caution). |
| **Registre** | La **liste** des dossiers d'un produit + son périmètre RBAC (qui voit / agit). **Ce n'est pas une table**, c'est une vue filtrée décrite par le catalogue. | Routes frontend par workspace (ex. registre Leasing = DRI, registre Cautions = DCM). |
| **Dossier** | L'agrégat qui regroupe les éléments d'une demande d'un client pour une variante donnée, et qui suit un **workflow** d'approbation. | `CreditCase` (financement) **ou** `CautionDossier` (engagement par signature). |

### Invariant produit / variante (verrouillé)

> **Le workflow est défini au niveau du Produit. Une variante ne diffère que par les
> documents requis, les champs saisis et les layouts générés — jamais par le workflow.**

C'est déjà le cas du Leasing (avec / sans contrat = même workflow, seule la variante FA
« Pilier 2 » change). Toute intégration future doit respecter cet invariant : il rend
l'ajout d'une variante trivial et l'ajout d'un produit cadré.

---

## 3. Les deux archétypes de dossier (distincts par conception)

Nimba modélise **deux formes de dossier fondamentalement différentes**. On partage le
transverse entre elles, **jamais leur forme** — les forcer dans un unique dossier
polymorphe produirait un moteur de configuration ingérable.

### 3.1 Dossier de FINANCEMENT — module `creditcase`

Produits : **Leasing** (avec / sans contrat), **MC2/MUFFA**.
Un crédit amorti : il porte un **échéancier** (TA), une **fiche d'analyse** (FA), passe
par un **comité**, et donne lieu à un **remboursement** suivi jusqu'à clôture.

- Racine : `CreditCase` (`creditcase/internal/CreditCase.kt`).
- Satellites (rattachés par `creditCaseId`) :
  - `amortizationschedule` — TA (échéancier) + traités (lettres de change).
  - `analysissheet` — FA (fiche d'analyse, sections typées par pilier).
  - `guarantee` — garanties + pièces jointes.
  - `pv` — procès-verbal du comité (snapshot figé à la finalisation).
  - `fmp` — fiche de mise en place (extraite du PV figé).
  - `workflow` — machine à états inter-directions + notifications.
- Politique par variante : `CaseTypePolicy` / `CaseTypePolicies`
  (`creditcase/CaseTypePolicy.kt`) — documents requis, format TA (`ScheduleFormat`),
  variante FA (`FaVariant`), génération des traités, **par couple (produit, variante)**.

### 3.2 Dossier d'ENGAGEMENT PAR SIGNATURE — module `caution`

Produit : **Caution** (SMS, ACF, AFC, PRO, et AVD à venir).
**Ce n'est pas un crédit amorti** : pas d'échéancier, pas de traités, pas de
remboursement. C'est un engagement par signature ; son « dossier » est un **bundle
d'attestations générées** pour une demande client (un appel d'offres, éventuellement
plusieurs lots). Sa divergence de workflow est donc **légitime**, pas un défaut.

- Racine : `CautionDossier` (`caution/internal/CautionDossier.kt`) — porte le contexte
  marché partagé (`contentJson`).
- Documents membres : `CautionDocument` (`caution/internal/CautionDocument.kt`), typés par
  `CautionDocumentType` (SMS/ACF/AFC/PRO), avec historique `CautionDocumentVersion` et
  journal `CautionDossierEvent`.
- Moteur de champs : `CautionFieldRegistry` (`caution/CautionFieldType.kt`) — métadonnées
  de formulaire par type, avec **scope `COMMON` / `SPECIFIC`** : les champs COMMON sont
  saisis **une seule fois** sur le dossier et hérités par chaque document (règle « ne
  jamais demander deux fois la même information », `effectiveContent`).
- Référence : `Client` par `clientId` (déjà la source unique côté caution).

---

## 4. Cycles de vie (workflows)

### 4.1 Financement — `WorkflowStatus` (`workflow/WorkflowStatus.kt`)

Machine à états inter-directions (DRI → DCM → DRC → COMITÉ), 14 états déclarés :

```
BROUILLON → EN_REVUE_DCM → EN_REVUE_DRC → CORRECTIONS_DRI / A_COMPLETER
          → EN_VERIFICATION_DCM → PRET_POUR_COMITE → APPROUVE
          → (PV → FMP → EN_SIGNATURE → SIGNE → EN_COURS → CLOTURE)
rejet : … → EN_ARCHIVAGE → REJETE
```

- Deux approbations comité **distinctes** requises (`comiteApprovalsRequired`).
- Tout retour vers la DRI invalide les approbations.
- Chaque transition journalisée (`WorkflowEvent`) + notification (`notification`).
- Le workflow est initialisé/purgé **par événements** (`CreditCaseCreated` /
  `CreditCaseDeleted`, consommés par `CreditCaseLifecycleListener`) — `creditcase`
  ignore l'existence du module `workflow` (pas de cycle).

### 4.2 Engagement par signature — `DossierStatus` (`caution/DossierStatus.kt`)

```
BROUILLON ──finalize──► FINALISE ──proroge (Manager)──► EN_PROROGATION
    ▲                        ▲                                │
    └──── (édition libre)    └────────── refinalize ──────────┘
```

Beaucoup plus léger, **sans comité**. À la finalisation, chaque document fige un snapshot
de l'identité client (`CautionClientSnapshot`).

---

## 5. Modèle transverse

- **Identité (RBAC)** : `identity` — `User` avec `status`, `platformAdmin`, et un ensemble
  de `Membership(Department, DepartmentRole)`. Directions : `DRI`, `DCM`, `DRC`, `COMITE`.
  Autorités `ROLE_{DEPT}_{ROLE}` + `ROLE_ADMIN`, hiérarchie `MANAGER > MEMBER`.
- **Audit** : `audit` — trace toute requête mutante.
- **Numérotation** : `CreditCaseNumberGenerator` (`DOS-{année}-{NNNN}`) et
  `CautionNumberGenerator` (`{seq}-{matricule}-{code}-{date}`) — même mécanique d'upsert
  atomique sur un compteur ; **candidat noyau** (formats propres à chaque produit).

---

## 6. Comment ajouter un produit de crédit (guide d'extension) **[ACTUEL]**

Checklist concrète, avec les points d'extension réels. Chemins backend relatifs à
`app/src/main/kotlin/com/nimba/`, frontend à `web/components/`.

### 6.0 Étape commune à tout produit — le catalogue
Déclarer la famille dans **`catalog/ProductFamily.kt`** : une valeur d'enum avec
`label`, `department` (code direction pilote), `lifecycle` (`FINANCEMENT` | `ENGAGEMENT`).
Rien d'autre à faire pour la navigation : `GET /catalog/products` l'expose, et le
frontend (registres + fil d'Ariane) la reprend automatiquement via le module
`components/modules/catalog`. **C'est le seul point d'entrée obligatoire.**

Puis choisir l'archétype (§3) : **financement amorti** → §6.A (réutilise `creditcase`) ;
**engagement / document généré** → §6.B (module frère). Ne jamais forcer un produit dans
le mauvais archétype (voir §3).

### 6.A Produit de FINANCEMENT (hébergé par `creditcase`)
1. **`creditcase/ProductType.kt`** : ajouter la valeur d'enum + la mapper vers la
   `ProductFamily` de §6.0 dans `ProductType.family()`.
2. **`creditcase/CaseTypePolicy.kt`** : ajouter une entrée dans `CaseTypePolicies.ALL`
   **par variante** — `requiredDocuments` (`DocumentKind`), `scheduleFormat`
   (`ScheduleFormat`), `faVariant` (`FaVariant`), `generatesTraites`. Cela pilote le
   sélecteur de type à la création (`GET /credit-cases/types` via `CaseTypeController`)
   et la validation create/update — **aucun consommateur à toucher**.
3. **Variantes** : si le produit en a, réutiliser/étendre `creditcase/ContractType.kt`.
   Le **workflow reste inchangé** (invariant §2) — une variante ne diffère que par
   documents / champs / layouts.
4. **FA** : si la fiche d'analyse diffère, étendre `analysissheet/FaSectionRegistry.kt`
   (+ `FaVariant`, `FaSectionKey.kt`). Les sections COMPUTED/BOUND se dérivent seules.
5. **Client** : rien à faire — un dossier référence déjà un `Client` par `clientId` et le
   formulaire de création sélectionne le client (`components/modules/credit-case`).
6. **Frontend** : rien de spécifique — le produit apparaît dans le registre DRI
   (`credit-case/registre-view.tsx`, piloté par le catalogue) dès §6.0.

### 6.B Nouvel ARCHÉTYPE (module frère, façon `caution`)
À réserver à un produit qui n'est **pas** un crédit amorti (pas d'échéancier/TA/comité).
Créer un module `com.nimba.<produit>` en miroir de `com.nimba.caution` :
1. Agrégat racine + documents/enfants typés, avec **son propre cycle de vie** (cf.
   `caution/DossierStatus.kt`), **jamais** le workflow comité du financement.
2. Registry de champs par type (cf. `caution/CautionFieldType.kt` : scopes
   `COMMON`/`SPECIFIC`, héritage `effectiveContent`).
3. Numérotation propre (cf. `caution/internal/CautionNumberGenerator.kt` — même mécanique
   d'upsert atomique ; **candidat noyau partagé**, ne pas copier-coller si extrait).
4. **Référencer `Client` par `clientId`** (jamais de FK JPA cross-module) ; si le produit
   exige le matricule (comme la caution), le vérifier à la création (400 sinon).
5. Journaliser le cycle de vie (cf. `caution/internal/CautionDossierEvent`) ; figer un
   snapshot client à la finalisation si le document devient un acte officiel.
6. **Frontend** : nouveau module `components/modules/<produit>` (schema/service/hook/vues)
   + routes + entrée de nav dans `components/shared/workspace-registry.ts`, en miroir de
   `caution` / `client-file`.

### 6.C Transverse (les deux archétypes)
- **RBAC** : si une nouvelle direction pilote le produit, ajouter le matcher dans
  `shared/security/SecurityConfig.kt` (ordre : spécifique avant le catch-all). `/clients`
  est déjà partagé DRI+DCM.
- **Vue client 360** : un nouveau produit apparaît dans la fiche client dès qu'on ajoute
  sa liste `by-client` dans `components/modules/client-file/client-file-view.tsx` (le
  backend expose déjà `?clientId=` côté financement ; côté caution `listDossiers(clientId)`).
- **Tests** : refléter les tests existants du module modèle. **Toute migration qui touche
  des lignes existantes** doit avoir un test Flyway à deux phases (cf.
  `app/src/test/.../creditcase/ClientUnificationMigrationTest.kt` — migrer jusqu'à N-1,
  insérer des lignes legacy, migrer N, asserter).

### 6.D Invariant à ne jamais violer
Le **workflow se définit au niveau du Produit** ; une **variante** ne diffère que par les
documents requis, les champs et les layouts générés — **jamais** par le workflow (§2).

---

## 7. Refonte réalisée (Client unique + catalogue produit) — NIMBA-83

Motivation : le code avait divergé — l'identité client était **dupliquée** entre `Client`
(module client, caution) et `CreditCase.clientIdentity` (embeddable, financement), rendant
impossible la vue « un client, plusieurs produits » ; granularité produit incohérente
(MC2/MUFFA = valeur d'enum ; Caution = module). Réalisé :

- **Phase 1 — FAIT** : module `catalog` (`ProductFamily` / `LifecycleKind` / `ProductCatalog`,
  `GET /catalog/products`). `ProductType.family()` relie le financement au catalogue.
- **Phase 2 — FAIT** : **unification Client** — `ClientType` (particulier-ready), **matricule
  optionnel / unique si présent** ; `CreditCase` référence `clientId`, l'embeddable
  `ClientIdentity` supprimé. `CreditCaseInfo.clientName`/`clientIdentity` **résolus depuis le
  Client** → traités, FA/PV/FMP, workflow inchangés. Création de dossier = choix d'un Client
  (comme la caution). Migrations V35/V36 (dédup par `code_nif`, test à deux phases). `/clients`
  ouvert à la DRI.
- **Phase 3 — partiel** : numérotation/journal restent des **candidats noyau** (pas encore
  extraits ; duplication tolérée tant qu'un seul autre module la porte).
- **Phase 4 — FAIT** : sélecteur de client à la création ; édition d'identité repointée sur le
  Client ; **registres pilotés par le catalogue** (`registre-view.tsx`) ; **vue client 360**
  (`components/modules/client-file` : dossiers financement + cautions) ; écran d'édition
  complète de la fiche client.

**Reste ouvert** : produits *particulier* (readiness posée, non construits) ; extraction du
noyau transverse (Phase 3) ; intégration de nouveaux produits via le guide §6.
