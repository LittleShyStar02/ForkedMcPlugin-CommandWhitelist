# PROGRESS

## Fatto
- 2026-10-31: audit in `CommandWhitelist-Problemi-e-Miglioramenti.pdf`.
- 2026-10-31: migrazione da Maven a Gradle, jar unico `build/libs/CommandWhitelist-2.14.0.jar`. Moduli: common, bukkit, paper, folia, velocity, waterfall, bundle. Backup del progetto Maven: `../ForkedMcPlugin-CommandWhitelist-master.backup-2026-10-31`.
- Corretti i punti dell'audit, escluse le decisioni dell'utente: N08, N19, N20 non toccati; unione dei gruppi invariata (punto 12, solo cache); versione minima 1.13.
- Punto 01: WHITELIST esatta, BLACKLIST ignora maiuscole, perche' 1.13.2 e 1.16.5 eseguono davvero /VERSION.
- JUnit 32/32. Test con bot mineflayer in `tests/e2e` passati su Paper 1.13.2, 1.16.5, 1.20.4, 1.21.11, 26.1.2; Folia 1.20.4, 1.21.11; Velocity 3.5.1, 4.3.1; Waterfall 1.21; Spigot 1.21.11.

## Da fare
- Limiti Spigot puro: i redirect vanilla (`tp` di `teleport`) non vengono riconosciuti e l'albero non viene potato. Spigot 1.13 non provato.
- Workflow CI (punto 16): non aggiunto, da approvare.
- Nessun commit: la cartella non e' un repository git.
