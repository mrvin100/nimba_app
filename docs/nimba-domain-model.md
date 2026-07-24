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

## 6. Comment ajouter un produit de crédit (guide d'extension) **[CIBLE]**

1. **Choisir l'archétype** : le produit est-il un **financement amorti** (comme le
   leasing → réutilise la colonne vertébrale `creditcase` : TA/FA/PV/FMP/workflow comité)
   ou un **engagement / document généré** (comme la caution → module dédié léger) ?
   Un archétype vraiment nouveau justifie un module frère ; sinon, réutiliser l'existant.
2. **Déclarer le produit et ses variantes** dans le `ProductCatalog` (famille, variantes,
   registre, direction pilote, `LifecycleKind`).
3. **Définir la politique par variante** (documents requis, champs, layouts) dans la
   politique du module d'accueil (`CaseTypePolicy` côté financement, `CautionFieldRegistry`
   côté caution) — **sans toucher aux autres produits** (invariant §2).
4. **Rattacher au Client unique** par `clientId` (jamais de FK JPA cross-module).
5. **Ne pas dupliquer le transverse** : réutiliser numérotation, journal, snapshot.

---

## 7. Refonte en cours (Client unique + catalogue produit)

Motivation : le code a divergé — l'identité client est aujourd'hui **dupliquée** entre
`Client` (module client, utilisé par la caution) et `CreditCase.clientIdentity`
(embeddable, utilisé par le financement), ce qui rend impossible la vue « un client,
plusieurs produits ». La granularité produit est incohérente (MC2/MUFFA = valeur d'enum ;
Caution = module). La refonte :

- **Phase 1** — `ProductCatalog` dans `shared` (famille → variante, registre, direction,
  `LifecycleKind`) ; point d'extension unique, additif.
- **Phase 2** — **Unification Client** : `Client` devient la source unique (ajout d'un
  discriminant `ClientType`, **matricule optionnel / unique si présent**, *particulier-ready*) ;
  `CreditCase` référence `clientId` et **abandonne** l'embeddable `ClientIdentity` ; la
  création d'un dossier de financement **sélectionne un Client existant** (comme la caution).
  Les snapshots figés (PV/FMP, caution) sont inchangés — seul le chemin de lecture *vive* migre.
- **Phase 3** — extraction du noyau transverse (numérotation, journal) là où la
  duplication est réelle.
- **Phase 4** — frontend : sélecteur de client à la création, registres pilotés par le
  catalogue, **vue client 360** (tous les dossiers d'un client, financement + cautions).
