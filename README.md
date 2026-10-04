# Tone Stepper

App Android Kotlin che genera in tempo reale una sinusoide con centro predefinito a 15.000 Hz.

Sequenza:
-2 → -1 → 0 → +1 → +2 → +1 → 0 → -1 semitoni, poi ripete.

Default:
- Centro: 15000 Hz
- Durata: 10 s per tono (personalizzabile 0,5–60 s)
- Sample rate: 48 kHz
- Fade: 75 ms
- Volume interno iniziale: 10%

## Aprire
Apri la cartella `ToneStepper` in Android Studio, lascia completare Gradle Sync e premi Run sul telefono.

Nota: il progetto non include il Gradle Wrapper binario; Android Studio può usare/configurare il Gradle compatibile.

## Sicurezza
Le frequenze 15–17 kHz sono molto alte. Parti a volume basso, soprattutto in cuffia. Non aumentare il volume solo perché il tono sembra poco udibile.


## Compilazione automatica con GitHub Actions

1. Crea un repository GitHub.
2. Carica TUTTO il contenuto di questa cartella nella radice del repository, inclusa `.github`.
3. Apri la scheda **Actions**.
4. Se necessario, seleziona **Build Android APK** e premi **Run workflow**.
5. Attendi il completamento del job.
6. Apri il job completato e, nella sezione **Artifacts**, scarica **ToneStepper-APK**.
7. Estrai lo ZIP dell'artifact: contiene `app-debug.apk`.

L'APK debug è installabile manualmente su Android.
