<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue";
import * as THREE from "three";
import { engine } from "@/studio/engine";
import { createSurfaceBuffers, evalSurface, subdivisionFor } from "@/studio/mesh";
import { hexToRgb } from "@/studio/color";
import {
	COLOR_SPACE_INDEX,
	meshFragmentShader,
	meshVertexShader,
} from "@/studio/shaders/meshPass";
import {
	applyEffectsUniforms,
	createPostUniforms,
	postFragmentShader,
	postVertexShader,
} from "@/studio/shaders/postPass";

/**
 * Animated WebGL mesh-gradient backdrop, rendering a zoxilsi studio
 * `MeshDoc` preset through the exact two-pass pipeline the editor uses:
 * bicubic Hermite mesh → render target → effects post pass.
 */

const props = defineProps({
	doc: { type: Object, required: true },
	/** Whether the cursor force (attract/repel) from the preset is live. */
	interactive: { type: Boolean, default: true },
});

const container = ref(null);
const failed = ref(false);

let renderer = null;
let camera = null;
let target = null;
let postUniforms = null;
let meshScene = null;
let postScene = null;
let surface = null;
let geometry = null;
let meshMaterial = null;
let postMaterial = null;

let rafId = 0;
let lastTime = 0;
let inView = true;
let reducedMotion = false;
let resizeObserver = null;
let intersectionObserver = null;
let disposed = false;
/** Internal doc clone with hue cycling frozen (see onMounted). */
let activeDoc = null;

const sub = subdivisionFor(props.doc.rows, props.doc.cols);

function resize() {
	if (!renderer || !container.value) return;
	const w = container.value.clientWidth;
	const h = container.value.clientHeight;
	if (w === 0 || h === 0) return;

	renderer.setSize(w, h, false);
	const db = renderer.getDrawingBufferSize(new THREE.Vector2());
	target.setSize(db.x, db.y);
	postUniforms.uResolution.value = [db.x, db.y];
}

function renderFrame() {
	if (!renderer || disposed) return;
	const doc = activeDoc ?? props.doc;

	engine.syncColors(doc);
	evalSurface(surface, doc.rows, doc.cols, sub, engine.nodePos, engine.nodeCol, doc.nodes);
	geometry.attributes.position.needsUpdate = true;
	geometry.attributes.color.needsUpdate = true;

	// Pass 1 — mesh into the render target (lattice bleed is clipped by the viewport).
	renderer.setRenderTarget(target);
	renderer.render(meshScene, camera);

	// Pass 2 — effects to the screen.
	renderer.setRenderTarget(null);
	postUniforms.tDiffuse.value = target.texture;
	postUniforms.uTime.value = engine.shaderTime;
	renderer.render(postScene, camera);
}

function frame(now) {
	rafId = requestAnimationFrame(frame);
	const dt = Math.min((now - lastTime) / 1000, 0.05);
	lastTime = now;
	// Skip the (not free) CPU/GPU work while the hero is offscreen or hidden.
	if (!inView || document.hidden) return;
	engine.tick(activeDoc ?? props.doc, dt);
	renderFrame();
}

function onPointerMove(event) {
	const el = container.value;
	if (!el) return;
	const rect = el.getBoundingClientRect();
	// Cursor force lives in artboard uv space (0–1, y up).
	engine.mouse.x = (event.clientX - rect.left) / rect.width;
	engine.mouse.y = 1 - (event.clientY - rect.top) / rect.height;
	engine.mouse.active = true;
}

function onPointerLeave() {
	engine.mouse.active = false;
}
function onVisibilityChange() {
	if (!document.hidden && !reducedMotion) lastTime = performance.now();
}

onMounted(() => {
	reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
	activeDoc = structuredClone(props.doc);
	activeDoc.animation.hueFlow = 0;
	engine.flowTime = 75;
	const doc = activeDoc;

	try {
		renderer = new THREE.WebGLRenderer({
			antialias: true,
			alpha: false,
			powerPreference: "high-performance",
		});
	} catch {
		failed.value = true;
		return;
	}

	renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
	renderer.domElement.className = "mesh-gradient-canvas";
	container.value.appendChild(renderer.domElement);

	const bg = hexToRgb(doc.canvas.backgroundColor);
	const clear = new THREE.Color();
	clear.setRGB(bg.r, bg.g, bg.b, THREE.LinearSRGBColorSpace);
	renderer.setClearColor(clear, 1);

	// Pass 1 scene — the Hermite surface, buffers rebuilt only on doc change.
	surface = createSurfaceBuffers(doc.rows, doc.cols, sub);
	geometry = new THREE.BufferGeometry();
	geometry.setAttribute(
		"position",
		new THREE.BufferAttribute(surface.positions, 3).setUsage(THREE.DynamicDrawUsage),
	);
	geometry.setAttribute(
		"color",
		new THREE.BufferAttribute(surface.colors, 3).setUsage(THREE.DynamicDrawUsage),
	);
	geometry.setIndex(new THREE.BufferAttribute(surface.indices, 1));

	meshMaterial = new THREE.ShaderMaterial({
		vertexShader: meshVertexShader,
		fragmentShader: meshFragmentShader,
		uniforms: { uColorSpace: { value: COLOR_SPACE_INDEX[doc.canvas.colorSpace] ?? 2 } },
		depthTest: false,
		depthWrite: false,
		side: THREE.DoubleSide,
	});
	const mesh = new THREE.Mesh(geometry, meshMaterial);
	mesh.frustumCulled = false;
	meshScene = new THREE.Scene();
	meshScene.add(mesh);

	// Pass 2 scene — fullscreen effects quad.
	target = new THREE.WebGLRenderTarget(1, 1, { depthBuffer: false, stencilBuffer: false });
	postUniforms = createPostUniforms();
	applyEffectsUniforms(postUniforms, doc.effects);
	postMaterial = new THREE.ShaderMaterial({
		vertexShader: postVertexShader,
		fragmentShader: postFragmentShader,
		uniforms: postUniforms,
		depthTest: false,
		depthWrite: false,
	});
	const quad = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), postMaterial);
	quad.frustumCulled = false;
	postScene = new THREE.Scene();
	postScene.add(quad);

	camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);

	resize();
	resizeObserver = new ResizeObserver(resize);
	resizeObserver.observe(container.value);

	intersectionObserver = new IntersectionObserver(([entry]) => {
		inView = entry.isIntersecting;
		if (inView && reducedMotion) renderFrame();
	});
	intersectionObserver.observe(container.value);
	document.addEventListener("visibilitychange", onVisibilityChange);

	// Always draw one frame so a static image is there even without animation.
	renderFrame();
	if (!reducedMotion) {
		lastTime = performance.now();
		rafId = requestAnimationFrame(frame);
	}
});

onBeforeUnmount(() => {
	disposed = true;
	cancelAnimationFrame(rafId);
	resizeObserver?.disconnect();
	intersectionObserver?.disconnect();
	document.removeEventListener("visibilitychange", onVisibilityChange);
	geometry?.dispose();
	meshMaterial?.dispose();
	postMaterial?.dispose();
	target?.dispose();
	renderer?.dispose();
	renderer?.domElement.remove();
});
</script>

<template>
	<div
		ref="container"
		class="mesh-gradient"
		:class="{ fallback: failed }"
		:style="failed ? { backgroundColor: doc.canvas.backgroundColor } : undefined"
		aria-hidden="true"
		@pointermove="props.interactive && onPointerMove($event)"
		@pointerleave="onPointerLeave"
	></div>
</template>

<style scoped>
.mesh-gradient {
	position: absolute;
	inset: 0;
	overflow: hidden;
}

.mesh-gradient-canvas {
	width: 100%;
	height: 100%;
}
</style>
