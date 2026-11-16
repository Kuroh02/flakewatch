import type { components } from '../api/schema';

/** API types, generated from api/openapi.yaml (Constitution Article 5). Never written by hand. */
type Schemas = components['schemas'];
export type Project = Schemas['Project'];
export type ProjectPage = Schemas['ProjectPage'];
export type ProjectSummary = Schemas['ProjectSummary'];
export type TestSummary = Schemas['TestSummary'];
export type TestSummaryPage = Schemas['TestSummaryPage'];
export type TestDetail = Schemas['TestDetail'];
export type Evidence = Schemas['Evidence'];
export type ExecutionPage = Schemas['ExecutionPage'];
export type RunSummary = Schemas['RunSummary'];
export type RunPage = Schemas['RunPage'];
export type RunDetail = Schemas['RunDetail'];
export type FailureGroup = Schemas['FailureGroup'];
export type FailureGroupPage = Schemas['FailureGroupPage'];
export type FailureGroupDetail = Schemas['FailureGroupDetail'];

/** An error from the API, carrying the HTTP status and the server's explanation. */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export async function apiGet<T>(path: string, signal?: AbortSignal): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, { headers: { accept: 'application/json' }, signal });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      throw e;
    }
    throw new ApiError(0, 'Could not reach the Flakewatch server. It may be waking up; try again in a moment.');
  }
  if (!response.ok) {
    let detail = '';
    try {
      const problem = (await response.json()) as { detail?: string };
      detail = problem.detail ?? '';
    } catch {
      // not JSON: keep the status only
    }
    throw new ApiError(response.status, detail || `The server answered ${response.status}.`);
  }
  return (await response.json()) as T;
}

/** Builds an API path with only the non-empty query values, in the given order. */
export function apiPath(path: string, query: Array<[string, string | undefined]> = []): string {
  const params = new URLSearchParams();
  for (const [key, value] of query) {
    if (value !== undefined && value !== '') {
      params.set(key, value);
    }
  }
  const search = params.toString();
  return search ? `${path}?${search}` : path;
}
