import {Theme} from '../../../services/theme.service';

const VOYAGER = 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png';
const DARK_MATTER = 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png';

export function tileUrlForTheme(theme: Theme): string {
  return theme === 'dark' ? DARK_MATTER : VOYAGER;
}

export const CARTO_TILE_OPTIONS = {
  attribution:
    '&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a> &copy; <a href="https://carto.com/attributions">CARTO</a>',
  subdomains: 'abcd',
  maxZoom: 19,
};
