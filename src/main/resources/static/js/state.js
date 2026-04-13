import { CATALOG_KEY } from './config.js';

export const state = { catalog: [], ranking: [], rankingToday: [], history: [], session: null, hasCatalogEndpoint: false, filters: { search: "", genre: "", type: "", year: "" }, currentUser: null };

export const normalize = (value) => (value || "").toString().normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
export const getCache = () => { try { return JSON.parse(localStorage.getItem(CATALOG_KEY) || "[]"); } catch { return []; } };
export const setCache = (data) => localStorage.setItem(CATALOG_KEY, JSON.stringify(data));
export const mergeCatalog = (...lists) => Array.from(new Map(lists.flat().map((item, i) => [(item.id || `${normalize(item.titulo)}-${item.anioEstreno || i}`), item])).values());
export const loadUserFromStorage = () => { try { const u = localStorage.getItem("streamflix.user"); if(u) state.currentUser = JSON.parse(u); } catch {} };
export const saveUserToStorage = (user) => { state.currentUser = user; if(user) localStorage.setItem("streamflix.user", JSON.stringify(user)); else localStorage.removeItem("streamflix.user"); };
