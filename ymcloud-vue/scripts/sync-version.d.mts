import type { Plugin } from 'vite';

export function parseProjectVersion(xml: string): string;
export function readProjectVersion(pomPath?: string | URL): string;
export function syncProjectVersion(directory?: string): {
  version: string;
  changed: string[];
};
export function createVersionPlugin(pomPath?: string | URL): Plugin;
