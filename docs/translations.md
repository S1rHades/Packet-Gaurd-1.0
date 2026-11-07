# Translations

The interface text is in `ByeDPI-Guard/app/src/main/res/values*/strings_ui.xml` (keys start with
`pg_`); `strings.xml` holds the older text from ByeByeDPI. English (`values`) is the default.

| Language | Folder | Status |
|---|---|---|
| English | `values` | Source text |
| Russian | `values-ru` | Written without a native speaker's review |
| Turkish | `values-tr` | Written without a native speaker's review |
| Kazakh | `values-kk` | Written without a native speaker's review |
| Vietnamese | `values-vi` | Written without a native speaker's review |

## Reviewing a language

1. Open the language's `strings_ui.xml` next to `values/strings_ui.xml`; keys are in the same order.
2. Fix wording in place. Keep placeholders exactly (`%1$s`, `%1$d`, `%%`), and every `plurals`
   quantity the language needs (Russian: one, few, many, other).
3. Run `./gradlew lintDebug` and, for Russian, `./gradlew recordPaparazziDebug` to update the
   screenshots in `app/src/test/snapshots`, then check that nothing is cut off.
4. Open a pull request and change the language's status above to "Reviewed by …".
