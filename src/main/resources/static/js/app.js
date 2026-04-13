import { demo, USER_KEY } from './config.js';
import { state, getCache, setCache, mergeCatalog } from './state.js';
import { api } from './api.js';
import { $, setStatus, renderFilters, renderCatalog, renderRanking, renderSession, openModal, closeModal } from './ui.js';

async function refreshCatalog() {
    setStatus("Actualizando catalogo...");
    const full = await api.fetchCatalog();
    state.catalog = mergeCatalog(getCache(), full || []);
    setCache(state.catalog);
    $("mode-badge").textContent = state.hasCatalogEndpoint ? "GET /contenidos activo" : "Modo fallback";
    $("catalog-source").textContent = state.hasCatalogEndpoint ? "Backend completo" : "Cache local y backend por genero";
    setStatus(state.hasCatalogEndpoint ? "Catalogo cargado desde GET /api/contenidos." : "GET /api/contenidos aun no existe. Usando fallback.");
    renderFilters(); renderCatalog(); renderRanking(); renderSession();
}

async function refreshRanking() { try { state.ranking = await api.ranking(); renderRanking(); setStatus("Ranking actualizado."); } catch (e) { setStatus(`No se pudo actualizar el ranking: ${e.message}`); } }
async function fetchGenre() { const genre = state.filters.genre || $("genre-filter").value; if (!genre) return setStatus("Selecciona un genero."); try { const data = await api.fetchGenre(genre); state.catalog = mergeCatalog(state.catalog, data); setCache(state.catalog); renderFilters(); renderCatalog(); setStatus(`Se cargaron ${data.length} titulos para ${genre}.`); } catch (e) { setStatus(`No se pudo consultar el genero: ${e.message}`); } }
async function loadSession() { const userId = $("user-id").value.trim(); if (!userId) return setStatus("Ingresa un usuario."); try { state.session = await api.getSession(userId); localStorage.setItem(USER_KEY, userId); renderSession(); setStatus(`Sesion cargada para ${userId}.`); } catch { state.session = null; renderSession(); setStatus(`No existe sesion activa para ${userId}.`); } }
async function registerView(id, title = "contenido") { if (!id) return setStatus(`"${title}" aun no tiene ID persistido.`); try { await api.view(id); await refreshRanking(); setStatus(`Vista registrada para ${title}.`); } catch (e) { setStatus(`No se pudo registrar la vista: ${e.message}`); } }
async function saveSession(id, title = "contenido") { const userId = $("user-id").value.trim(); if (!userId) return setStatus("Ingresa un usuario."); if (!id) return setStatus(`"${title}" aun no tiene ID persistido.`); try { await api.saveSession(userId, id); state.session = { userId, lastContentId: id, timestamp: Math.floor(Date.now() / 1000) }; localStorage.setItem(USER_KEY, userId); renderSession(); setStatus(`Sesion guardada para ${userId}.`); } catch (e) { setStatus(`No se pudo guardar la sesion: ${e.message}`); } }
async function deleteContent(id, title = "contenido") { if (!id) return setStatus(`"${title}" aun no tiene ID persistido.`); if (!window.confirm(`Vas a eliminar "${title}" del catalogo. Deseas continuar?`)) return; try { await api.remove(id); state.catalog = state.catalog.filter((item) => item.id !== id); if (state.session?.lastContentId === id) state.session = null; setCache(state.catalog); renderFilters(); renderCatalog(); renderRanking(); renderSession(); closeModal(); setStatus(`Contenido eliminado: ${title}.`); } catch (e) { setStatus(`No se pudo eliminar el contenido: ${e.message}`); } }

async function createContent(event) {
    event.preventDefault();
    const data = new FormData($("create-form"));
    const payload = {
        titulo: String(data.get("titulo") || "").trim(),
        tipo: String(data.get("tipo") || "").trim(),
        director: String(data.get("director") || "").trim(),
        anioEstreno: Number(data.get("anioEstreno")),
        generos: String(data.get("generos") || "").split(",").map((v) => v.trim()).filter(Boolean),
        metadatos: {
            calidad: String(data.get("calidad") || "").trim(),
            idiomas: String(data.get("idiomas") || "").split(",").map((v) => v.trim()).filter(Boolean),
            subtitulos: String(data.get("subtitulos") || "").split(",").map((v) => v.trim()).filter(Boolean)
        }
    };
    try { const created = await api.create(payload); state.catalog = mergeCatalog(state.catalog, [created]); setCache(state.catalog); $("create-form").reset(); renderFilters(); renderCatalog(); setStatus(`Contenido creado: ${created.titulo}.`); } catch (e) { setStatus(`No se pudo crear el contenido: ${e.message}`); }
}

async function seedDemo() {
    setStatus("Insertando catalogo demo...");
    const created = [];
    for (const item of demo) { try { created.push(await api.create(item)); } catch {} }
    state.catalog = mergeCatalog(state.catalog, created);
    setCache(state.catalog);
    renderFilters(); renderCatalog();
    setStatus(created.length ? `Se insertaron ${created.length} titulos demo.` : "No se insertaron titulos demo.");
}

function bind() {
    $("search").addEventListener("input", (e) => { state.filters.search = e.target.value; $("search-mobile").value = e.target.value; renderCatalog(); });
    $("search-mobile").addEventListener("input", (e) => { state.filters.search = e.target.value; $("search").value = e.target.value; renderCatalog(); });
    $("genre-filter").addEventListener("change", (e) => { state.filters.genre = e.target.value; renderCatalog(); });
    $("type-filter").addEventListener("change", (e) => { state.filters.type = e.target.value; renderCatalog(); });
    $("year-filter").addEventListener("input", (e) => { state.filters.year = e.target.value; renderCatalog(); });
    $("reset-filters").addEventListener("click", () => { state.filters = { search: "", genre: "", type: "", year: "" }; $("search").value = ""; $("search-mobile").value = ""; $("genre-filter").value = ""; $("type-filter").value = ""; $("year-filter").value = ""; renderCatalog(); });
    $("refresh-catalog").addEventListener("click", refreshCatalog);
    $("refresh-ranking").addEventListener("click", refreshRanking);
    $("fetch-genre").addEventListener("click", fetchGenre);
    $("load-session").addEventListener("click", loadSession);
    $("seed-demo").addEventListener("click", seedDemo);
    $("close-modal").addEventListener("click", closeModal);
    $("create-form").addEventListener("submit", createContent);
    $("catalog").addEventListener("click", async (e) => {
        const button = e.target.closest("button"); if (!button) return;
        const item = state.catalog.find((entry) => entry.id === button.dataset.id) || state.catalog.find((entry) => entry.titulo === button.dataset.title); if (!item) return;
        if (button.dataset.action === "detail") openModal(item);
        if (button.dataset.action === "play") { await registerView(item.id, item.titulo); await saveSession(item.id, item.titulo); }
    });
    $("modal-body").addEventListener("click", async (e) => {
        const playId = e.target.getAttribute("data-modal-play"); const sessionId = e.target.getAttribute("data-modal-session"); const deleteId = e.target.getAttribute("data-modal-delete"); const deleteTitle = e.target.getAttribute("data-modal-title");
        if (playId !== null) { const item = state.catalog.find((entry) => entry.id === playId); await registerView(playId, item?.titulo); closeModal(); }
        if (sessionId !== null) { const item = state.catalog.find((entry) => entry.id === sessionId); await saveSession(sessionId, item?.titulo); closeModal(); }
        if (deleteId !== null) await deleteContent(deleteId, deleteTitle || "contenido");
    });
    $("modal").addEventListener("click", (e) => { if (e.target === $("modal")) closeModal(); });
}

async function init() {
    $("user-id").value = localStorage.getItem(USER_KEY) || "demo-user";
    state.catalog = mergeCatalog(getCache());
    bind();
    renderFilters(); renderCatalog(); renderRanking(); renderSession();
    await refreshCatalog();
    await refreshRanking();
}

// Inicializar la aplicación
init();
