import { fileURLToPath } from 'node:url'
import { mergeConfig, defineConfig, configDefaults } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(
  viteConfig,
  defineConfig({
    resolve: {
      alias: {
        '@vue/test-utils': fileURLToPath(
          new URL('./node_modules/@vue/test-utils', import.meta.url),
        ),
      },
    },
    server: {
      fs: {
        allow: [fileURLToPath(new URL('../', import.meta.url))],
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      exclude: [...configDefaults.exclude, 'e2e/**'],
      include: ['../tests/**/*.{test,spec}.ts'],
      root: fileURLToPath(new URL('./', import.meta.url)),
    },
  }),
)
