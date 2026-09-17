import { AppProviders } from "./app/AppProviders";
import { AppRouter } from "./app/AppRouter";
import { ErrorBoundary } from "./app/ErrorBoundary";
import "./app/global.css";

export function App() {
  return (
    <AppProviders>
      <ErrorBoundary>
        <AppRouter />
      </ErrorBoundary>
    </AppProviders>
  );
}
