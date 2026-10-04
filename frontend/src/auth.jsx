import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, tokenStore } from './api.js';

const AuthContext = createContext(null);
const USER_KEY = 'gym_user';

function loadUser() {
  try {
    const u = JSON.parse(localStorage.getItem(USER_KEY));
    return u && tokenStore.get() ? u : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(loadUser);

  const logout = useCallback(() => {
    tokenStore.clear();
    localStorage.removeItem(USER_KEY);
    setUser(null);
  }, []);

  const login = useCallback(async (username, password) => {
    const res = await api.post('/api/auth/login', { username, password });
    tokenStore.set(res.token);
    const u = { username: res.username, fullName: res.fullName, role: res.role };
    localStorage.setItem(USER_KEY, JSON.stringify(u));
    setUser(u);
    return u;
  }, []);

  useEffect(() => {
    window.addEventListener('gym-unauthorized', logout);
    return () => window.removeEventListener('gym-unauthorized', logout);
  }, [logout]);

  const value = useMemo(() => ({ user, login, logout, isAdmin: user?.role === 'ADMIN' }), [user, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
