package com.etwow.ui

/**
 * Journal des modifications, affiché au clic sur la version.
 * Toute entrée doit correspondre à une version livrée (voir AGENTS.md).
 */
data class ChangelogEntry(
    val version: String,
    val date: String,
    val items: List<String>,
)

val CHANGELOG = listOf(
    ChangelogEntry(
        version = "0.17",
        date = "26/11/2026 23:42",
        items = listOf(
            "Icône de l'application : une trottinette à la place de l'éclair (le logo du widget suit).",
            "Widget 1×1 plus petit : carré de 56 dp centré dans la cellule, au lieu de remplir la cellule.",
        ),
    ),
    ChangelogEntry(
        version = "0.16",
        date = "24/11/2026 18:48",
        items = listOf(
            "Connexion en arrière-plan : la recherche reprend dès que le Bluetooth du téléphone est rallumé.",
            "Si le Bluetooth n'est pas encore prêt au démarrage du téléphone, la recherche réessaie au lieu d'abandonner.",
        ),
    ),
    ChangelogEntry(
        version = "0.15",
        date = "23/11/2026 00:07",
        items = listOf(
            "Barre de défilement : elle part du haut de la zone et suit vraiment le texte (elle flottait au milieu).",
            "Toute la colonne de la barre est saisissable, pas seulement le pouce.",
        ),
    ),
    ChangelogEntry(
        version = "0.14",
        date = "22/11/2026 23:55",
        items = listOf(
            "La barre de défilement du journal et des trames peut être glissée pour défiler.",
        ),
    ),
    ChangelogEntry(
        version = "0.13",
        date = "22/11/2026 23:43",
        items = listOf(
            "Indicateur de position vertical dans la fenêtre du journal et dans la liste des trames.",
        ),
    ),
    ChangelogEntry(
        version = "0.12",
        date = "22/11/2026 23:36",
        items = listOf(
            "Tableau de bord : les trois valeurs et les trois libellés sont réellement en colonnes.",
        ),
    ),
    ChangelogEntry(
        version = "0.11",
        date = "22/11/2026 23:26",
        items = listOf(
            "Tableau de bord : valeurs et libellés alignés en deux colonnes.",
            "Le mode de conduite n'apparaît que si la trottinette le transmet.",
            "Zero start : précision « Démarrer au pied pour que la trottinette accélère ».",
        ),
    ),
    ChangelogEntry(
        version = "0.10",
        date = "22/11/2026 23:15",
        items = listOf(
            "Corrige le plantage quand le mode de conduite n'est pas encore connu (index -1 dans la liste des modes).",
        ),
    ),
    ChangelogEntry(
        version = "0.9",
        date = "22/11/2026 23:10",
        items = listOf(
            "Nouvelles commandes : klaxon, zero start, remise à zéro du trip, mode de conduite sur les modèles récents.",
            "Simulateur « nouveau protocole » dans les réglages, pour tester sans matériel.",
            "Widget : le point remplace la coche ; hors ligne, un appui reconnecte puis passe à 25 km/h.",
            "Tableau de bord : libellés sur la même ligne que les valeurs.",
        ),
    ),
    ChangelogEntry(
        version = "0.8",
        date = "22/11/2026 22:53",
        items = listOf(
            "Prise en charge du nouveau protocole (appareils BR2262e-s / TK / ET-WOW) : trames de 20 octets, XOR.",
            "Décodage des opcodes 0B (vitesse, batterie, verrou, trip) et 0C (odomètre, phare, mode).",
            "Commandes correspondantes : verrou, phare, limite, avec bascule automatique au format détecté.",
        ),
    ),
    ChangelogEntry(
        version = "0.7",
        date = "22/11/2026 22:43",
        items = listOf(
            "Trip calculé comme l'application officielle : cumul des écarts d'odomètre, conservé entre les sessions.",
            "Opcodes vérifiés dans l'APK officielle E-TWOW 4.4 (verrou, phare, limite, zero start, unité, reset, klaxon).",
        ),
    ),
    ChangelogEntry(
        version = "0.6",
        date = "22/11/2026 19:35",
        items = listOf(
            "Décodage calé sur le parseur de l'application officielle : valeur 16 bits petit-boutiste, vitesse /10, distance /10.",
            "L'odomètre correspond enfin à l'afficheur (931,9 km sur la machine de test) ; le trip n'est pas transmis par ce modèle.",
            "Statut : phare 0x10, verrou 0x20, zero start 0x80, cran de limite sur les 3 bits de poids faible.",
        ),
    ),
    ChangelogEntry(
        version = "0.5",
        date = "22/11/2026 19:12",
        items = listOf(
            "Notifications décodées d'après les trames réelles d'une GTS : la somme de contrôle finale n'est plus prise pour une donnée.",
            "Batterie, trip (en mètres) et odomètre (en chiffres décimaux) affichés selon le format réel.",
            "Simulateur aligné sur les trames réelles, checksums compris.",
        ),
    ),
    ChangelogEntry(
        version = "0.4",
        date = "22/11/2026 01:38",
        items = listOf(
            "Notification du service réduite au minimum : plus d'icône ni de texte de recherche dans la barre d'état.",
            "Trames reçues journalisées aussi sur la vraie trottinette, avec la dernière trame batterie/trip/odo dans les informations techniques.",
        ),
    ),
    ChangelogEntry(
        version = "0.3",
        date = "22/11/2026 01:26",
        items = listOf(
            "Corrige le plantage au lancement tant que l'autorisation Bluetooth n'est pas accordée.",
            "La liaison affiche un message clair quand l'autorisation manque.",
        ),
    ),
    ChangelogEntry(
        version = "0.2",
        date = "22/11/2026 01:07",
        items = listOf(
            "Widget 1×1 : logo et coche verte à 25 km/h, rouge sinon, grise hors ligne ; un appui passe toujours à 25.",
            "Données du tableau de bord plus compactes et alignées à droite de la vitesse.",
            "Phare automatique : 30 min avant le coucher et 30 min après le lever, lever affiché avant coucher.",
            "Crédits de développement dans les réglages.",
        ),
    ),
    ChangelogEntry(
        version = "0.1",
        date = "22/11/2026 00:49",
        items = listOf(
            "Connexion automatique BLE (E-TWOW / GTSport) et mode simulateur pour l'émulateur.",
            "Tableau de bord : vitesse et, à droite, batterie, trip et odomètre.",
            "Verrou et phare en un appui chacun, état visible par la couleur du bouton.",
            "Limites de vitesse 6/20/25/42 km/h, triées.",
            "Phare automatique au coucher et au lever du soleil, réglable dans les commandes.",
            "Widget 25 km/h entièrement cliquable, qui restaure la limite précédente.",
            "Réglages et informations techniques sur des pages séparées (trames brutes, copie).",
            "Journal des modifications accessible en cliquant sur la version.",
        ),
    ),
)
