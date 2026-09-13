package app.pocketful.data

import app.pocketful.AppVersion

/** How the app introduces itself to every host it talks to. */
object Network {
    /**
     * Built from [AppVersion] so a new release says so without anyone editing a second place,
     * and with an address so whoever reads it in a log can reach the person responsible.
     */
    val USER_AGENT: String = "Pocketful/${AppVersion.NAME} (+https://github.com/TronVonDoom/Pocketful)"
}
