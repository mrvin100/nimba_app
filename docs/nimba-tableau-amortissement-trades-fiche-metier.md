# Nimba — Fiche métier : Tableau d'amortissement → Trades

**Statut : BROUILLON — en attente de validation par le Product Owner (représentant métier DRI).**
Ce document est requis par l'EPIC-04 du backlog (`nimba-mvp-backlog.md`). Il a été reconstitué
à partir du cas réel **ETS OC ET FRÈRES** (fichiers source `docs/taettraites/TA OC ET FRERES.xlsx`
et `docs/taettraites/TRAITE OC ET FRERES.docx`) et des critères d'acceptation déjà figés dans le
backlog. Il doit être relu et validé avant le démarrage de NIMBA-15 (parseur), conformément à la
Definition of Done de NIMBA-14.

---

## 1. Objet et périmètre

La fonctionnalité cœur de cette phase transforme le **tableau d'amortissement** d'un dossier de
crédit-bail (leasing) en un ensemble de **trades** (lettres de change / traites) que la banque
émet à l'ordre d'elle-même, à faire accepter par le client.

Le flux est : *upload CSV → prévisualisation (contrôles) → téléversement définitif → génération
des trades → consultation / téléchargement*. La génération ne requiert **aucune validation
hiérarchique** (voir §9).

---

## 2. Le cas réel de référence (ETS OC ET FRÈRES)

- **Tiré (client / locataire) :** ETS OC ET FRÈRES — bénéficiaire OUSMANE CAMARA
- **Tireur (banque) :** Afriland First Bank Guinée, agence Kaloum
- **Produit :** LEASING — **devise : GNF**
- **Durée :** 24 échéances mensuelles + 1 valeur résiduelle (VR)
- **Date de première échéance :** 01/05/2026 — **dernière échéance :** 01/04/2028
- **Taux d'intérêt annuel :** 14 % (mensuel 1,1667 %)
- **Périodicité :** mensuelle — **mode d'amortissement :** capitalisé
- **Valeur résiduelle :** 2 % (montant VR 46 000 000 GNF, loyer TTC VR 54 280 000 GNF)

Ce cas sert de **fixture de test de bout en bout** (NIMBA-23) : la génération doit reproduire
exactement les 25 trades du §6.

---

## 3. Structure du tableau d'amortissement

Le tableau comporte une ligne par échéance, plus une ligne **VR** (valeur résiduelle), plus une
ligne **TOTAL** (informative). Le fichier CSV standard d'upload (§5) ne contient **pas** la ligne
TOTAL — elle est recalculée et affichée comme donnée de contrôle.

### 3.1 Les 12 colonnes

| # | Colonne (en-tête CSV) | Type | Obligatoire | Description |
|---|---|---|---|---|
| 1 | `numero_echeance` | texte | oui | Entier positif, ou la valeur littérale `VR` pour la ligne de valeur résiduelle |
| 2 | `date_echeance` | date `JJ/MM/AAAA` | oui (sauf VR) | Jour calendaire de l'échéance ; vide/ignoré pour la ligne VR (sa date de trade dérive de la dernière échéance) |
| 3 | `interet` | montant | oui | Intérêt de la période ; **pour la VR, porte le montant de la valeur résiduelle** (voir §8) |
| 4 | `equipement` | montant | oui | Quote-part équipement |
| 5 | `assurance` | montant | oui | Quote-part assurance |
| 6 | `tracking` | montant | oui | Quote-part tracking |
| 7 | `immatriculation` | montant | oui | Quote-part immatriculation |
| 8 | `capital` | montant | oui | = `equipement` + `assurance` + `tracking` + `immatriculation` |
| 9 | `loyer_ht` | montant | oui | = `capital` + `interet` |
| 10 | `taxes` | montant | oui | Taxes (TAF) de la période |
| 11 | `loyer_ttc` | montant | oui | = `loyer_ht` + `taxes` — **c'est le montant du trade** |
| 12 | `capital_restant_du` | montant | non | Capital restant dû après l'échéance (collecté pour usage futur — états financiers — non utilisé pour générer les trades) |

### 3.2 Conventions de format (figées ici, cf. NIMBA-14)

- **Encodage :** UTF-8.
- **Séparateur de colonnes :** point-virgule `;` (les montants peuvent contenir une virgule
  décimale ou des séparateurs de milliers selon la saisie, donc la virgule est exclue comme
  séparateur de colonnes).
- **Format de date :** `JJ/MM/AAAA`.
- **Montants :** nombres ; stockés en numérique exact (`NUMERIC`/`BigDecimal`), jamais en
  flottant. Les décimales du tableau actuariel sont conservées ; l'arrondi à l'entier n'intervient
  que pour le **montant en lettres** du trade (§7).

---

## 4. Paramètres de décalage (ajustables par dossier)

La date d'un trade dérive de la date de la ligne source, décalée puis ramenée à un **jour fixe**
du mois. Trois paramètres, **stockés sur l'échéancier (dossier par dossier), non globaux** :

| Paramètre | Défaut | Rôle |
|---|---|---|
| Décalage des échéances ordinaires | **1 mois** | date trade = date échéance + 1 mois, jour fixe |
| Décalage de la ligne VR | **2 mois** | date trade VR = date de la **dernière** échéance + 2 mois, jour fixe |
| Jour fixe du mois | **5** | jour appliqué au mois résultant ; si le mois est plus court, ramené au dernier jour valide |

Exemples vérifiés sur le cas réel : échéance 01/05/2026 → trade **05/06/2026** ; dernière échéance
01/04/2028, VR → trade **05/06/2028**.

---

## 5. Flux fonctionnel

1. L'analyste DRI ouvre un dossier (déjà créé) et glisse-dépose le fichier CSV de l'échéancier.
2. **Prévisualisation** (`POST …/amortization-schedule/preview`, aucune écriture) : le fichier est
   parsé (§3) puis soumis aux contrôles de cohérence (§8.4). La réponse contient les lignes lues,
   la liste des erreurs (parsing + cohérence) rattachées à leur ligne, et la somme informative des
   `loyer_ttc` (hors VR).
3. Si des erreurs existent, elles sont affichées ligne par ligne ; le bouton « Téléverser » reste
   désactivé.
4. **Téléversement définitif** (`POST …/amortization-schedule`) : rejoue exactement le même
   parseur et les mêmes contrôles ; si — et seulement si — il n'y a aucune erreur, persiste un
   nouvel `AmortizationSchedule` (numéro de version = max+1 pour ce dossier) avec ses lignes, et
   conserve le fichier CSV original pour traçabilité.
5. **Génération des trades** (`POST …/amortization-schedule/trades`) : action distincte et
   volontaire. Pour chaque ligne (échéances puis VR), calcule la date de trade (§4), génère le
   montant en lettres (§7) à partir du `loyer_ttc`, et persiste un `Trade`. Opération
   transactionnelle (tout ou rien). Aucune validation hiérarchique (§9).
6. **Consultation / téléchargement** : les trades actifs sont consultables et exportables en CSV.

---

## 6. Trades attendus pour le cas réel (fixture NIMBA-23)

25 trades (24 échéances + VR). `loyer_ttc` arrondi à l'entier = montant du trade. Dates de trade
calculées avec les défauts (1 mois ; VR 2 mois ; jour 5).

| N° | date_echeance | date_trade | interet | capital | loyer_ht | taxes | loyer_ttc (= montant trade) |
|---|---|---|---|---|---|---|---|
| 1 | 01/05/2026 | 05/06/2026 | 29447980 | 504822507 | 534270486 | 5300636 | 539571123 |
| 2 | 01/06/2026 | 05/07/2026 | 23558384 | 77050298 | 100608681 | 4240509 | 104849190 |
| 3 | 01/07/2026 | 05/08/2026 | 22659464 | 77949218 | 100608681 | 4078703 | 104687385 |
| 4 | 01/08/2026 | 05/09/2026 | 21750056 | 78858625 | 100608681 | 3915010 | 104523691 |
| 5 | 01/09/2026 | 05/10/2026 | 20830039 | 79778643 | 100608681 | 3749407 | 104358088 |
| 6 | 01/10/2026 | 05/11/2026 | 19899288 | 80709393 | 100608681 | 3581872 | 104190553 |
| 7 | 01/11/2026 | 05/12/2026 | 18957678 | 81651003 | 100608681 | 3412382 | 104021063 |
| 8 | 01/12/2026 | 05/01/2027 | 18005083 | 82603598 | 100608681 | 3240915 | 103849596 |
| 9 | 01/01/2027 | 05/02/2027 | 17041375 | 83567307 | 100608681 | 3067447 | 103676129 |
| 10 | 01/02/2027 | 05/03/2027 | 16066423 | 84542259 | 100608681 | 2891956 | 103500637 |
| 11 | 01/03/2027 | 05/04/2027 | 15080096 | 85528585 | 100608681 | 2714417 | 103323099 |
| 12 | 01/04/2027 | 05/05/2027 | 14082263 | 86526418 | 100608681 | 2534807 | 103143489 |
| 13 | 01/05/2027 | 05/06/2027 | 13072788 | 87535893 | 100608681 | 2353102 | 102961783 |
| 14 | 01/06/2027 | 05/07/2027 | 12051536 | 88557145 | 100608681 | 2169276 | 102777958 |
| 15 | 01/07/2027 | 05/08/2027 | 11018369 | 89590312 | 100608681 | 1983306 | 102591988 |
| 16 | 01/08/2027 | 05/09/2027 | 9973149 | 90635532 | 100608681 | 1795167 | 102403848 |
| 17 | 01/09/2027 | 05/10/2027 | 8915734 | 91692947 | 100608681 | 1604832 | 102213513 |
| 18 | 01/10/2027 | 05/11/2027 | 7845983 | 92762698 | 100608681 | 1412277 | 102020958 |
| 19 | 01/11/2027 | 05/12/2027 | 6763752 | 93844930 | 100608681 | 1217475 | 101826157 |
| 20 | 01/12/2027 | 05/01/2028 | 5668894 | 94939787 | 100608681 | 1020401 | 101629082 |
| 21 | 01/01/2028 | 05/02/2028 | 4561263 | 96047418 | 100608681 | 821027 | 101429709 |
| 22 | 01/02/2028 | 05/03/2028 | 3440710 | 97167971 | 100608681 | 619328 | 101228009 |
| 23 | 01/03/2028 | 05/04/2028 | 2307084 | 98301597 | 100608681 | 415275 | 101023956 |
| 24 | 01/04/2028 | 05/05/2028 | 1160232 | 99448449 | 100608681 | 208842 | 100817523 |
| VR | (rattachée à la 24e) | 05/06/2028 | 46000000 | 0 | 0 | 8280000 | 54280000 |
| **TOTAL** (hors trade) | | | 370157622 | 2524112534 | 2848270156 | 66628372 | **2960898528** |

> La ligne TOTAL est informative (somme de contrôle affichée en prévisualisation) ; elle n'est ni
> dans le CSV standard ni génératrice d'un trade.

---

## 7. Règle de génération d'un trade

Pour chaque ligne de l'échéancier (les 24 échéances puis la VR) :

1. **Montant en chiffres** = `loyer_ttc` de la ligne (numérique exact).
2. **Date d'échéance du trade** = date calculée selon §4.
3. **Montant en lettres** = écriture française du `loyer_ttc` **arrondi à l'entier**, suivie du nom
   de la devise. **Règle de capitalisation : chaque mot commence par une majuscule.**
   - Exemples réels vérifiés (au caractère près) :
     - `539571123` → **« Cinq Cent Trente-Neuf Millions Cinq Cent Soixante-Onze Mille Cent Vingt-Trois Francs Guinéens »**
     - `54280000` → **« Cinquante-Quatre Millions Deux Cent Quatre-Vingt Mille Francs Guinéens »**
   - Règles d'accord françaises : `quatre-vingt`/`cent` prennent un `s` au pluriel sauf suivis d'un
     autre nombre ; `mille` invariable ; `million`/`milliard` s'accordent. Tiret pour les nombres
     composés.

**Cardinalité :** nombre de trades = nombre de lignes (N échéances + 1 si VR présente). Cas réel :
24 + 1 = **25 trades**.

### 7.1 Structure d'une lettre de change (référence, hors périmètre PDF)

Le document `TRAITE OC ET FRERES.docx` montre la forme cible d'une traite : tireur (banque), date
de paiement (date du trade), ordre (la banque), montant en chiffres et en lettres, tiré (le
client), domiciliation, n° de compte, devise. Cette phase **n'émet pas** les PDF individuels ; elle
expose les données (consultation + export CSV). La génération PDF est une phase ultérieure.

---

## 8. Cas particuliers

### 8.1 Ligne VR (valeur résiduelle)
- `numero_echeance` = `VR`.
- La colonne `interet` porte le **montant de la valeur résiduelle** (46 000 000 dans le cas réel),
  pas un intérêt.
- `equipement`, `assurance`, `tracking`, `immatriculation`, `capital`, `loyer_ht` valent 0.
- `loyer_ttc` = VR + `taxes` (54 280 000 = 46 000 000 + 8 280 000).
- La VR est **exclue** des contrôles de cohérence §8.4 (sa structure est particulière).
- Sa date de trade dérive de la **dernière échéance** + décalage VR (défaut 2 mois), jour fixe.

### 8.2 Mois plus court que le jour fixe
Si le jour fixe (défaut 5) dépasse le nombre de jours du mois cible, ramener au dernier jour valide
du mois (jamais déborder sur le mois suivant).

### 8.3 Re-téléversement et régénération
Le re-téléversement suit le même flux simple. Une nouvelle génération produit un nouvel ensemble de
trades rattaché à la **dernière** version de l'échéancier ; les trades de la version précédente sont
conservés (traçabilité) mais marqués **non actifs/supersédés**. Aucune confirmation bloquante,
aucun avertissement, aucune validation supplémentaire. La consultation « courante » ne renvoie que
les trades de la génération active.

### 8.4 Contrôles de cohérence arithmétique
Pour chaque ligne **ordinaire** (hors VR), avec une **tolérance d'arrondi explicite** (ex. 1 unité
de devise) :
- `capital` = `equipement` + `assurance` + `tracking` + `immatriculation`
- `loyer_ht` = `capital` + `interet`
- `loyer_ttc` = `loyer_ht` + `taxes`

Toute violation au-delà de la tolérance est signalée avec le numéro de ligne, le champ en cause, la
valeur trouvée et la valeur attendue. La **somme des `loyer_ttc` hors VR** est calculée et renvoyée
comme donnée informative, même sans erreur.

---

## 9. Pas de validation hiérarchique

La génération des trades est une production documentaire déclenchée par l'analyste DRI seul. Aucune
contre-analyse DCM, aucun comité, aucun circuit d'approbation n'intervient dans cette phase (ces
modules appartiennent à la vision Prodigy complète, hors périmètre ici). Un seul rôle existe :
`DRI_ANALYST`.

---

## 10. Traçabilité

- Le fichier CSV original (octets bruts + nom) est conservé par version d'échéancier.
- Chaque trade référence sa ligne source et la version d'échéancier qui l'a produit.
- L'historique (quelle version a produit quels trades, et quand) reste interrogeable même sans
  écran dédié dans cette phase.

---

## 11. Points tranchés (anciennes questions résiduelles)

- **Séparateur CSV** : point-virgule `;` (figé, NIMBA-14).
- **Format de date** : `JJ/MM/AAAA` (figé).
- **Comportement au re-téléversement** : remplacement simple, sans avertissement (§8.3).
- **Capitalisation du montant en lettres** : chaque mot en majuscule initiale (§7).

---

## 12. Hors périmètre de cette phase

Fiche d'analyse, contre-analyse DCM, risques, comité, archivage ; états financiers dérivés du même
tableau (le `capital_restant_du` est néanmoins collecté en prévision) ; autres produits que le
leasing ; rôles multiples ; **génération des PDF individuels des lettres de change** ; cycle de vie
du trade au-delà de « Généré ».
