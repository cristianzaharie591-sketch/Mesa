# BLACK MESA WATCH — Android

Proiect Android pentru Half-Life 1 Steam (App ID 70).

## Ce face
- Afișează countdown până la data de 17 decembrie 2026.
- Citește prețul din Steam Store API pentru regiunea România (`cc=ro`).
- Dacă Steam returnează altă monedă decât RON, încearcă o conversie orientativă prin Frankfurter API.
- Programează o notificare zilnică în jurul orei 15:00, ora Europei/București.
- Deschide pagina oficială Steam pentru Half-Life.

## Construirea APK-ului fără PC
1. Creează un repository GitHub nou și încarcă toate fișierele din acest proiect.
2. Deschide fila **Actions** și rulează workflow-ul **Build unsigned APK** (sau așteaptă build-ul automat după push).
3. Deschide rularea terminată și descarcă artifact-ul `BlackMesaWatch-unsigned`.
4. Dezarhivează artifact-ul pe telefon. APK-ul este nesemnat.

## Semnare
Semnează `app-release-unsigned.apk` cu aplicația Android de semnare APK pe care o alegi. Păstrează cheia de semnare într-un loc sigur; aceeași cheie este necesară pentru actualizări viitoare ale aplicației.

## Note
- Notificarea este programată pentru aproximativ 15:00; Android poate întârzia alarmele în modul de economisire a bateriei.
- Permite notificările când Android te întreabă.
- Prețul și cursul valutar necesită internet. Conversia în RON este estimativă.
- Data de 17 decembrie 2026 este introdusă în aplicație ca dată anunțată; actualizeaz-o în cod dacă Valve schimbă calendarul.
