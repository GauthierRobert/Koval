// @ts-check
import eslint from '@eslint/js';
import tseslint from 'typescript-eslint';
import angular from 'angular-eslint';

export default tseslint.config(
  {
    ignores: [
      'dist/**',
      'out-tsc/**',
      'coverage/**',
      'node_modules/**',
      '.angular/**',
      'src/environments/environment.ts',
    ],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...tseslint.configs.stylistic,
      ...angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/component-class-suffix': 'error',
      '@angular-eslint/directive-class-suffix': 'error',
      '@angular-eslint/no-output-native': 'error',
      '@angular-eslint/prefer-on-push-component-change-detection': 'warn',
      '@angular-eslint/no-empty-lifecycle-method': 'warn',
      '@angular-eslint/prefer-inject': 'warn',
      // 72 baseline `any` casts — surface as warning until Phase 3.4 cleanup,
      // then flip to error.
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-empty-function': 'warn',
      '@typescript-eslint/no-inferrable-types': 'warn',
      '@typescript-eslint/no-unused-vars': ['warn', {argsIgnorePattern: '^_', varsIgnorePattern: '^_'}],
      '@typescript-eslint/consistent-type-definitions': 'off',
      '@typescript-eslint/consistent-generic-constructors': 'off',
      '@typescript-eslint/consistent-indexed-object-style': 'off',
      '@typescript-eslint/array-type': 'off',
      '@typescript-eslint/prefer-for-of': 'off',
    },
  },
  {
    files: ['**/*.html'],
    extends: [
      ...angular.configs.templateRecommended,
      ...angular.configs.templateAccessibility,
    ],
    rules: {
      // Real bug-prone patterns — keep as error.
      '@angular-eslint/template/eqeqeq': 'error',
      '@angular-eslint/template/no-negated-async': 'error',
      '@angular-eslint/template/prefer-control-flow': 'error',
      '@angular-eslint/template/alt-text': 'error',
      // Legacy a11y backlog: surface but don't break the build until cleanup.
      '@angular-eslint/template/label-has-associated-control': 'warn',
      '@angular-eslint/template/click-events-have-key-events': 'warn',
      '@angular-eslint/template/interactive-supports-focus': 'warn',
      // Stylistic — Angular 17+ self-closing tags. Surface as info only.
      '@angular-eslint/template/prefer-self-closing-tags': 'warn',
    },
  },
  // Allow `any` in spec files; tests inevitably touch internals.
  {
    files: ['**/*.spec.ts', 'e2e/**/*.ts'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
    },
  },
);
