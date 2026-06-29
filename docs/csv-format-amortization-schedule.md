# Format CSV standard — Tableau d'amortissement (NIMBA-14)

Spécification figée du fichier CSV d'upload de l'échéancier. Le parseur (NIMBA-15)
et les analystes DRI partagent cette référence unique. Document métier complet :
`nimba-tableau-amortissement-trades-fiche-metier.md`.

## Conventions

- **Encodage :** UTF-8 (un encodage invalide est rejeté avec un message explicite).
- **Séparateur de colonnes :** point-virgule `;`. Choisi parce que les montants peuvent
  contenir une virgule décimale ou des séparateurs selon la saisie ; la virgule est donc
  exclue comme séparateur de colonnes.
- **Format de date :** `JJ/MM/AAAA`.
- **Séparateur décimal des montants :** le point `.` est la forme canonique des fichiers
  d'exemple ; le parseur accepte aussi la virgule décimale (`,`) par tolérance.
- **Première ligne :** ligne d'en-tête obligatoire, avec exactement les 12 noms de colonnes
  ci-dessous (orthographe exacte ; l'ordre des colonnes n'est pas significatif).
- **Pas de ligne TOTAL :** le fichier ne contient pas de ligne « TOTAL ». La somme des
  `loyer_ttc` (hors VR) est recalculée et affichée comme donnée de contrôle.

## Les 12 colonnes (dans l'ordre canonique)

| # | En-tête | Type | Obligatoire | Description |
|---|---|---|---|---|
| 1 | `numero_echeance` | texte | oui | Entier positif, ou la valeur littérale `VR` |
| 2 | `date_echeance` | date `JJ/MM/AAAA` | oui sauf ligne `VR` | Jour de l'échéance ; vide pour la VR |
| 3 | `interet` | montant | oui | Intérêt ; pour la VR, porte le montant de la valeur résiduelle |
| 4 | `equipement` | montant | oui | Quote-part équipement |
| 5 | `assurance` | montant | oui | Quote-part assurance |
| 6 | `tracking` | montant | oui | Quote-part tracking |
| 7 | `immatriculation` | montant | oui | Quote-part immatriculation |
| 8 | `capital` | montant | oui | = equipement + assurance + tracking + immatriculation |
| 9 | `loyer_ht` | montant | oui | = capital + interet |
| 10 | `taxes` | montant | oui | Taxes (TAF) |
| 11 | `loyer_ttc` | montant | oui | = loyer_ht + taxes — **montant du trade** |
| 12 | `capital_restant_du` | montant | non | Capital restant dû (collecté pour usage futur) |

## Règles de validation

- L'en-tête doit contenir exactement les 12 noms obligatoires (orthographe exacte). Un
  en-tête incorrect rejette le fichier.
- Une colonne obligatoire vide, ou non numérique là où un nombre est attendu, rejette la
  ligne avec un message identifiant la ligne et la colonne.
- `numero_echeance` accepte les entiers positifs et la valeur littérale `VR`.
- Un fichier sans aucune ligne de données (en-tête seul) est rejeté.
- La cohérence arithmétique (colonnes 8/9/11) est vérifiée séparément (NIMBA-16), avec une
  tolérance d'arrondi ; la ligne `VR` est exclue de ces contrôles.

## Ligne VR (valeur résiduelle)

`numero_echeance` = `VR`, `date_echeance` vide. La colonne `interet` porte le montant de la
valeur résiduelle ; `equipement`, `assurance`, `tracking`, `immatriculation`, `capital`,
`loyer_ht` valent 0 ; `loyer_ttc` = VR + `taxes`.

## Fichiers d'exemple

- `examples/exemple-echeancier-valide.csv` — cas réel ETS OC ET FRÈRES : 24 échéances + 1
  ligne VR (25 lignes de données), arithmétiquement cohérent.
- `examples/exemple-echeancier-invalide.csv` — en-tête valide mais deux anomalies servant de
  fixtures de test : une colonne obligatoire vide (`capital` de la 2ᵉ ligne) et une
  incohérence arithmétique (`loyer_ttc` de la 3ᵉ ligne).
