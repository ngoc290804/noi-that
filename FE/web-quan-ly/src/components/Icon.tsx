import type { SVGProps } from 'react';
const paths = {
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-3.87M15 3.13a4 4 0 0 1 0 7.75M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
  key: 'm15 7 2 2m-4-4a5 5 0 1 1-1 8l-7 7H2v-3l7-7a5 5 0 0 1 4-5Z',
  logout: 'M9 3H4v18h5m7-14 5 5-5 5M8 12h13',
  trash: 'M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7',
  box: 'm12 3 9 5v9l-9 5-9-5V8l9-5Zm0 9 9-4M12 12 3 8m9 4v10M7.5 5.5l9 5',
  tag: 'M20 13 11 22l-9-9V3h10l8 8a1.5 1.5 0 0 1 0 2ZM7 7h.01',
  image: 'M4 3h16a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Zm-1 13 5-5 5 5 3-3 5 5M8 7h.01',
  file: 'M6 3h12a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Zm3 4h5M9 11h7m-7 4h4',
  search: 'm20 20-5-5m2-5a7 7 0 1 1-14 0 7 7 0 0 1 14 0',
  plus: 'M12 4v16M4 12h16',
  download: 'M12 3v12m-5-5 5 5 5-5M4 16v4h16v-4',
  external: 'M14 3h7v7m0-7L11 13M9 4H4v16h16v-5',
  edit: 'm15 4 5 5M4 16 16 4a2 2 0 0 1 4 4L8 20l-5 1 1-5Z',
  star: 'm12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9L12 3Z',
  right: 'M4 12h16m-6-6 6 6-6 6',
  left: 'M20 12H4m6-6-6 6 6 6',
  down: 'm6 9 6 6 6-6',
  close: 'm6 6 12 12M6 18 18 6',
  menu: 'M4 6h16M4 12h16M4 18h16',
  eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Zm13 0a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  check: 'm5 12 4 4L19 6',
  upload: 'M12 16V3m-5 5 5-5 5 5M4 16v5h16v-5',
  archive: 'M3 3h18v5H3V3Zm2 5v13h14V8M9 12h6',
  info: 'M12 8h.01M12 11v6m9-5a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
} as const;
export type IconName = keyof typeof paths;
export function Icon({ name, ...props }: SVGProps<SVGSVGElement> & { name: IconName }) {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...props}><path d={paths[name]} /></svg>;
}
