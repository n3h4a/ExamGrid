import { useSearchParams } from 'react-router';

/** A single URL query parameter as React state, so searches survive reloads and links. */
export function useSearchParam(name: string): [string, (value: string) => void] {
  const [params, setParams] = useSearchParams();
  const value = params.get(name) ?? '';
  const set = (next: string) =>
    setParams(
      (prev) => {
        const p = new URLSearchParams(prev);
        if (next) p.set(name, next);
        else p.delete(name);
        return p;
      },
      { replace: true },
    );
  return [value, set];
}
