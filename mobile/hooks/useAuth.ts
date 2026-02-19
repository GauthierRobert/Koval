import React, { createContext, useContext, useEffect, useState } from 'react';
import { User, fetchCurrentUser, loginWithGoogle, logout as logoutService } from '../services/authService';
import { getToken } from '../services/api';

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  login: () => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue>({
  user: null,
  loading: true,
  login: async () => {},
  logout: async () => {},
});

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  // On mount — try to restore session from SecureStore
  useEffect(() => {
    (async () => {
      try {
        const token = await getToken();
        if (token) {
          const me = await fetchCurrentUser();
          setUser(me);
        }
      } catch {
        // Token expired or invalid — stay logged out
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  async function login() {
    setLoading(true);
    try {
      const me = await loginWithGoogle();
      setUser(me);
    } finally {
      setLoading(false);
    }
  }

  async function logout() {
    await logoutService();
    setUser(null);
  }

  return React.createElement(
    AuthContext.Provider,
    { value: { user, loading, login, logout } },
    children
  );
}

export function useAuth(): AuthContextValue {
  return useContext(AuthContext);
}
