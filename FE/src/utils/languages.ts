export interface LanguageOption {
  code: string
  label: string
}

export const TARGET_LANGUAGES: LanguageOption[] = [
  { code: 'en', label: 'English' },
  { code: 'vi', label: 'Tiếng Việt' },
  { code: 'ja', label: '日本語' },
]

// Same supported-language set, reused for "what language is the video in" - kept as a
// separate export so call sites read clearly even though the underlying list is shared.
export const SOURCE_LANGUAGES: LanguageOption[] = TARGET_LANGUAGES

// Defaults differ on purpose: most users here are Vietnamese speakers picking an
// English (or other) video to learn from, and want translations in their own language.
// If both defaulted to the same code, the translation/download-with-translation
// controls would start out disabled (nothing to translate) with no obvious reason why.
export const DEFAULT_TARGET_LANGUAGE_CODE = 'vi'
export const DEFAULT_SOURCE_LANGUAGE_CODE = 'en'

export function languageLabel(code: string | null | undefined): string {
  if (!code) return '—'
  return TARGET_LANGUAGES.find((l) => l.code === code)?.label ?? code
}
