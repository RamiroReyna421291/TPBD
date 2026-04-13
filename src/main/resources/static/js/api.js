import { API } from './config.js';
import { state } from './state.js';

export async function request(path, options = {}) {
    const res = await fetch(`${API}${path}`, options);
    if (!res.ok) throw new Error(await res.text() || `HTTP ${res.status}`);
    return (res.headers.get("content-type") || "").includes("application/json") ? res.json() : null;
}

export const api = {
    fetchCatalog: async () => { try { const data = await request("/contenidos"); state.hasCatalogEndpoint = true; return data; } catch { state.hasCatalogEndpoint = false; return null; } },
    fetchGenre: (genre) => request(`/contenidos/genero/${encodeURIComponent(genre)}`),
    create: (body) => request("/contenidos", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }),
    remove: (id) => request(`/contenidos/${encodeURIComponent(id)}`, { method: "DELETE" }),
    ranking: () => request("/ranking/top5"),
    view: (id) => request(`/vistas/${encodeURIComponent(id)}`, { method: "POST" }),
    saveSession: (userId, contentId) => request(`/sesion/${encodeURIComponent(userId)}?contenidoId=${encodeURIComponent(contentId)}`, { method: "POST" }),
    getSession: (userId) => request(`/sesion/${encodeURIComponent(userId)}`)
};
