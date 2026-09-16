import { Component } from "react";
import type { ReactNode } from "react";

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  public state: ErrorBoundaryState = { hasError: false };

  public static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  public componentDidCatch(): void {
    // An approved telemetry integration can be added here without exposing error details to users.
  }

  public render() {
    if (this.state.hasError) {
      return (
        <main aria-labelledby="application-error-title" className="error-page">
          <h1 id="application-error-title">Something went wrong</h1>
          <p>Please refresh the page and try again.</p>
        </main>
      );
    }

    return this.props.children;
  }
}
