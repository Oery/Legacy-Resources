// Derivation lab front end. No framework and no build step on purpose: the page is served straight
// off disk by LabServer, so editing this file and refreshing is the whole edit loop.

/** Tiles per row before a card's source / derived column wraps, so one card cannot span the page. */
const SOURCE_COLUMNS = 4;
const OUTPUT_COLUMNS = 4;

const state = {
	derivations: [],
	current: null,
	values: {},          // param name -> current slider value
	lastRender: null,
	inFlight: null,
};

const el = {
	derivation: document.getElementById('derivation'),
	status: document.getElementById('status'),
	banner: document.getElementById('banner'),
	target: document.getElementById('target'),
	control: document.getElementById('control'),
	controlNote: document.getElementById('control-note'),
	params: document.getElementById('params'),
	grid: document.getElementById('grid'),
	skipped: document.getElementById('skipped'),
	viewer: document.getElementById('viewer'),
	viewerImage: document.getElementById('viewer-image'),
	viewerCaption: document.getElementById('viewer-caption'),
};

// ---------------------------------------------------------------- boot

async function boot() {
	const data = await (await fetch('/api/derivations')).json();
	showErrors(data.errors, data.note);
	state.derivations = data.derivations || [];
	el.derivation.innerHTML = state.derivations
		.map((d) => `<option value="${d.id}">${d.id}</option>`)
		.join('');
	if (state.derivations.length) {
		selectDerivation(state.derivations[0].id);
	}
}

function selectDerivation(id) {
	state.current = state.derivations.find((d) => d.id === id) || null;
	state.values = {};
	for (const param of state.current?.params || []) {
		state.values[param.name] = param.default;
	}
	buildParams();
	render();
}

// ---------------------------------------------------------------- sliders

function buildParams() {
	el.params.innerHTML = '';
	for (const param of state.current?.params || []) {
		const wrap = document.createElement('div');
		wrap.className = 'param';
		wrap.innerHTML = `
			<div class="param-head">
				<code>${param.name}</code>
				<input type="number" min="${param.min}" max="${param.max}" step="${param.step}">
			</div>
			<input type="range" min="${param.min}" max="${param.max}" step="${param.step}">`;

		const number = wrap.querySelector('input[type=number]');
		const slider = wrap.querySelector('input[type=range]');
		const apply = (value, source) => {
			state.values[param.name] = value;
			// Only write back to the control that wasn't just typed in, so the caret doesn't jump.
			if (source !== slider) slider.value = value;
			if (source !== number) number.value = format(value, param);
			wrap.classList.toggle('changed', value !== param.default);
			render();
		};
		slider.addEventListener('input', () => apply(Number(slider.value), slider));
		number.addEventListener('change', () => apply(Number(number.value), number));
		apply(param.default, null);
		el.params.append(wrap);
	}
}

function format(value, param) {
	return param.integer ? String(Math.round(value)) : String(Number(value.toFixed(3)));
}

// ---------------------------------------------------------------- rendering

let renderTimer = null;

function render() {
	// Debounced, and the previous request is abandoned rather than left to finish: dragging a slider
	// otherwise queues up a batch render of the whole corpus per pixel of travel.
	clearTimeout(renderTimer);
	renderTimer = setTimeout(runRender, 120);
}

async function runRender() {
	if (!state.current) return;
	state.inFlight?.abort();
	const controller = new AbortController();
	state.inFlight = controller;

	el.status.textContent = 'rendering...';
	try {
		const response = await fetch(`/api/render?${queryString()}`, { signal: controller.signal });
		const data = await response.json();
		state.lastRender = data;
		paint(data);
	} catch (e) {
		if (e.name !== 'AbortError') {
			el.status.textContent = 'failed';
			showErrors([String(e)], null);
		}
	}
}

function queryString() {
	const query = new URLSearchParams({ d: state.current.id });
	for (const [name, value] of Object.entries(state.values)) {
		query.set(`p.${name}`, value);
	}
	return query.toString();
}

function paint(data) {
	showErrors(data.errors, data.note);
	const packs = filtered(data.packs || []);
	el.status.textContent = `${packs.length} packs · ${data.elapsedMs}ms`;

	el.target.innerHTML = '';
	for (const path of data.outputs) {
		el.target.append(tile(data.target?.[path], shortName(path), { pack: '__target', path }));
	}

	el.control.innerHTML = '';
	if (data.control) {
		for (const path of data.outputs) {
			const score = data.control.scores?.[path];
			el.control.append(tile(
				data.control.outputs?.[path],
				score === undefined ? shortName(path) : `${score.toFixed(1)}%`,
				{ pack: data.control.id, path, derived: true },
			));
		}
		el.controlNote.textContent = 'Difference from vanilla’s own texture; lower is closer.';
	} else {
		el.controlNote.textContent = 'No control - reference/1.8.9/assets is missing.';
	}

	// Cards are as wide as their two columns of tiles, and both counts vary per derivation (netherite
	// armour reads 7 sources, suspicious gravel 11). Feeding the counts to CSS keeps the grid packing
	// as tightly as the current derivation allows instead of to one hardcoded guess.
	const showSources = document.getElementById('show-sources').checked;
	const style = document.documentElement.style;
	style.setProperty('--source-columns', String(showSources ? Math.min(SOURCE_COLUMNS, data.sources.length) : 0));
	style.setProperty('--output-columns', String(Math.min(OUTPUT_COLUMNS, data.outputs.length)));

	el.grid.innerHTML = '';
	for (const pack of packs) {
		el.grid.append(card(pack, data.outputs, data.sources));
	}

	el.skipped.innerHTML = (data.skipped || [])
		.map((s) => `<li>${escapeHtml(s.name)} <em>${escapeHtml(s.reason)}</em></li>`)
		.join('') || '<li>none</li>';
}

function filtered(packs) {
	let result = packs;
	if (document.getElementById('hide-missing').checked) {
		result = result.filter((pack) => Object.keys(pack.outputs || {}).length > 0);
	}
	if (document.getElementById('sort-resolution').checked) {
		result = [...result].sort((a, b) => (a.resolution || 0) - (b.resolution || 0));
	}
	return result;
}

function card(pack, outputs, sources) {
	const node = document.createElement('div');
	node.className = 'card';
	const produced = Object.keys(pack.outputs || {}).length;
	if (produced < outputs.length) node.classList.add('incomplete');

	const head = document.createElement('div');
	head.className = 'card-head';
	head.innerHTML = `<span class="card-name" title="${escapeHtml(pack.name)}">${escapeHtml(pack.name)}</span>`
		+ `<span class="card-res">${pack.resolution ? pack.resolution + 'px' : '&mdash;'}</span>`;
	node.append(head);

	// Source art on the left, what the derivation made of it on the right. Judging a derivation means
	// judging the transform, not the output alone - whether a pack came out dark because the
	// constants are wrong or because its own art was already dark is invisible without the input
	// next to it.
	const body = document.createElement('div');
	body.className = 'card-body';
	if (document.getElementById('show-sources').checked) {
		body.append(group('source', sources, (path) => tile(
			pack.sources?.[path], '', { pack: pack.id, path }, shortName(path),
		)));
	}
	body.append(group('derived', outputs, (path) => tile(
		// No caption, only a hover title: the outputs are in declared order and repeat on every card,
		// so labelling each one costs packs per row to say nothing.
		pack.outputs?.[path], '', { pack: pack.id, path, derived: true }, shortName(path),
	)));
	node.append(body);
	// Which column is which is stated once in the sidebar legend rather than on all sixty cards; per
	// card it was a whole text row of vertical space to repeat something the layout already says.

	if (pack.error) {
		const note = document.createElement('div');
		note.className = 'card-note';
		note.textContent = pack.error;
		node.append(note);
	} else if (pack.missing?.length) {
		const note = document.createElement('div');
		note.className = 'card-note';
		note.textContent = `missing ${pack.missing.map(shortName).join(', ')}`;
		node.append(note);
	}
	return node;
}

/** One column of tiles within a card. */
function group(label, paths, build) {
	const node = document.createElement('div');
	node.className = `group group-${label}`;
	const strip = document.createElement('div');
	strip.className = 'strip';
	for (const path of paths) strip.append(build(path));
	node.append(strip);
	return node;
}

function tile(dataUrl, caption, target, title = caption) {
	const figure = document.createElement('figure');
	figure.className = dataUrl ? 'tile' : 'tile blank';
	figure.innerHTML = `<img alt="${escapeHtml(title)}" title="${escapeHtml(title)}" src="${dataUrl || transparentPixel}">`
		+ `<figcaption>${escapeHtml(caption)}</figcaption>`;
	if (dataUrl && target) {
		figure.addEventListener('click', () => openViewer(target, caption));
	}
	return figure;
}

const transparentPixel =
	'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';

// The grid shows previews capped at 128px; this is how the real thing gets looked at.
function openViewer(target, caption) {
	const query = new URLSearchParams({ pack: target.pack, path: target.path });
	if (target.derived) {
		query.set('d', state.current.id);
		for (const [name, value] of Object.entries(state.values)) query.set(`p.${name}`, value);
	}
	el.viewerImage.src = `/api/texture?${query}`;
	el.viewerCaption.textContent = `${caption} — ${target.path}`;
	el.viewer.hidden = false;
}

// ---------------------------------------------------------------- misc

function showErrors(errors, note) {
	const lines = [...(errors || [])];
	if (note) lines.push(note);
	el.banner.hidden = lines.length === 0;
	el.banner.textContent = lines.join('\n');
}

function shortName(path) {
	return path.split('/').pop();
}

function escapeHtml(value) {
	return String(value).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}

/** The tuned values, in the form they have to take to become the derivation's new defaults. */
function copyAsJava() {
	const lines = state.current.params.map((param) => {
		const value = state.values[param.name];
		return param.integer
			? `Param.ofInt("${param.name}", ${param.min}, ${param.max}, ${Math.round(value)}),`
			: `Param.of("${param.name}", ${trim(param.min)}, ${trim(param.max)}, ${trim(value)}),`;
	});
	navigator.clipboard.writeText(lines.join('\n'));
	el.status.textContent = 'copied';
}

function trim(value) {
	return String(Number(Number(value).toFixed(3)));
}

el.derivation.addEventListener('change', () => selectDerivation(el.derivation.value));
document.getElementById('reset').addEventListener('click', () => selectDerivation(state.current.id));
document.getElementById('copy').addEventListener('click', copyAsJava);
document.getElementById('zoom').addEventListener('input', (e) => {
	document.documentElement.style.setProperty('--tile', `${e.target.value}px`);
});
for (const id of ['show-sources', 'hide-missing', 'sort-resolution']) {
	document.getElementById(id).addEventListener('change', () => state.lastRender && paint(state.lastRender));
}
el.viewer.addEventListener('click', () => { el.viewer.hidden = true; });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') el.viewer.hidden = true; });

boot();
