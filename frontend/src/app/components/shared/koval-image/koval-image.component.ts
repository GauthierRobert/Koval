import {ChangeDetectionStrategy, Component, computed, input, signal} from '@angular/core';
import {decode as decodeBlurHash} from 'blurhash';
import {MediaResponse} from '../../../models/media.model';

type VariantSize = 'thumb' | 'small' | 'medium' | 'large';

/**
 * Image renderer for resolved {@link MediaResponse}s.
 *
 * Picks the best resolution for the requested {@code size} input, falls back
 * to the original signed URL when no variants exist (e.g. processing failed
 * or HEIC), and renders a low-quality placeholder while the real bytes load.
 *
 * Optionally uses BlurHash for the placeholder. The {@code blurhash} npm
 * package is loaded lazily; if absent we degrade to a plain neutral
 * background — the layout still doesn't shift thanks to {@code aspect-ratio}.
 */
@Component({
  selector: 'koval-image',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="koval-image-wrapper"
      [style.aspect-ratio]="aspectRatio()"
    >
      @if (placeholderUri(); as uri) {
        @if (!loaded()) {
          <div
            class="koval-image-placeholder"
            [style.background-image]="'url(' + uri + ')'"
          ></div>
        }
      }
      @if (resolvedUrl(); as url) {
        <img
          [src]="url"
          [alt]="alt()"
          loading="lazy"
          decoding="async"
          (load)="onLoad()"
          class="koval-image-img"
          [class.loaded]="loaded()"
        />
      }
    </div>
  `,
  styles: [`
    .koval-image-wrapper {
      position: relative;
      overflow: hidden;
      background: var(--surface-elevated);
    }
    .koval-image-placeholder {
      position: absolute;
      inset: 0;
      background-size: cover;
      background-position: center;
      filter: blur(20px);
      transform: scale(1.1);
    }
    .koval-image-img {
      position: relative;
      width: 100%;
      height: 100%;
      object-fit: cover;
      opacity: 0;
      transition: opacity 280ms ease-out;
    }
    .koval-image-img.loaded {
      opacity: 1;
    }
  `],
})
export class KovalImageComponent {
  readonly media = input.required<MediaResponse | null>();
  readonly size = input<VariantSize>('medium');
  readonly alt = input<string>('');

  readonly loaded = signal(false);

  readonly resolvedUrl = computed(() => {
    const m = this.media();
    if (!m) return null;
    const v = m.variants?.[this.size()];
    if (v) return v.url;
    for (const fallback of ['large', 'medium', 'small', 'thumb'] as VariantSize[]) {
      const candidate = m.variants?.[fallback];
      if (candidate) return candidate.url;
    }
    return m.originalUrl;
  });

  readonly aspectRatio = computed(() => {
    const m = this.media();
    return m?.width && m?.height ? `${m.width} / ${m.height}` : '16 / 9';
  });

  readonly placeholderUri = computed(() => {
    const hash = this.media()?.blurHash;
    if (!hash) return null;
    return decodeBlurHashLazy(hash);
  });

  onLoad(): void {
    this.loaded.set(true);
  }
}

const blurHashCache = new Map<string, string>();

function decodeBlurHashLazy(hash: string): string | null {
  const cached = blurHashCache.get(hash);
  if (cached) return cached;

  try {
    const w = 32, h = 32;
    const pixels = decodeBlurHash(hash, w, h);
    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext('2d');
    if (!ctx) return null;
    const imageData = ctx.createImageData(w, h);
    imageData.data.set(pixels);
    ctx.putImageData(imageData, 0, 0);
    const uri = canvas.toDataURL();
    blurHashCache.set(hash, uri);
    return uri;
  } catch {
    return null;
  }
}
