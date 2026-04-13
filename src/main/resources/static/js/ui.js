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
    $("ranking").innerHTML = state.ranking.length ? state.ranking.map((item, index) => {
        const content = state.catalog.find((entry) => entry.id === item.contenidoId);
        return `<article class="overflow-hidden rounded-[1.25rem] border border-white/10 bg-black/20">
            <div class="bg-gradient-to-br ${cardGradient(index)} p-4">
                <p class="text-xs uppercase tracking-[.2em] text-white/70">Puesto ${index + 1}</p>
                <h4 class="mt-4 text-xl font-black">${content?.titulo || item.contenidoId}</h4>
                <p class="mt-2 text-sm text-white/85">${content?.director || "Sin detalle en cache"}</p>
            </div>
            <div class="flex items-center justify-between p-4 text-sm"><span class="text-slate-300">Vistas</span><span class="font-semibold">${Math.round(item.vistas || 0)}</span></div>
        </article>`;
    }).join("") : `<div class="md:col-span-2 xl:col-span-5 rounded-[1.25rem] border border-dashed border-white/10 bg-black/20 p-6 text-center text-slate-300">El ranking todavia no tiene datos.</div>`;
}

export function renderSession() {
    if (!state.session) { $("session-card").textContent = "Carga una sesion para ver el ultimo contenido guardado."; return; }
    const content = state.catalog.find((item) => item.id === state.session.lastContentId);
    const when = state.session.timestamp ? new Date(state.session.timestamp * 1000).toLocaleString("es-AR") : "Sin timestamp";
    $("session-card").innerHTML = `<p class="text-xs uppercase tracking-[.2em] text-mist">Usuario</p><p class="mt-2 text-xl font-bold text-white">${state.session.userId}</p><div class="mt-4 rounded-2xl border border-white/10 bg-white/5 p-4"><p class="text-xs uppercase tracking-[.2em] text-mist">Ultimo contenido</p><p class="mt-2 text-lg font-semibold text-white">${content?.titulo || state.session.lastContentId}</p><p class="mt-1 text-sm text-slate-300">${content?.director || "ID sin detalle en cache local"}</p></div><p class="mt-4 text-sm text-slate-300">Ultima actividad: ${when}</p>`;
}

export function openModal(item) {
    $("modal-kicker").textContent = `${item.tipo || "contenido"} • ${item.anioEstreno || "Año desconocido"}`;
    $("modal-title").textContent = item.titulo || "Sin titulo";
    $("modal-body").innerHTML = `<div class="space-y-4"><div class="rounded-[1.25rem] bg-gradient-to-br ${cardGradient(0)} p-6"><p class="text-sm text-white/80">Dirigida por</p><p class="mt-2 text-2xl font-black">${item.director || "Director sin definir"}</p></div><div><p class="text-xs uppercase tracking-[.2em] text-mist">Generos</p><div class="mt-3 flex flex-wrap gap-2">${(item.generos || []).map((g) => `<span class="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-sm">${g}</span>`).join("") || "<span class='text-slate-400'>Sin generos</span>"}</div></div></div><div class="space-y-4"><div class="rounded-[1.25rem] border border-white/10 bg-white/5 p-5 text-sm"><p>Calidad: <span class="font-semibold text-white">${item.metadatos?.calidad || "No definida"}</span></p><p class="mt-2">Idiomas: <span class="font-semibold text-white">${(item.metadatos?.idiomas || []).join(", ") || "No definidos"}</span></p><p class="mt-2">Subtitulos: <span class="font-semibold text-white">${(item.metadatos?.subtitulos || []).join(", ") || "No definidos"}</span></p></div><div class="flex flex-wrap gap-3"><button data-modal-play="${item.id || ""}" class="rounded-full bg-ember px-4 py-3 text-sm font-semibold hover:bg-red-700">Registrar vista</button><button data-modal-session="${item.id || ""}" class="rounded-full border border-white/10 px-4 py-3 text-sm hover:bg-white/5">Guardar en sesion</button><button data-modal-delete="${item.id || ""}" data-modal-title="${item.titulo || "contenido"}" class="rounded-full border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-200 hover:bg-red-500/20">Eliminar</button></div></div>`;
    $("modal").classList.remove("hidden");
    $("modal").classList.add("flex");
}

export function closeModal() { $("modal").classList.add("hidden"); $("modal").classList.remove("flex"); }
