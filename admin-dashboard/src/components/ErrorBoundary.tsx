import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button } from '@/components/ui/Button';

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Last-resort guard: a rendering bug shows a recoverable panel instead of a
 * blank page. Details stay out of the production UI.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    if (import.meta.env.DEV) {
      console.error('[ProxiLink] render error', error, info.componentStack);
    }
  }

  render(): ReactNode {
    if (!this.state.error) return this.props.children;
    return (
      <div className="flex min-h-screen items-center justify-center bg-navy-900 p-6">
        <div className="w-full max-w-md rounded-2xl border border-white/10 bg-navy-850 p-6 text-center shadow-card">
          <h1 className="text-lg font-semibold text-slate-100">Something went wrong</h1>
          <p className="mt-2 text-sm leading-relaxed text-slate-400">
            The console hit an unexpected rendering error. Reloading usually restores the session.
          </p>
          <Button className="mt-5" onClick={() => window.location.reload()}>
            Reload console
          </Button>
        </div>
      </div>
    );
  }
}
