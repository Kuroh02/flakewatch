import { Link } from './Link';

/** "Next page" and "First page" links. The cursor lives in the URL, so every page can be shared (US-03 #3). */
export function Pager({ nextHref, firstHref }: { nextHref: string | null; firstHref: string | null }) {
  if (!nextHref && !firstHref) {
    return null;
  }
  return (
    <nav className="pager" aria-label="Pages">
      {firstHref ? <Link href={firstHref}>← First page</Link> : <span />}
      {nextHref && <Link href={nextHref}>Next page →</Link>}
    </nav>
  );
}
