/** @type {import('stylelint').Config} */
export default {
  extends: ['stylelint-config-standard-scss'],
  plugins: ['stylelint-declaration-strict-value'],
  rules: {
    // ── Design tokens enforcement ────────────────────────────────
    // Properties below MUST use a CSS variable (var(--*)). Raw hex /
    // rgb / oklch values are flagged so new code can't drift away
    // from the design system in styles.scss.
    // 279 legacy violations remain across component CSS — flag them as
    // warnings so the codebase still lints clean while migrators surface
    // the drift. lint-staged should run with --max-warnings=0 once the
    // baseline is cleared so new violations break the commit.
    'scale-unlimited/declaration-strict-value': [
      ['/color$/', 'background-color', 'background', 'border-color', 'fill', 'stroke', 'box-shadow'],
      {
        ignoreValues: [
          'inherit',
          'currentColor',
          'transparent',
          'none',
          'initial',
          'unset',
          'revert',
          '/^linear-gradient\\(/',
          '/^radial-gradient\\(/',
          '/^var\\(--/',
          '/^url\\(/',
        ],
        disableFix: true,
        severity: 'warning',
      },
    ],

    // ── Project-specific allowances ──────────────────────────────
    // Token names use kebab-case; allow our `--space-2xl` style.
    'custom-property-pattern': null,
    'selector-class-pattern': null,
    'no-descending-specificity': null,
    // Component CSS often selects nested elements — keep it readable.
    'no-empty-source': null,
    // Allow vendor-prefixed properties (we ship -webkit-backdrop-filter etc).
    'property-no-vendor-prefix': null,
    'value-no-vendor-prefix': null,
    'at-rule-no-vendor-prefix': null,
    // Angular emits :host, ::ng-deep, etc.
    'selector-pseudo-class-no-unknown': [true, {ignorePseudoClasses: ['host', 'host-context']}],
    'selector-pseudo-element-no-unknown': [true, {ignorePseudoElements: ['ng-deep']}],
    // SCSS @use 'styles/buttons' is a partial reference, not a URL
    'scss/load-no-partial-leading-underscore': null,
    // Permit double slashes in SCSS comments
    'scss/comment-no-empty': null,
    // Don't be too strict on number-max-precision — oklch needs three decimals
    'number-max-precision': 4,
    // Allow `@apply`-style empty-line rules common in our partials
    'declaration-empty-line-before': null,
    'rule-empty-line-before': null,
    'comment-empty-line-before': null,
    // Codebase uses legacy `@media (max-width: …)`; don't churn over modern range syntax.
    'media-feature-range-notation': null,
    // Keep `min-height: 80px;` etc. — pixel literals are fine for layout.
    'length-zero-no-unit': null,
    // Hex shorthand mixed (3 vs 6 digits) — too noisy across legacy code.
    'color-hex-length': null,
    'color-function-notation': null,
    'alpha-value-notation': null,
    // SCSS `@use` with no namespace is the file's own choice.
    'scss/at-use-no-unnamespaced': null,
    // ── Stylistic notation preferences we don't enforce ──────────
    // The design tokens use `oklch(0.75 0.18 65)` and `rgba(…, 0.5)`;
    // flipping to `oklch(75% 0.18 65deg)` or `rgb(… / 50%)` would churn
    // the entire styles.scss without changing pixels.
    'lightness-notation': null,
    'hue-degree-notation': null,
    'color-function-alias-notation': null,
    'custom-property-empty-line-before': null,
    'shorthand-property-no-redundant-values': null,
    'declaration-block-no-shorthand-property-overrides': null,
    'declaration-block-no-redundant-longhand-properties': null,
    'no-duplicate-selectors': null,
    'font-family-name-quotes': null,
    'font-family-no-missing-generic-family-keyword': null,
    'value-keyword-case': null,
    'selector-not-notation': null,
    'media-query-no-invalid': null,
    'media-feature-name-no-vendor-prefix': null,
    'declaration-property-value-no-unknown': null,
    'function-no-unknown': null,
    'no-empty-source': null,
    // Modal/overlay z-index values are ad-hoc but intentional; don't mandate a scale.
    'declaration-property-value-keyword-no-deprecated': null,
    'scss/no-global-function-names': null,
    'scss/operator-no-newline-after': null,
    'scss/operator-no-unspaced': null,
    'scss/at-mixin-pattern': null,
    'scss/at-function-pattern': null,
    'scss/dollar-variable-pattern': null,
    'scss/percent-placeholder-pattern': null,
    'scss/load-partial-extension': null,
    'scss/at-rule-conditional-no-parentheses': null,
    'scss/double-slash-comment-empty-line-before': null,
    'keyframes-name-pattern': null,
    'at-rule-empty-line-before': null,
    'at-rule-no-deprecated': null,
    'comment-whitespace-inside': null,
    'function-url-quotes': null,
    // Codebase intentionally uses one-liner rules (e.g. power-curve-page.component.css).
    'declaration-block-single-line-max-declarations': null,
    'scss/dollar-variable-colon-space-after': null,
    'selector-pseudo-element-colon-notation': null,
    'property-no-deprecated': null,
    'declaration-block-no-duplicate-properties': null,
  },
  overrides: [
    {
      files: ['**/styles.scss', '**/styles/_*.scss'],
      rules: {
        // Token definitions live here — they ARE the source values.
        'scale-unlimited/declaration-strict-value': null,
      },
    },
  ],
  ignoreFiles: [
    'dist/**/*',
    'out-tsc/**/*',
    'coverage/**/*',
    'node_modules/**/*',
    '.angular/**/*',
  ],
};
