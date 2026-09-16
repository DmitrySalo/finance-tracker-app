import { createContext, useCallback, useState } from "react";
import type { ReactNode } from "react";
import styles from "./NotificationProvider.module.css";

interface Notification {
  id: number;
  message: string;
}

interface NotificationContextValue {
  notify: (message: string) => void;
}

const NotificationContext = createContext<NotificationContextValue | null>(null);

interface NotificationProviderProps {
  children: ReactNode;
}

export function NotificationProvider({ children }: NotificationProviderProps) {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const notify = useCallback((message: string) => {
    setNotifications((current) => [...current, { id: Date.now(), message }]);
  }, []);
  const dismiss = useCallback((id: number) => {
    setNotifications((current) => current.filter((notification) => notification.id !== id));
  }, []);

  return (
    <NotificationContext.Provider value={{ notify }}>
      {children}
      <div aria-live="polite" className={styles.region}>
        {notifications.map((notification) => (
          <div className={styles.notification} key={notification.id} role="status">
            <span>{notification.message}</span>
            <button aria-label="Dismiss notification" onClick={() => dismiss(notification.id)} type="button">Dismiss</button>
          </div>
        ))}
      </div>
    </NotificationContext.Provider>
  );
}

export { NotificationContext };
