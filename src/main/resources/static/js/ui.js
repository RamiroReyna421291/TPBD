import { state, normalize } from './state.js';

export const $ = (id) => document.getElementById(id);
export const setStatus = (text) => $("status").textContent = text;

export function renderFilters() {
    const genres = [...new Set(state.catalog.flatMap((item) => item.generos || []))].sort((a, b) => a.localeCompare(b));
    $("genre-filter").innerHTML = '<option value="">Todos los generos</option>' + genres.map((genre) => `<option value="${genre}">${genre}</option>`).join("");
    $("genre-filter").value = state.filters.genre;
}

export function filteredCatalog() {
    return state.catalog.filter((item) => {
        const haystack = normalize([item.titulo, item.director, ...(item.generos || [])].join(" "));
        return (!state.filters.search || haystack.includes(normalize(state.filters.search)))
            && (!state.filters.genre || (item.generos || []).some((g) => normalize(g) === normalize(state.filters.genre)))
            && (!state.filters.type || normalize(item.tipo) === normalize(state.filters.type))
            && (!state.filters.year || Number(item.anioEstreno || 0) >= Number(state.filters.year));
    });
}

export function cardGradient(index) {
    return ["from-rose-950 via-rose-800 to-amber-500", "from-sky-950 via-cyan-700 to-teal-400", "from-fuchsia-950 via-violet-700 to-indigo-400", "from-zinc-900 via-slate-700 to-orange-300"][index % 4];
}

export function renderCatalog() {
    const items = filteredCatalog();
    $("catalog-count").textContent = `${items.length} titulos`;
    $("catalog").innerHTML = items.length ? items.map((item, index) => `
        <article class="overflow-hidden rounded-[1.25rem] border border-white/10 bg-black/20">
            <div class="h-72 bg-gradient-to-br ${cardGradient(index)} p-5">
                <div class="flex h-full flex-col justify-between">
                    <div class="flex justify-between gap-3">
                        <span class="rounded-full border border-white/15 bg-black/25 px-3 py-1 text-xs uppercase">${item.tipo || "contenido"}</span>
                        <span class="rounded-full bg-white/10 px-3 py-1 text-xs">${item.metadatos?.calidad || "SD"}</span>
                    </div>
                    <div>
                        <div class="mb-3 flex flex-wrap gap-2">${(item.generos || []).slice(0, 3).map((g) => `<span class="rounded-full bg-white/10 px-2 py-1 text-[11px]">${g}</span>`).join("")}</div>
                        <h4 class="text-2xl font-black leading-tight">${item.titulo || "Sin titulo"}</h4>
                        <p class="mt-2 text-sm text-slate-200">${item.director || "Director sin definir"} • ${item.anioEstreno || "Año desconocido"}</p>
                    </div>
                </div>
            </div>
            <div class="flex items-center justify-between gap-3 p-4">
                <button data-action="detail" data-id="${item.id || ""}" data-title="${item.titulo || ""}" class="rounded-full border border-white/10 px-4 py-2 text-sm hover:bg-white/5">Ver detalle</button>
                <button data-action="play" data-id="${item.id || ""}" data-title="${item.titulo || ""}" class="rounded-full bg-ember px-4 py-2 text-sm font-semibold hover:bg-red-700">Ver ahora</button>
            </div>
        </article>`).join("") : `<div class="col-span-full rounded-[1.25rem] border border-dashed border-white/10 bg-black/20 p-8 text-center text-slate-300">No hay titulos para esos filtros.</div>`;
}

export function renderRanking() {
    const buildCards = (list) => list.length ? list.map((item, index) => {
        const content = state.catalog.find((entry) => entry.id === item.contenidoId);
        return `<article class="overflow-hidden rounded-[1.25rem] border border-white/10 bg-black/20">
            <div class="bg-gradient-to-br ${cardGradient(index)} p-4">
                <p class="text-xs uppercase tracking-[.2em] text-white/70">Puesto ${index + 1}</p>
                <h4 class="mt-4 text-xl font-black">${content?.titulo || item.contenidoId}</h4>
                <p class="mt-2 text-sm text-white/85">${content?.director || "Sin detalle en cache"}</p>
            </div>
            <div class="flex items-center justify-between p-4 text-sm"><span class="text-slate-300">Vistas</span><span class="font-semibold">${Math.round(item.vistas || 0)}</span></div>
        </article>`;
    }).join("") : `<div class="md:col-span-2 xl:col-span-5 rounded-[1.25rem] border border-dashed border-white/10 bg-black/20 p-6 text-center text-slate-300">Aún no hay datos para este ranking.</div>`;

    $("ranking").innerHTML = buildCards(state.ranking);
    if($("ranking-today")) $("ranking-today").innerHTML = buildCards(state.rankingToday);
}

export function renderHistory() {
    const pnl = $("history-panel");
    if(!pnl) return;
    if(!state.history || !state.history.length) {
        pnl.classList.add("hidden");
        return;
    }
    pnl.classList.remove("hidden");
    pnl.innerHTML = `
        <p class="text-xs uppercase tracking-[.2em] text-mist mb-3">Visto Recientemente</p>
        <div class="flex flex-col gap-2">
            ${state.history.map(id => {
                const item = state.catalog.find(c => c.id === id);
                return `<div class="rounded-xl border border-white/5 bg-white/5 px-4 py-3 flex justify-between items-center text-xs">
                    <span class="font-semibold text-white truncate mr-2">${item ? item.titulo : id}</span>
                    <button data-action="play" data-id="${id}" class="text-ember hover:text-white shrink-0 font-bold uppercase tracking-widest text-[10px]">Play</button>
                </div>`;
            }).join("")}
        </div>
    `;
}

export function renderSession() {
    if (!state.session) { $("session-card").textContent = "Sin actividad reciente o no iniciaste sesión (o Redis vació el TTL)."; return; }
    const content = state.catalog.find((item) => item.id === state.session.lastContentId);
    const when = state.session.timestamp ? new Date(state.session.timestamp * 1000).toLocaleString("es-AR") : "Sin timestamp";
    $("session-card").innerHTML = `
        <p class="text-xs uppercase tracking-[.2em] text-mist">Estado en Redis: ACTIVO</p>
        <p class="mt-2 text-xl font-bold text-white">${state.session.userId}</p>
        <div class="mt-4 rounded-2xl border border-white/10 bg-white/5 p-4">
            <p class="text-xs uppercase tracking-[.2em] text-mist">Viendo recientemente</p>
            <p class="mt-2 text-lg font-semibold text-white">${content?.titulo || state.session.lastContentId}</p>
        </div>
        <p class="mt-4 text-sm text-slate-300">Última actividad: ${when}</p>
        <button id="btn-test-ttl" class="mt-3 w-full rounded-xl bg-orange-600/30 px-4 py-2 text-xs font-semibold text-orange-200 hover:bg-orange-600/50">Forzar expiración de Sesión (5 seg)</button>
    `;
}

export function openModal(item) {
    const isAdmin = state.currentUser?.rol === "ROLE_ADMIN";
    $("modal-kicker").textContent = `${item.tipo || "contenido"} • ${item.anioEstreno || "Año desconocido"}`;
    $("modal-title").textContent = item.titulo || "Sin titulo";
    $("modal-body").innerHTML = `<div class="space-y-4"><div class="rounded-[1.25rem] bg-gradient-to-br ${cardGradient(0)} p-6"><p class="text-sm text-white/80">Dirigida por</p><p class="mt-2 text-2xl font-black">${item.director || "Director sin definir"}</p></div><div><p class="text-xs uppercase tracking-[.2em] text-mist">Generos</p><div class="mt-3 flex flex-wrap gap-2">${(item.generos || []).map((g) => `<span class="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-sm">${g}</span>`).join("") || "<span class='text-slate-400'>Sin generos</span>"}</div></div></div><div class="space-y-4"><div class="rounded-[1.25rem] border border-white/10 bg-white/5 p-5 text-sm"><p>Calidad: <span class="font-semibold text-white">${item.metadatos?.calidad || "No definida"}</span></p><p class="mt-2">Idiomas: <span class="font-semibold text-white">${(item.metadatos?.idiomas || []).join(", ") || "No definidos"}</span></p><p class="mt-2">Subtitulos: <span class="font-semibold text-white">${(item.metadatos?.subtitulos || []).join(", ") || "No definidos"}</span></p></div><div class="flex flex-wrap gap-3"><button data-modal-play="${item.id || ""}" class="rounded-full bg-ember px-4 py-3 text-sm font-semibold hover:bg-red-700">Registrar vista</button><button data-modal-session="${item.id || ""}" class="rounded-full border border-white/10 px-4 py-3 text-sm hover:bg-white/5">Guardar en sesion</button>
    ${isAdmin ? `<button data-modal-delete="${item.id || ""}" data-modal-title="${item.titulo || "contenido"}" class="rounded-full border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-200 hover:bg-red-500/20">Eliminar (Soy Admin)</button>` : ''}
    </div></div>`;
    $("modal").classList.remove("hidden");
    $("modal").classList.add("flex");
}

export function closeModal() { $("modal").classList.add("hidden"); $("modal").classList.remove("flex"); }

export function renderAuth() {
    const pnl = $("auth-panel");
    const adminPnl = $("admin-panel");
    if(state.currentUser) {
        pnl.innerHTML = `
            <p class="text-xs uppercase tracking-[.2em] text-mist">Cuenta Autenticada</p>
            <p class="mt-2 text-xl font-bold text-white">${state.currentUser.username}</p>
            <span class="inline-block mt-1 rounded bg-white/10 px-2 py-1 text-xs text-white">${state.currentUser.rol}</span>
            <button id="btn-logout" class="mt-4 w-full rounded-2xl border border-white/15 bg-transparent px-4 py-3 text-sm hover:bg-white/5 hover:text-white text-mist">Cerrar Sesión HTTP</button>
        `;
        if (state.currentUser.rol === "ROLE_ADMIN" && adminPnl) adminPnl.style.display = "block";
        else if(adminPnl) adminPnl.style.display = "none";
    } else {
        pnl.innerHTML = `
            <p class="text-xs uppercase tracking-[.2em] text-mist mb-3">Iniciar Sesión / Registro</p>
            <form id="auth-form" class="space-y-3">
                <input name="user" placeholder="Nombre de usuario" required class="w-full rounded-xl border border-white/10 bg-black/30 px-4 py-2 outline-none">
                <input name="pass" type="password" placeholder="Contraseña" required class="w-full rounded-xl border border-white/10 bg-black/30 px-4 py-2 outline-none">
                <label class="flex items-center gap-2 text-xs text-mist p-1"><input type="checkbox" name="admin" class="rounded"> Quiero ser ADMIN (Beta test)</label>
                <div class="flex gap-2">
                    <button type="submit" id="btn-login" class="flex-1 rounded-xl bg-white/10 px-3 py-2 text-sm hover:bg-white/20">Entrar</button>
                    <button type="submit" id="btn-register" class="flex-1 rounded-xl bg-ember px-3 py-2 text-sm font-semibold hover:bg-red-600">Registrar</button>
                </div>
            </form>
        `;
        if(adminPnl) adminPnl.style.display = "none";
    }
}
