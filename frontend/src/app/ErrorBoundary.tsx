import { Component } from "react";
import type { ContextType, ReactNode } from "react";
import { LocalizationContext } from "../shared/localization/LocalizationProvider";

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  public static contextType = LocalizationContext;
  declare context: ContextType<typeof LocalizationContext>;
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
           <h1 id="application-error-title">{this.context?.t("error.title")}</h1>
           <p>{this.context?.t("error.refresh")}</p>
        </main>
      );
    }

    return this.props.children;
  }
}
