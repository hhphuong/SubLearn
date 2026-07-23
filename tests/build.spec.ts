import { execSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import path from 'node:path'

const feDir = path.resolve(__dirname, '../FE')

describe('production build', () => {
  it(
    'builds successfully and produces expected output files',
    () => {
      execSync('npm run build', { cwd: feDir, stdio: 'inherit' })
      expect(existsSync(path.join(feDir, 'dist', 'index.html'))).toBe(true)
      expect(existsSync(path.join(feDir, 'dist', 'assets'))).toBe(true)
    },
    60_000,
  )
})
