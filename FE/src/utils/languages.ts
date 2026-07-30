export interface LanguageOption {
  code: string
  label: string
}

export const TARGET_LANGUAGES: LanguageOption[] = [
  { code: 'en', label: 'English' },
  { code: 'vi', label: 'Tiếng Việt' },
  { code: 'ja', label: '日本語' },
]

export const DEFAULT_TARGET_LANGUAGE_CODE = 'en'

export function languageLabel(code: string | null | undefined): string {
  if (!code) return '—'
  return TARGET_LANGUAGES.find((l) => l.code === code)?.label ?? code
}
