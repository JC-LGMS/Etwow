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
