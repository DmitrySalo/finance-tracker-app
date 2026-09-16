import { useContext } from "react";
import { NotificationContext } from "./NotificationProvider";

export function useNotifications() {
  const value = useContext(NotificationContext);
  if (value === null) {
    throw new Error("useNotifications must be used within NotificationProvider.");
  }

  return value;
}
