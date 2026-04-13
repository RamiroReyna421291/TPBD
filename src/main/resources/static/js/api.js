import { API, USER_KEY } from './config.js';
import { state, saveUserToStorage } from './state.js';

export const getToken = () => localStorage.getItem("streamflix.token");
export const setToken = (token) => { if(token) localStorage.setItem("streamflix.token", token); else localStorage.removeItem("streamflix.token"); };

export async function request(path, options = {}) {
    const headers = options.headers || {};
    const token = getToken();
    if (token) headers["Authorization"] = `Bearer ${token}`;
    
    const res = await fetch(`${API}${path}`, { ...options, headers });
    
    // Si la API tira 401 o 403, es porque el token venció o no hay permisos. Limpiamos.
    if (res.status === 401 || res.status === 403) {
        if (path !== "/auth/login") {
            setToken(null);
            saveUserToStorage(null);
            if (state.currentUser) alert("Tu sesión ha expirado o no tenés permisos.");
            window.location.reload();
        }
        throw new Error("Acceso denegado o sesión expirada");
    }
    
    if (!res.ok) throw new Error(await res.text() || `HTTP ${res.status}`);
    return (res.headers.get("content-type") || "").includes("application/json") ? res.json() : null;
}

export const api = {
    login: (username, password) => request("/auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ username, password }) }),
    register: (username, email, password, admin) => request(`/auth/register?admin=${admin}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ username, email, password }) }),
    testTtl: (userId) => request(`/sesion/${encodeURIComponent(userId)}/test-ttl`, { method: "POST" }),
    
    fetchCatalog: async () => { try { const data = await request("/contenidos"); state.hasCatalogEndpoint = true; return data; } catch { state.hasCatalogEndpoint = false; return null; } },
    fetchGenre: (genre) => request(`/contenidos/genero/${encodeURIComponent(genre)}`),
    create: (body) => request("/contenidos", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }),
    remove: (id) => request(`/contenidos/${encodeURIComponent(id)}`, { method: "DELETE" }),
    ranking: () => request("/ranking/top5"),
    rankingToday: () => request("/ranking/top5/hoy"),
    fetchHistory: (userId) => request(`/vistas/historial/${encodeURIComponent(userId)}`),
    view: (id) => request(`/vistas/${encodeURIComponent(id)}`, { method: "POST" }),
    saveSession: (userId, contentId) => request(`/sesion/${encodeURIComponent(userId)}?contenidoId=${encodeURIComponent(contentId)}`, { method: "POST" }),
    getSession: (userId) => request(`/sesion/${encodeURIComponent(userId)}`)
};
